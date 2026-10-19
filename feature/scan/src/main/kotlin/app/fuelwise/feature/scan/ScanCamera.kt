package app.fuelwise.feature.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
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
import app.fuelwise.core.domain.scan.OcrBox
import app.fuelwise.core.domain.scan.OcrLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * Camera preview plus on-device text recognition (PRD F-15). ML Kit's bundled Latin model runs in the app process;
 * frames and photos never leave the device. Each frame's lines go to [onFrame]; a frame's photo is only encoded
 * when the user keeps it. The camera is bound to the screen's lifecycle and released when it leaves.
 */
// ImageProxy.image hands the frame to ML Kit without a copy; CameraX marks it experimental.
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
internal fun ScanCameraPreview(onFrame: (OcrFrame) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrame by rememberUpdatedState(onFrame)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var lastRun = 0L
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(executor) { proxy ->
            val now = System.currentTimeMillis()
            val media = proxy.image
            if (media == null || now - lastRun < MIN_FRAME_GAP_MS) {
                proxy.close()
                return@setAnalyzer
            }
            lastRun = now
            val rotation = proxy.imageInfo.rotationDegrees
            val bitmap = runCatching { proxy.uprightBitmap() }.getOrNull()
            val upright = rotation % HALF_TURN != 0
            val width = if (upright) proxy.height else proxy.width
            val height = if (upright) proxy.width else proxy.height
            recognizer.process(InputImage.fromMediaImage(media, rotation))
                .addOnSuccessListener { text ->
                    val lines = text.textBlocks.flatMap { block -> block.lines }.map { line ->
                        OcrLine(line.text, line.boundingBox?.let { OcrBox(it.left, it.top, it.right, it.bottom) })
                    }
                    currentOnFrame(OcrFrame(lines, width, height) { bitmap?.jpeg() })
                }
                .addOnCompleteListener { proxy.close() }
        }
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            recognizer.close()
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

private fun ImageProxy.uprightBitmap(): Bitmap {
    val raw = toBitmap()
    val degrees = imageInfo.rotationDegrees
    if (degrees == 0) return raw
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
}

private fun Bitmap.jpeg(): ByteArray = ByteArrayOutputStream().use { out ->
    compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    out.toByteArray()
}

/** About three readings a second: enough for live labels, light on the battery. */
private const val MIN_FRAME_GAP_MS = 300L
private const val HALF_TURN = 180
private const val JPEG_QUALITY = 80
