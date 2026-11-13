package app.fuelwise.core.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.fuelwise.core.designsystem.theme.FwTheme
import app.fuelwise.core.domain.trip.HeatPoint
import app.fuelwise.core.model.TrackPoint
import app.fuelwise.core.ui.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.google.maps.android.heatmaps.Gradient
import com.google.maps.android.heatmaps.HeatmapTileProvider
import kotlin.math.cos

/**
 * False in tests and screenshots: Robolectric can't run the Maps SDK, so the maps draw a plain trace or dots
 * instead. The app leaves it true.
 */
val LocalMapsEnabled = staticCompositionLocalOf { true }

/** A pin on [FwPinMap]: one geotagged entry, coloured by its energy type. */
data class MapPin(val id: String, val lat: Double, val lng: Double, val color: Color)

/**
 * A route on a map (design.md screens 18 and 19). Display only: nothing is looked up (CLAUDE.md rule 5, PRD D12).
 * [follow] keeps the camera on the newest point (live trip); otherwise the whole route is fitted once. A pause
 * breaks the line ([TrackPoint.segment]).
 */
@Composable
fun FwRouteMap(
    route: List<TrackPoint>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    follow: Boolean = false,
) {
    val accent = FwTheme.colors.accent
    val labelled = modifier.semantics { this.contentDescription = contentDescription }
    if (!LocalMapsEnabled.current) {
        RouteTrace(route, accent, labelled)
        return
    }
    val camera = rememberCameraPositionState {
        position = route.lastOrNull()?.let { CameraPosition.fromLatLngZoom(it.latLng(), FOLLOW_ZOOM) }
            ?: CameraPosition.fromLatLngZoom(INDIA_CENTRE, INDIA_ZOOM)
    }
    var loaded by remember { mutableStateOf(false) }
    val last = route.lastOrNull()
    LaunchedEffect(loaded, if (follow) last else route.size) {
        if (!loaded || route.isEmpty()) return@LaunchedEffect
        if (follow) {
            camera.animate(CameraUpdateFactory.newLatLngZoom(route.last().latLng(), FOLLOW_ZOOM))
        } else if (route.size > 1) {
            val bounds = LatLngBounds.builder().apply { route.forEach { include(it.latLng()) } }.build()
            camera.move(CameraUpdateFactory.newLatLngBounds(bounds, FIT_PADDING_PX))
        } else {
            camera.move(CameraUpdateFactory.newLatLngZoom(route.first().latLng(), FOLLOW_ZOOM))
        }
    }
    GoogleMap(
        modifier = labelled,
        cameraPositionState = camera,
        properties = mapProperties(),
        uiSettings = MAP_UI,
        onMapLoaded = { loaded = true },
    ) {
        route.groupBy { it.segment }.values.forEach { segment ->
            if (segment.size > 1) {
                Polyline(points = segment.map { it.latLng() }, color = accent, width = ROUTE_WIDTH_PX)
            }
        }
        last?.let { Circle(center = it.latLng(), radius = END_DOT_M, fillColor = accent, strokeColor = Color.White) }
    }
}

/** Geotagged entries as coloured pins (design.md screen 12b). Tapping one calls [onSelect]. */
@Composable
fun FwPinMap(
    pins: List<MapPin>,
    contentDescription: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelled = modifier.semantics { this.contentDescription = contentDescription }
    if (!LocalMapsEnabled.current) {
        PinDots(pins, labelled)
        return
    }
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(INDIA_CENTRE, INDIA_ZOOM) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(loaded, pins) {
        if (!loaded || pins.isEmpty()) return@LaunchedEffect
        if (pins.size == 1) {
            camera.move(CameraUpdateFactory.newLatLngZoom(LatLng(pins[0].lat, pins[0].lng), FOLLOW_ZOOM))
        } else {
            val bounds = LatLngBounds.builder().apply { pins.forEach { include(LatLng(it.lat, it.lng)) } }.build()
            camera.move(CameraUpdateFactory.newLatLngBounds(bounds, FIT_PADDING_PX))
        }
    }
    val ring = FwTheme.colors.surface
    GoogleMap(
        modifier = labelled,
        cameraPositionState = camera,
        properties = mapProperties(),
        uiSettings = MAP_UI,
        onMapLoaded = { loaded = true },
    ) {
        pins.forEach { pin ->
            MarkerComposable(
                pin.id,
                pin.color,
                state = rememberUpdatedMarkerState(LatLng(pin.lat, pin.lng)),
                onClick = {
                    onSelect(pin.id)
                    true
                },
            ) {
                Box(Modifier.size(PIN_DP.dp).background(pin.color, CircleShape).border(2.dp, ring, CircleShape))
            }
        }
    }
}

