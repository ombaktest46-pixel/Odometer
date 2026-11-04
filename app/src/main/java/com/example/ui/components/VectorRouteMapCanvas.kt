package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RoutePoint
import com.example.ui.theme.InterFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

enum class RouteMapMode(val label: String) {
    STREET_MAP("Peta"),
    VECTOR_RADAR("Vektor")
}

enum class StreetTileStyle(val label: String) {
    DARK("Gelap"),
    STREET("Terang")
}

private const val OSM_COMPLIANT_USER_AGENT =
    "MotoOdoGpsTracker/1.0.3 (Android; Package/com.aistudio.motoodo.vxkrmp; +https://openstreetmap.org)"

/**
 * Luminance-preserving Dark Slate ColorMatrix:
 * First converts RGB tile pixels to neutral luminance (0.299R + 0.587G + 0.114B) and then
 * inverts into a clean dark automotive slate (#19202B background, crisp silver street labels).
 * Prevents green forests/terrain from turning purple when inverted.
 */
private val DarkSlateOsmColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -0.203f, -0.399f, -0.078f, 0f, 192f,
            -0.203f, -0.399f, -0.078f, 0f, 199f,
            -0.203f, -0.399f, -0.078f, 0f, 210f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

private object CompliantTileMemoryCache {
    private val cache = object : LruCache<String, ImageBitmap>(256) {}

    fun get(key: String): ImageBitmap? = synchronized(cache) { cache.get(key) }

    fun put(key: String, bitmap: ImageBitmap) = synchronized(cache) { cache.put(key, bitmap) }
}

private data class MapTileRequestKey(
    val z: Int,
    val x: Int,
    val y: Int
) {
    val cacheKey: String get() = "tile_v4/$z/$x/$y"

    fun buildUrls(): List<String> = listOf(
        "https://tile.openstreetmap.org/$z/$x/$y.png",
        "https://tile.openstreetmap.de/$z/$x/$y.png",
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/$z/$y/$x"
    )
}

private fun lonToTileX(lon: Double, z: Int): Double {
    val n = 2.0.pow(z.toDouble())
    return ((lon + 180.0) / 360.0) * n
}

private fun latToTileY(lat: Double, z: Int): Double {
    val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
    val n = 2.0.pow(z.toDouble())
    return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
}

private fun chooseBestTileZoom(latSpan: Double, lngSpan: Double): Int {
    val maxSpan = max(latSpan, lngSpan)
    return when {
        maxSpan > 0.70 -> 10
        maxSpan > 0.35 -> 11
        maxSpan > 0.16 -> 12
        maxSpan > 0.08 -> 13
        maxSpan > 0.035 -> 14
        maxSpan > 0.012 -> 15
        else -> 16
    }
}

/**
 * Calculates distance in meters between two GPS coordinates using Haversine formula.
 */
private fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1.0 - a))
    return r * c
}

/**
 * Determines whether two consecutive RoutePoints belong to the same continuous driving segment.
 * Prevents drawing a long straight green line ("garis ijo panjang") when:
 * 1) Multiple trips are combined on the overview map (timestamp jumps backwards or > 90s gap), or
 * 2) GPS signal jumps > 200 meters between two samples.
 */
private fun isContinuousRouteSegment(p1: RoutePoint, p2: RoutePoint): Boolean {
    if (p1.timestamp > 0L && p2.timestamp > 0L) {
        val dtMillis = p2.timestamp - p1.timestamp
        if (dtMillis < 0L || dtMillis > 90_000L) {
            return false
        }
    }
    val distMeters = haversineDistanceMeters(p1.lat, p1.lng, p2.lat, p2.lng)
    return distMeters <= 200.0
}

