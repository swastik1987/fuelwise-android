package app.fuelwise.feature.scan

import android.graphics.Bitmap
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.fuelwise.core.domain.scan.DisplayCleanup
import app.fuelwise.core.domain.scan.GuideRegion
import app.fuelwise.core.domain.scan.OcrLine
import app.fuelwise.core.domain.scan.PixelRect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The link between the scan screen and the camera (PRD F-15): where the guide sits, so only the display under it is
 * read, and the "Snap" capture. Set up by [ScanCameraPreview]; until the camera is bound, [snap] reports nothing read.
 */
internal class ScanCamera {
    @Volatile
    var guide: GuideRegion? = null

    @Volatile
    internal var capture: ((OcrSnap) -> Unit) -> Unit = { it(OcrSnap.Nothing) }

    fun snap(onResult: (OcrSnap) -> Unit) = capture(onResult)
}

/**
 * Camera preview plus on-device text recognition (PRD F-15). ML Kit's bundled Latin model runs in the app process;
 * frames and photos never leave the device. The camera is bound to the screen's lifecycle and released when it leaves.
 *
 * Live: frames at about 1280×720, cropped to the guide, each read in turn plain, contrast-stretched or with
 * thickened digit strokes (segment displays read badly as they are). Snap: one full-resolution photo, read every way.
 */
@Composable
internal fun ScanCameraPreview(camera: ScanCamera, onFrame: (OcrFrame) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrame by rememberUpdatedState(onFrame)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val reader = LiveReader(camera, recognizer) { frame -> main.execute { currentOnFrame(frame) } }
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(selector(ResolutionStrategy(LIVE_SIZE, CLOSEST)))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(executor, reader::analyze)
        val capture = ImageCapture.Builder()
            .setResolutionSelector(selector(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY))
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            // A flash only adds glare on a display.
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .build()
        val snaps = SnapReader(capture, executor, main, camera, recognizer)
        camera.capture = { onResult -> snaps.snap(onResult) }
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().setResolutionSelector(selector(null)).build()
                .also { it.surfaceProvider = previewView.surfaceProvider }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis, capture)
            }
        }, main)
        onDispose {
            camera.capture = { it(OcrSnap.Nothing) }
            runCatching { providerFuture.get().unbindAll() }
            recognizer.close()
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Preview, live frames and snaps share one aspect ratio, so the guide maps onto each image the same way. */
private fun selector(resolution: ResolutionStrategy?): ResolutionSelector = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
    .apply { resolution?.let(::setResolutionStrategy) }
    .build()

/** Reads about three frames a second, one at a time, cycling through the image clean-ups. */
private class LiveReader(
    private val camera: ScanCamera,
    private val recognizer: TextRecognizer,
    private val onFrame: (OcrFrame) -> Unit,
) {
    private val inFlight = AtomicBoolean(false)
    private var lastRun = 0L
    private var turn = 0

    fun analyze(proxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (inFlight.get() || now - lastRun < MIN_FRAME_GAP_MS) {
            proxy.close()
            return
        }
        lastRun = now
        val upright = runCatching { proxy.use(ScanImages::upright) }.getOrNull() ?: return
        val rect = camera.guide?.cropOf(upright.width, upright.height) ?: upright.fullRect()
        val variant = LIVE_VARIANTS[turn++ % LIVE_VARIANTS.size]
        val input = runCatching { prepare(upright, rect, variant) }.getOrNull() ?: return
        inFlight.set(true)
        recognizer.process(InputImage.fromBitmap(input, 0))
            .addOnSuccessListener { text ->
                val lines = ScanImages.lines(text, rect.left, rect.top)
                onFrame(OcrFrame(lines, upright.width, upright.height) { ScanImages.jpeg(upright) })
            }
            .addOnCompleteListener { inFlight.set(false) }
    }
}

/** The camera pieces a snap needs. Work runs on [executor] (it waits for each read); results go out on [main]. */
private class SnapReader(
    val capture: ImageCapture,
    val executor: Executor,
    val main: Executor,
    val camera: ScanCamera,
    val recognizer: TextRecognizer,
)

/**
 * Takes one photo, then reads the part under the guide plain and with every clean-up. [onResult] is called on the
 * main thread. A failed capture reports nothing read.
 */
private fun SnapReader.snap(onResult: (OcrSnap) -> Unit) {
    capture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val result = runCatching {
                    val upright = ScanImages.limit(image.use(ScanImages::upright), SNAP_MAX_SIDE)
                    val rect = camera.guide?.cropOf(upright.width, upright.height) ?: upright.fullRect()
                    val reads = SNAP_VARIANTS.map { variant ->
                        val text = Tasks.await(
                            recognizer.process(InputImage.fromBitmap(prepare(upright, rect, variant), 0)),
                        )
                        ScanImages.lines(text, rect.left, rect.top)
                    }
                    OcrSnap(reads, upright.width, upright.height) { ScanImages.jpeg(upright) }
                }.getOrDefault(OcrSnap.Nothing)
                main.execute { onResult(result) }
            }

            override fun onError(exception: ImageCaptureException) {
                main.execute { onResult(OcrSnap.Nothing) }
            }
        },
    )
}

/** The image ML Kit reads: the crop as it is, or cleaned up in grey. */
private fun prepare(upright: Bitmap, rect: PixelRect, variant: DisplayCleanup.Variant?): Bitmap {
    val cropped = ScanImages.crop(upright, rect)
    return if (variant == null) cropped else ScanImages.bitmap(DisplayCleanup.apply(ScanImages.grey(cropped), variant))
}

private fun Bitmap.fullRect() = PixelRect(0, 0, width, height)

/** A snap's reads: one list of lines per way the photo was read. Boxes are in the [width] × [height] photo. */
internal class OcrSnap(val reads: List<List<OcrLine>>, val width: Int, val height: Int, val jpeg: () -> ByteArray?) {
    companion object {
        val Nothing = OcrSnap(emptyList(), 0, 0) { null }
    }
}

/** Null is the plain image. Live frames take one each in turn. */
private val LIVE_VARIANTS = listOf(null, DisplayCleanup.Variant.Stretched, DisplayCleanup.Variant.Thickened)
private val SNAP_VARIANTS = LIVE_VARIANTS + DisplayCleanup.Variant.ThickenedMore

private val LIVE_SIZE = Size(1280, 720)
private const val CLOSEST = ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER

/** About three readings a second: enough for live labels, light on the battery. */
private const val MIN_FRAME_GAP_MS = 300L

/** Long side of a snap before reading; enough for segment digits, quick for ML Kit. */
private const val SNAP_MAX_SIDE = 2000