/**
 * The consolidated trips map (PRD F-07.7, free in R1 since 12 Nov 2026): saved routes as a heatmap, with [pins] for
 * places visited often and fill-ups. Display only: nothing is looked up. The camera fits everything once.
 */
@Composable
fun FwHeatMap(heat: List<HeatPoint>, pins: List<MapPin>, contentDescription: String, modifier: Modifier = Modifier) {
    val labelled = modifier.semantics { this.contentDescription = contentDescription }
    val accent = FwTheme.colors.accent
    if (!LocalMapsEnabled.current) {
        HeatDots(heat, pins, accent, labelled)
        return
    }
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(INDIA_CENTRE, INDIA_ZOOM) }
    var loaded by remember { mutableStateOf(false) }
    val all = remember(heat, pins) { heat.map { LatLng(it.lat, it.lng) } + pins.map { LatLng(it.lat, it.lng) } }
    LaunchedEffect(loaded, all) {
        if (!loaded || all.isEmpty()) return@LaunchedEffect
        if (all.size == 1) {
            camera.move(CameraUpdateFactory.newLatLngZoom(all.first(), PLACE_ZOOM))
        } else {
            val bounds = LatLngBounds.builder().apply { all.forEach(::include) }.build()
            camera.move(CameraUpdateFactory.newLatLngBounds(bounds, FIT_PADDING_PX))
        }
    }
    // android-maps-utils draws the tiles; it needs at least one point. Cool to hot in the accent colour.
    val tiles = remember(heat, accent) {
        if (heat.isEmpty()) {
            null
        } else {
            val colors = intArrayOf(accent.copy(alpha = HEAT_COOL_ALPHA).toArgb(), accent.toArgb())
            HeatmapTileProvider.Builder()
                .data(heat.map { LatLng(it.lat, it.lng) })
                .radius(HEAT_RADIUS_PX)
                .gradient(Gradient(colors, HEAT_STOPS))
                .build()
        }
    }
    val ring = FwTheme.colors.surface
    GoogleMap(
        modifier = labelled,
        cameraPositionState = camera,
        properties = mapProperties(),
        uiSettings = MAP_UI,
        onMapLoaded = { loaded = true },
    ) {
        tiles?.let { TileOverlay(tileProvider = it) }
        pins.forEach { pin ->
            MarkerComposable(pin.id, pin.color, state = rememberUpdatedMarkerState(LatLng(pin.lat, pin.lng))) {
                Box(Modifier.size(PIN_DP.dp).background(pin.color, CircleShape).border(2.dp, ring, CircleShape))
            }
        }
    }
}

/**
 * Where one entry happened (PRD F-02.1 fill-up details): a small, still map with a pin. Lite mode draws a static
 * picture and ignores touches; the page scrolls over it. Display only: nothing is looked up (CLAUDE.md rule 5).
 */
@Composable
fun FwPlaceMap(lat: Double, lng: Double, contentDescription: String, modifier: Modifier = Modifier) {
    val labelled = modifier.semantics { this.contentDescription = contentDescription }
    val accent = FwTheme.colors.accent
    val ring = FwTheme.colors.surface
    if (!LocalMapsEnabled.current) {
        // The fallback for tests: the pin in the middle, as the real map centres on it.
        Box(labelled.background(FwTheme.colors.surface2), contentAlignment = Alignment.Center) {
            Box(Modifier.size(PIN_DP.dp).background(accent, CircleShape).border(2.dp, ring, CircleShape))
        }
        return
    }
    // A new place (the user changed the station) starts a new map, centred on it.
    key(lat, lng) {
        val at = LatLng(lat, lng)
        val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(at, PLACE_DETAIL_ZOOM) }
        GoogleMap(
            modifier = labelled,
            cameraPositionState = camera,
            properties = mapProperties(),
            uiSettings = STILL_UI,
            googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
        ) {
            MarkerComposable(lat, lng, accent, state = rememberUpdatedMarkerState(at)) {
                Box(Modifier.size(PIN_DP.dp).background(accent, CircleShape).border(2.dp, ring, CircleShape))
            }
        }
    }
}

@Composable
private fun mapProperties(): MapProperties {
    val context = LocalContext.current
    val dark = FwTheme.colors.bg.luminance() < HALF
    return remember(dark) {
        MapProperties(
            mapStyleOptions = if (dark) MapStyleOptions.loadRawResourceStyle(context, R.raw.fw_map_dark) else null,
            // Never the user's own location layer: maps here only show what was recorded.
            isMyLocationEnabled = false,
        )
    }
}