@Composable
fun VectorRouteMapCanvas(
    routePoints: List<RoutePoint>,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
    mapHeight: Dp = 260.dp,
    routeSegments: List<List<RoutePoint>> = remember(routePoints) {
        if (routePoints.isEmpty()) emptyList() else listOf(routePoints)
    }
) {
    val cs = MaterialTheme.colorScheme
    val isLightTheme = cs.background.luminance() > 0.5f

    var mapMode by remember { mutableStateOf(RouteMapMode.STREET_MAP) }
    var tileStyle by remember(isLightTheme) {
        mutableStateOf(if (isLightTheme) StreetTileStyle.STREET else StreetTileStyle.DARK)
    }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var selectedPointIndex by remember(routePoints) { mutableIntStateOf(-1) }

    MotoCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Top Map Header + Mode Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cs.surfaceVariant.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (mapMode == RouteMapMode.STREET_MAP) Icons.Outlined.Map else Icons.Outlined.Explore,
                        contentDescription = "Mode Peta Rute",
                        tint = cs.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Peta Rute",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• ${routePoints.size} titik",
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (mapMode == RouteMapMode.STREET_MAP) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(cs.surface)
                                .border(1.dp, cs.outline, RoundedCornerShape(8.dp))
                                .clickable {
                                    tileStyle = if (tileStyle == StreetTileStyle.DARK) {
                                        StreetTileStyle.STREET
                                    } else {
                                        StreetTileStyle.DARK
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                .testTag("btn_toggle_map_theme")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Layers,
                                    contentDescription = "Ganti Tema Peta",
                                    tint = cs.secondary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = tileStyle.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = cs.secondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    RouteMapMode.entries.forEach { mode ->
                        val selected = mapMode == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) cs.secondary.copy(alpha = 0.14f) else cs.surface)
                                .border(
                                    1.dp,
                                    if (selected) cs.secondary else cs.outline,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { mapMode = mode }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                .testTag("btn_map_mode_${mode.name}")
                        ) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) cs.secondary else cs.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = cs.outline)

            // 2. Main Interactive Native Map Area (Strictly clipped to bounds)
            val mapBgColor = when {
                mapMode == RouteMapMode.STREET_MAP && tileStyle == StreetTileStyle.STREET ->
                    Color(0xFFEEF2F6)
                mapMode == RouteMapMode.STREET_MAP && tileStyle == StreetTileStyle.DARK ->
                    Color(0xFF19202B)
                isLightTheme -> Color(0xFFF3F5F8)
                else -> Color(0xFF151A22)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(mapHeight)
                    .clipToBounds()
                    .background(mapBgColor)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            val newZoom = (zoom * gestureZoom).coerceIn(0.75f, 6.0f)
                            zoom = newZoom
                            panOffset += pan
                        }
                    }
                    .pointerInput(routePoints) {
                        detectTapGestures(
                            onDoubleTap = {
                                zoom = (zoom * 1.4f).coerceIn(0.75f, 6.0f)
                            },
                            onTap = {
                                if (routePoints.isNotEmpty()) {
                                    selectedPointIndex = if (selectedPointIndex == -1) {
                                        routePoints.size / 2
                                    } else {
                                        -1
                                    }
                                }
                            }
                        )
                    }
                    .testTag("vector_route_map_canvas")
            ) {
                NativeInteractiveRouteMapCanvas(
                    routePoints = routePoints,
                    routeSegments = routeSegments,
                    mapMode = mapMode,
                    tileStyle = tileStyle,
                    zoom = zoom,
                    panOffset = panOffset,
                    selectedPointIndex = selectedPointIndex
                )

                // Top-Left Status & Zoom Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(cs.surface.copy(alpha = 0.92f))
                        .border(1.dp, cs.outline, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (routePoints.isNotEmpty()) cs.primary else cs.onSurfaceVariant)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (mapMode == RouteMapMode.STREET_MAP) {
                            "OSM Street • ${(zoom * 100).roundToInt()}%"
                        } else {
                            "Vektor GPS • ${(zoom * 100).roundToInt()}%"
                        },
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        color = cs.onSurface
                    )
                }

                // Top-Right Zoom & Reset Controls
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(cs.surface.copy(alpha = 0.92f))
                        .border(1.dp, cs.outline, RoundedCornerShape(10.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { zoom = (zoom * 1.3f).coerceIn(0.75f, 6.0f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Perbesar Peta",
                            tint = cs.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { zoom = (zoom / 1.3f).coerceIn(0.75f, 6.0f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Remove,
                            contentDescription = "Perkecil Peta",
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            zoom = 1f
                            panOffset = Offset.Zero
                            selectedPointIndex = -1
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CenterFocusStrong,
                            contentDescription = "Reset Posisi Peta",
                            tint = cs.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Bottom-Start Speed Legend Overlay
                if (routePoints.size >= 2) {
                    val maxSpd = remember(routePoints) { routePoints.maxOfOrNull { it.speedKmh } ?: 0f }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(cs.surface.copy(alpha = 0.92f))
                            .border(1.dp, cs.outline, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        LegendSwatch(color = cs.primary, label = "Stabil")
                        LegendSwatch(color = cs.secondary, label = "Cepat")
                        Text(
                            text = "Maks ${String.format("%.1f", maxSpd).replace('.', ',')} km/j",
                            style = TextStyle(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                fontFeatureSettings = "tnum"
                            ),
                            color = cs.onSurface
                        )
                    }
                }

                // Required OpenStreetMap Copyright Attribution Badge (Bottom-End)
                if (mapMode == RouteMapMode.STREET_MAP) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cs.surface.copy(alpha = 0.85f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "© OpenStreetMap contributors",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = cs.outline)

            // 3. Map Legend & Start/Finish Coordinates
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cs.surfaceVariant.copy(alpha = 0.55f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(
                    color = cs.primary,
                    prefix = "Awal:",
                    coord = startLabel,
                    modifier = Modifier.weight(1f)
                )
                LegendDot(
                    color = cs.error,
                    prefix = "Akhir:",
                    coord = endLabel,
                    modifier = Modifier.weight(1f),
                    alignEnd = true
                )
            }
        }
    }
}

@Composable
fun MiniRoutePreviewCanvas(
    routePoints: List<RoutePoint>,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val isLight = cs.background.luminance() > 0.5f
    val previewBg = if (isLight) Color(0xFFEEF2F6) else Color(0xFF141923)
    val segments = remember(routePoints) {
        if (routePoints.isEmpty()) emptyList() else listOf(routePoints)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clipToBounds()
            .background(previewBg)
            .border(1.dp, cs.outline, RoundedCornerShape(10.dp))
    ) {
        NativeInteractiveRouteMapCanvas(
            routePoints = routePoints,
            routeSegments = segments,
            mapMode = RouteMapMode.VECTOR_RADAR,
            tileStyle = if (isLight) StreetTileStyle.STREET else StreetTileStyle.DARK,
            zoom = 1f,
            panOffset = Offset.Zero,
            selectedPointIndex = -1,
            isCompactPreview = true
        )
    }
}

@Composable
private fun NativeInteractiveRouteMapCanvas(
    routePoints: List<RoutePoint>,
    routeSegments: List<List<RoutePoint>>,
    mapMode: RouteMapMode,
    tileStyle: StreetTileStyle,
    zoom: Float,
    panOffset: Offset,
    selectedPointIndex: Int,
    isCompactPreview: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val primaryColor = cs.primary
    val secondaryColor = cs.secondary
    val errorColor = cs.error

    val minLat = remember(routePoints) { routePoints.minOfOrNull { it.lat } ?: -6.2000 }
    val maxLat = remember(routePoints) { routePoints.maxOfOrNull { it.lat } ?: -6.1950 }
    val minLng = remember(routePoints) { routePoints.minOfOrNull { it.lng } ?: 106.8160 }
    val maxLng = remember(routePoints) { routePoints.maxOfOrNull { it.lng } ?: 106.8220 }

    val rawLatSpan = max(maxLat - minLat, 0.0012)
    val rawLngSpan = max(maxLng - minLng, 0.0012)

    val tileZoom = remember(rawLatSpan, rawLngSpan) {
        chooseBestTileZoom(rawLatSpan, rawLngSpan)
    }

    val minTileX = remember(minLng, tileZoom) { lonToTileX(minLng, tileZoom) }
    val maxTileX = remember(maxLng, tileZoom) { lonToTileX(maxLng, tileZoom) }
    val minTileY = remember(maxLat, tileZoom) { latToTileY(maxLat, tileZoom) }
    val maxTileY = remember(minLat, tileZoom) { latToTileY(minLat, tileZoom) }

    val centerTileXExact = (minTileX + maxTileX) / 2.0
    val centerTileYExact = (minTileY + maxTileY) / 2.0
    val spanTileX = max(maxTileX - minTileX, 0.18)
    val spanTileY = max(maxTileY - minTileY, 0.18)

    val loadedTiles = remember { mutableStateMapOf<String, ImageBitmap>() }
    val centerTileX = floor(centerTileXExact).toInt()
    val centerTileY = floor(centerTileYExact).toInt()

    // Approximate pan shift in tile units so panning the map automatically loads newly visible regions
    val panShiftTilesX = (-(panOffset.x / (240f * zoom.coerceAtLeast(0.5f)))).roundToInt().coerceIn(-6, 6)
    val panShiftTilesY = (-(panOffset.y / (240f * zoom.coerceAtLeast(0.5f)))).roundToInt().coerceIn(-6, 6)
    val zoomOutExtraMargin = if (zoom < 0.95f) 3 else 2

    // Prioritize tiles directly along the route line first, then fill out the entire route bounds + viewport
    val tileKeys = remember(
        mapMode,
        tileZoom,
        minTileX,
        maxTileX,
        minTileY,
        maxTileY,
        panShiftTilesX,
        panShiftTilesY,
        zoomOutExtraMargin,
        routePoints,
        isCompactPreview
    ) {
        if (isCompactPreview || mapMode != RouteMapMode.STREET_MAP) {
            emptyList()
        } else {
            val maxTileIndex = (1 shl tileZoom) - 1

            // 1. Collect all tiles directly touched by the GPS route line (e.g. Prigen -> Pasuruan)
            val routePathTiles = LinkedHashSet<Pair<Int, Int>>()
            if (routePoints.isNotEmpty()) {
                val step = (routePoints.size / 60).coerceAtLeast(1)
                var prevTx = -1
                var prevTy = -1
                for (i in routePoints.indices step step) {
                    val pt = routePoints[i]
                    val tx = floor(lonToTileX(pt.lng, tileZoom)).toInt().coerceIn(0, maxTileIndex)
                    val ty = floor(latToTileY(pt.lat, tileZoom)).toInt().coerceIn(0, maxTileIndex)
                    routePathTiles.add(tx to ty)
                    // Interpolate intermediate tiles between sample points so long segments never skip a tile
                    if (prevTx != -1 && prevTy != -1 && (abs(tx - prevTx) > 1 || abs(ty - prevTy) > 1)) {
                        val interpSteps = max(abs(tx - prevTx), abs(ty - prevTy)).coerceAtMost(12)
                        for (s in 1 until interpSteps) {
                            val ix = prevTx + ((tx - prevTx) * s) / interpSteps
                            val iy = prevTy + ((ty - prevTy) * s) / interpSteps
                            routePathTiles.add(ix.coerceIn(0, maxTileIndex) to iy.coerceIn(0, maxTileIndex))
                        }
                    }
                    prevTx = tx
                    prevTy = ty
                }
                val lastPt = routePoints.last()
                routePathTiles.add(
                    floor(lonToTileX(lastPt.lng, tileZoom)).toInt().coerceIn(0, maxTileIndex) to
                        floor(latToTileY(lastPt.lat, tileZoom)).toInt().coerceIn(0, maxTileIndex)
                )
            }

            // 2. Cover the full route bounding box + viewport margin + user pan offset
            val startX = (floor(minTileX).toInt() - zoomOutExtraMargin + min(0, panShiftTilesX))
                .coerceAtLeast(centerTileX - 5)
                .coerceIn(0, maxTileIndex)
            val endX = (floor(maxTileX).toInt() + zoomOutExtraMargin + max(0, panShiftTilesX))
                .coerceAtMost(centerTileX + 5)
                .coerceIn(0, maxTileIndex)
            val startY = (floor(minTileY).toInt() - zoomOutExtraMargin + min(0, panShiftTilesY))
                .coerceAtLeast(centerTileY - 5)
                .coerceIn(0, maxTileIndex)
            val endY = (floor(maxTileY).toInt() + zoomOutExtraMargin + max(0, panShiftTilesY))
                .coerceAtMost(centerTileY + 5)
                .coerceIn(0, maxTileIndex)

            val focusX = centerTileX + panShiftTilesX
            val focusY = centerTileY + panShiftTilesY

            buildList {
                // First add tiles directly along the route line (and their immediate 1-tile neighbors)
                routePathTiles.forEach { (rtx, rty) ->
                    add(MapTileRequestKey(tileZoom, rtx, rty))
                }
                // Then add all tiles covering the full bounding box & panned viewport
                for (tx in startX..endX) {
                    for (ty in startY..endY) {
                        add(MapTileRequestKey(tileZoom, tx, ty))
                    }
                }
            }
                .distinctBy { it.cacheKey }
                .sortedWith(
                    compareBy<MapTileRequestKey> { key ->
                        if (routePathTiles.contains(key.x to key.y)) 0 else 1
                    }.thenBy { key ->
                        abs(key.x - focusX) + abs(key.y - focusY)
                    }
                )
        }
    }

    LaunchedEffect(tileKeys) {
        coroutineScope {
            tileKeys.forEach { key ->
                val cached = CompliantTileMemoryCache.get(key.cacheKey)
                if (cached != null) {
                    loadedTiles[key.cacheKey] = cached
                } else {
                    launch(Dispatchers.IO) {
                        for (urlStr in key.buildUrls()) {
                            val bmp = fetchCompliantTileBitmap(urlStr)
                            if (bmp != null) {
                                CompliantTileMemoryCache.put(key.cacheKey, bmp)
                                withContext(Dispatchers.Main) {
                                    loadedTiles[key.cacheKey] = bmp
                                }
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    val isStreetLight = mapMode == RouteMapMode.STREET_MAP && tileStyle == StreetTileStyle.STREET
    val majorGridColor = if (isStreetLight) {
        Color(0xFFD5DCE6)
    } else {
        Color(0xFF232C3B)
    }
    val minorGridColor = if (isStreetLight) {
        Color(0xFFE4E9F0)
    } else {
        Color(0xFF1B222E)
    }
    val arterialRoadColor = if (isStreetLight) {
        Color(0xFFFFFFFF).copy(alpha = 0.85f)
    } else {
        Color(0xFF283244).copy(alpha = 0.75f)
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val w = size.width
        val h = size.height
        val centerX = w / 2f
        val centerY = h / 2f

        val paddingPx = if (isCompactPreview) 10.dp.toPx() else 38.dp.toPx()
        val usableW = (w - paddingPx * 2f).coerceAtLeast(12f)
        val usableH = (h - paddingPx * 2f).coerceAtLeast(12f)

        val baseTileSizePx = min(
            usableW / spanTileX.toFloat(),
            usableH / spanTileY.toFloat()
        ).coerceIn(120f, 1200f)
        val effectiveTileSizePx = baseTileSizePx * zoom

        // 1. Base Cartographic Street Grid / Radar Layer
        val baseStep = if (isCompactPreview) 16.dp.toPx() else 36.dp.toPx()
        val scaledStep = (baseStep * zoom).coerceIn(14.dp.toPx(), 110.dp.toPx())
        val offsetX = ((panOffset.x % scaledStep) + scaledStep) % scaledStep
        val offsetY = ((panOffset.y % scaledStep) + scaledStep) % scaledStep

        val subStep = scaledStep / 2f
        var sx = ((panOffset.x % subStep) + subStep) % subStep
        while (sx <= w) {
            drawLine(
                color = minorGridColor,
                start = Offset(sx, 0f),
                end = Offset(sx, h),
                strokeWidth = 1f
            )
            sx += subStep
        }
        var sy = ((panOffset.y % subStep) + subStep) % subStep
        while (sy <= h) {
            drawLine(
                color = minorGridColor,
                start = Offset(0f, sy),
                end = Offset(w, sy),
                strokeWidth = 1f
            )
            sy += subStep
        }

        val dashEffect = if (mapMode == RouteMapMode.VECTOR_RADAR) {
            PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        } else {
            null
        }
        var gx = offsetX
        while (gx <= w) {
            drawLine(
                color = majorGridColor,
                start = Offset(gx, 0f),
                end = Offset(gx, h),
                strokeWidth = if (mapMode == RouteMapMode.STREET_MAP) 1.6f else 1.2f,
                pathEffect = dashEffect
            )
            gx += scaledStep
        }
        var gy = offsetY
        while (gy <= h) {
            drawLine(
                color = majorGridColor,
                start = Offset(0f, gy),
                end = Offset(w, gy),
                strokeWidth = if (mapMode == RouteMapMode.STREET_MAP) 1.6f else 1.2f,
                pathEffect = dashEffect
            )
            gy += scaledStep
        }

        // In STREET_MAP mode, draw loaded tiles (with DarkSlateOsmColorFilter when Gelap is active)
        if (!isCompactPreview && mapMode == RouteMapMode.STREET_MAP) {
            if (loadedTiles.isEmpty()) {
                val artX = (centerX + panOffset.x).coerceIn(0f, w)
                val artY = (centerY + panOffset.y).coerceIn(0f, h)
                drawLine(
                    color = arterialRoadColor,
                    start = Offset(artX, 0f),
                    end = Offset(artX, h),
                    strokeWidth = 6.dp.toPx()
                )
                drawLine(
                    color = arterialRoadColor,
                    start = Offset(0f, artY),
                    end = Offset(w, artY),
                    strokeWidth = 6.dp.toPx()
                )
            } else {
                val tileFilter = if (tileStyle == StreetTileStyle.DARK) DarkSlateOsmColorFilter else null
                tileKeys.forEach { key ->
                    val bmp = loadedTiles[key.cacheKey] ?: return@forEach
                    val dxTiles = key.x - centerTileXExact
                    val dyTiles = key.y - centerTileYExact
                    val leftPx = centerX + (dxTiles * effectiveTileSizePx).toFloat() + panOffset.x
                    val topPx = centerY + (dyTiles * effectiveTileSizePx).toFloat() + panOffset.y

                    if (leftPx + effectiveTileSizePx >= 0f && leftPx <= w &&
                        topPx + effectiveTileSizePx >= 0f && topPx <= h
                    ) {
                        drawImage(
                            image = bmp,
                            dstOffset = IntOffset(leftPx.roundToInt(), topPx.roundToInt()),
                            dstSize = IntSize(
                                effectiveTileSizePx.roundToInt() + 1,
                                effectiveTileSizePx.roundToInt() + 1
                            ),
                            alpha = 0.96f,
                            colorFilter = tileFilter
                        )
                    }
                }
            }
        }

        if (routePoints.isEmpty()) return@Canvas

        // 2. Project GPS Coordinates using the exact same Web Mercator coordinate system
        fun project(pt: RoutePoint): Offset {
            val ptTileX = lonToTileX(pt.lng, tileZoom)
            val ptTileY = latToTileY(pt.lat, tileZoom)
            val dx = (ptTileX - centerTileXExact) * effectiveTileSizePx
            val dy = (ptTileY - centerTileYExact) * effectiveTileSizePx
            return Offset(
                x = centerX + dx.toFloat() + panOffset.x,
                y = centerY + dy.toFloat() + panOffset.y
            )
        }

        val activeSegments = if (routeSegments.isNotEmpty()) routeSegments else listOf(routePoints)
        val projectedSegments = activeSegments.map { seg -> seg.map { project(it) } }
        val projectedAll = routePoints.map { project(it) }

        // 3. Draw Route Casing, Glow, and Speed-Colored Segments independently per Trip Segment
        val fullPath = Path().apply {
            for (sIdx in activeSegments.indices) {
                val segPts = activeSegments[sIdx]
                val segProj = projectedSegments[sIdx]
                if (segProj.size >= 2) {
                    moveTo(segProj.first().x, segProj.first().y)
                    for (i in 1 until segProj.size) {
                        if (isContinuousRouteSegment(segPts[i - 1], segPts[i])) {
                            lineTo(segProj[i].x, segProj[i].y)
                        } else {
                            moveTo(segProj[i].x, segProj[i].y)
                        }
                    }
                }
            }
        }

        drawPath(
            path = fullPath,
            color = primaryColor.copy(alpha = if (isCompactPreview) 0.18f else 0.24f),
            style = Stroke(
                width = if (isCompactPreview) 6.dp.toPx() else 12.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        if (!isCompactPreview) {
            drawPath(
                path = fullPath,
                color = if (isStreetLight) Color.White else Color(0xFF0E131A),
                style = Stroke(
                    width = 6.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        for (sIdx in activeSegments.indices) {
            val segPts = activeSegments[sIdx]
            val segProj = projectedSegments[sIdx]
            if (segProj.size < 2) continue

            for (i in 0 until segProj.lastIndex) {
                if (!isContinuousRouteSegment(segPts[i], segPts[i + 1])) {
                    continue
                }
                val p1 = segProj[i]
                val p2 = segProj[i + 1]
                val spd = segPts[i + 1].speedKmh
                val segColor = when {
                    spd < 42f -> primaryColor
                    spd < 68f -> secondaryColor
                    else -> errorColor
                }
                drawLine(
                    color = segColor,
                    start = p1,
                    end = p2,
                    strokeWidth = if (isCompactPreview) 2.5.dp.toPx() else 4.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            if (!isCompactPreview && segProj.size >= 6) {
                val step = (segProj.size / 4).coerceAtLeast(2)
                for (i in step until segProj.lastIndex step step) {
                    if (!isContinuousRouteSegment(segPts[i - 1], segPts[i])) {
                        continue
                    }
                    val prev = segProj[i - 1]
                    val curr = segProj[i]
                    val dx = curr.x - prev.x
                    val dy = curr.y - prev.y
                    val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (dist > 8f) {
                        val angle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                        val arrowLen = 5.dp.toPx()
                        val wingAngle = 2.55f
                        val leftWing = Offset(
                            x = curr.x + cos(angle + wingAngle) * arrowLen,
                            y = curr.y + sin(angle + wingAngle) * arrowLen
                        )
                        val rightWing = Offset(
                            x = curr.x + cos(angle - wingAngle) * arrowLen,
                            y = curr.y + sin(angle - wingAngle) * arrowLen
                        )
                        drawLine(
                            color = Color.White,
                            start = leftWing,
                            end = curr,
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color.White,
                            start = rightWing,
                            end = curr,
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }

        // 4. Draw Start Pin (GREEN) & Finish Pin (RED) for each Trip Segment
        val pinRadius = if (isCompactPreview) 3.5.dp.toPx() else 6.5.dp.toPx()

        for (sIdx in projectedSegments.indices) {
            val segProj = projectedSegments[sIdx]
            if (segProj.isEmpty()) continue
            drawPinMarker(segProj.first(), primaryColor, pinRadius)
            if (segProj.size > 1) {
                drawPinMarker(segProj.last(), errorColor, pinRadius)
            }
        }

        if (!isCompactPreview && selectedPointIndex in projectedAll.indices) {
            val selPt = projectedAll[selectedPointIndex]
            drawCircle(
                color = secondaryColor.copy(alpha = 0.30f),
                radius = pinRadius * 2.4f,
                center = selPt
            )
            drawCircle(
                color = Color.White,
                radius = pinRadius * 1.2f,
                center = selPt
            )
            drawCircle(
                color = secondaryColor,
                radius = pinRadius * 0.85f,
                center = selPt
            )
        }
    }

    if (routePoints.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(cs.surface.copy(alpha = 0.90f))
                    .border(1.dp, cs.outline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (isCompactPreview) "No GPS" else "Menunggu Titik Rute GPS",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurface
                )
                if (!isCompactPreview) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Rute perjalanan akan digambar otomatis saat motor bergerak",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPinMarker(
    center: Offset,
    color: Color,
    pinRadius: Float
) {
    drawCircle(
        color = color.copy(alpha = 0.28f),
        radius = pinRadius * 2.1f,
        center = center
    )
    drawCircle(
        color = Color.White,
        radius = pinRadius * 1.25f,
        center = center
    )
    drawCircle(
        color = color,
        radius = pinRadius,
        center = center
    )
}

private fun fetchCompliantTileBitmap(urlStr: String): ImageBitmap? {
    return runCatching {
        val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4500
            readTimeout = 4500
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", OSM_COMPLIANT_USER_AGENT)
            setRequestProperty("Accept", "image/png,image/*;q=0.9,*/*;q=0.5")
        }
        connection.connect()
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val contentType = connection.contentType ?: ""
            if (!contentType.startsWith("image/", ignoreCase = true)) {
                return@runCatching null
            }
            connection.inputStream.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream) ?: return@use null
                if (isBlocked418BannerTile(bmp)) {
                    null
                } else {
                    bmp.asImageBitmap()
                }
            }
        } else {
            null
        }
    }.getOrNull()
}

/**
 * Specifically detects the OpenStreetMap HTTP 418 blocked placeholder image
 * (which has exact #222222 dark gray background AND a bright blue/white caution stripe at x=48..52).
 */
private fun isBlocked418BannerTile(bmp: Bitmap): Boolean {
    if (bmp.width < 100 || bmp.height < 100) return true
    val bg1 = bmp.getPixel(12, 12) and 0xFFFFFF
    val bg2 = bmp.getPixel(180, 12) and 0xFFFFFF
    // The 418 tile has exact solid #222222 (0x222222) background on both left and right sides
    return bg1 == 0x222222 && bg2 == 0x222222
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 10.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = cs.onSurfaceVariant
        )
    }
}

@Composable
private fun LegendDot(
    color: Color,
    prefix: String,
    coord: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$prefix $coord",
            style = TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                fontFeatureSettings = "tnum"
            ),
            color = cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