/** The fallback for tests: the route scaled to fit, north up, on a plain surface. */
@Composable
private fun RouteTrace(route: List<TrackPoint>, color: Color, modifier: Modifier) {
    Box(modifier.background(FwTheme.colors.surface2), contentAlignment = Alignment.Center) {
        if (route.size < 2) return@Box
        Canvas(Modifier.fillMaxSize().padding(24.dp)) {
            val project = Projection(route.map { it.lat to it.lng }, size.width, size.height)
            var segment = route.first().segment
            val path = Path().apply { project(0).let { moveTo(it.x, it.y) } }
            for (i in 1 until route.size) {
                val p = project(i)
                if (route[i].segment != segment) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                segment = route[i].segment
            }
            drawPath(path, color, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(color, radius = 7.dp.toPx(), center = project(route.lastIndex))
        }
    }
}

@Composable
private fun PinDots(pins: List<MapPin>, modifier: Modifier) {
    val ring = FwTheme.colors.surface
    Box(modifier.background(FwTheme.colors.surface2)) {
        if (pins.isEmpty()) return@Box
        Canvas(Modifier.fillMaxSize().padding(32.dp)) {
            val project = Projection(pins.map { it.lat to it.lng }, size.width, size.height)
            pins.forEachIndexed { i, pin ->
                drawCircle(ring, radius = (PIN_DP / 2 + 2).dp.toPx(), center = project(i))
                drawCircle(pin.color, radius = (PIN_DP / 2).dp.toPx(), center = project(i))
            }
        }
    }
}

/** The fallback for tests: heat as faint dots under the pins. */
@Composable
private fun HeatDots(heat: List<HeatPoint>, pins: List<MapPin>, color: Color, modifier: Modifier) {
    val ring = FwTheme.colors.surface
    Box(modifier.background(FwTheme.colors.surface2)) {
        val points = heat.map { it.lat to it.lng } + pins.map { it.lat to it.lng }
        if (points.isEmpty()) return@Box
        Canvas(Modifier.fillMaxSize().padding(32.dp)) {
            val project = Projection(points, size.width, size.height)
            val faint = color.copy(alpha = HEAT_COOL_ALPHA)
            heat.indices.forEach { drawCircle(faint, radius = 4.dp.toPx(), center = project(it)) }
            pins.forEachIndexed { i, pin ->
                val at = project(heat.size + i)
                drawCircle(ring, radius = (PIN_DP / 2 + 2).dp.toPx(), center = at)
                drawCircle(pin.color, radius = (PIN_DP / 2).dp.toPx(), center = at)
            }
        }
    }
}

/** Fits lat/lng pairs into a width × height box, north up, keeping proportions. */
private class Projection(points: List<Pair<Double, Double>>, width: Float, height: Float) {
    private val lat0 = Math.toRadians(points.first().first)
    private val xs = points.map { it.second * cos(lat0) }
    private val ys = points.map { it.first }
    private val minX = xs.min()
    private val maxY = ys.max()
    private val scale: Double
    private val offsetX: Double
    private val offsetY: Double

    init {
        val spanX = (xs.max() - minX).coerceAtLeast(MIN_SPAN)
        val spanY = (maxY - ys.min()).coerceAtLeast(MIN_SPAN)
        scale = minOf(width / spanX, height / spanY)
        offsetX = (width - spanX * scale) / 2
        offsetY = (height - spanY * scale) / 2
    }

    operator fun invoke(i: Int) =
        Offset((offsetX + (xs[i] - minX) * scale).toFloat(), (offsetY + (maxY - ys[i]) * scale).toFloat())
}

private fun TrackPoint.latLng() = LatLng(lat, lng)

private val MAP_UI = MapUiSettings(
    compassEnabled = false,
    mapToolbarEnabled = false,
    myLocationButtonEnabled = false,
    zoomControlsEnabled = false,
)

// A still map: no gestures, nothing to tap but the page around it.
private val STILL_UI = MAP_UI.copy(
    scrollGesturesEnabled = false,
    zoomGesturesEnabled = false,
    rotationGesturesEnabled = false,
    tiltGesturesEnabled = false,
)
private val INDIA_CENTRE = LatLng(22.5, 79.0)
private const val INDIA_ZOOM = 4f
private const val FOLLOW_ZOOM = 16f
private const val FIT_PADDING_PX = 96
private const val ROUTE_WIDTH_PX = 14f
private const val END_DOT_M = 12.0
private const val PIN_DP = 16
private const val HALF = 0.5f
private const val PLACE_ZOOM = 14f
private const val PLACE_DETAIL_ZOOM = 16f
private const val HEAT_RADIUS_PX = 30
private const val HEAT_COOL_ALPHA = 0.35f
private val HEAT_STOPS = floatArrayOf(0.2f, 1f)

// About 10 m, so a near-stationary start isn't blown up to fill the box.
private const val MIN_SPAN = 0.0001
