package com.example.ui.components

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
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

enum class RouteMapMode(val label: String) {
    STREET_MAP("Peta"),
    VECTOR_RADAR("Vektor")
}

enum class StreetTileStyle(val label: String) {
    DARK("Gelap"),
    STREET("Terang")
}

/**
 * Dark-mode color matrix applied to standard OpenStreetMap street tiles
 * so dark mode gets crisp, watermark-free dark street maps without requiring any external API key.
 */
private val DarkOsmColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -0.78f, 0.0f, 0.0f, 0f, 212f,
            0.0f, -0.76f, 0.0f, 0f, 218f,
            0.0f, 0.0f, -0.70f, 0f, 230f,
            0.0f, 0.0f, 0.0f, 1f, 0f
        )
    )
)

private object OsmTileMemoryCache {
    private val cache = object : LruCache<String, ImageBitmap>(96) {}

    fun get(key: String): ImageBitmap? = synchronized(cache) { cache.get(key) }

    fun put(key: String, bitmap: ImageBitmap) = synchronized(cache) { cache.put(key, bitmap) }
}

private data class OsmTileKey(
    val z: Int,
    val x: Int,
    val y: Int
) {
    val cacheKey: String get() = "osm_v2/$z/$x/$y"

    fun buildUrls(): List<String> = listOf(
        "https://tile.openstreetmap.org/$z/$x/$y.png",
        "https://a.tile.openstreetmap.fr/hot/$z/$x/$y.png",
        "https://b.tile.openstreetmap.fr/hot/$z/$x/$y.png"
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
        maxSpan > 0.35 -> 11
        maxSpan > 0.16 -> 12
        maxSpan > 0.08 -> 13
        maxSpan > 0.035 -> 14
        maxSpan > 0.012 -> 15
        else -> 16
    }
}

@Composable
fun VectorRouteMapCanvas(
    routePoints: List<RoutePoint>,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
    mapHeight: Dp = 260.dp
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

            // 2. Main Interactive Native Map Area (Strictly clipped to bounds!)
            val mapBgColor = when {
                mapMode == RouteMapMode.STREET_MAP && tileStyle == StreetTileStyle.STREET ->
                    Color(0xFFEEF2F6)
                mapMode == RouteMapMode.STREET_MAP && tileStyle == StreetTileStyle.DARK ->
                    Color(0xFF141923)
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
                            "OpenStreetMap • ${(zoom * 100).roundToInt()}%"
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

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clipToBounds()
            .background(previewBg)
            .border(1.dp, cs.outline, RoundedCornerShape(10.dp))
    ) {
        NativeInteractiveRouteMapCanvas(
            routePoints = routePoints,
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

    val tileKeys = remember(mapMode, tileZoom, centerTileX, centerTileY, isCompactPreview) {
        if (isCompactPreview || mapMode != RouteMapMode.STREET_MAP) {
            emptyList()
        } else {
            val maxTileIndex = (1 shl tileZoom) - 1
            buildList {
                for (dx in -2..2) {
                    for (dy in -2..2) {
                        val tx = (centerTileX + dx).coerceIn(0, maxTileIndex)
                        val ty = (centerTileY + dy).coerceIn(0, maxTileIndex)
                        add(OsmTileKey(tileZoom, tx, ty))
                    }
                }
            }.distinctBy { it.cacheKey }
        }
    }

    LaunchedEffect(tileKeys) {
        tileKeys.forEach { key ->
            val cached = OsmTileMemoryCache.get(key.cacheKey)
            if (cached != null) {
                loadedTiles[key.cacheKey] = cached
            } else {
                withContext(Dispatchers.IO) {
                    for (urlStr in key.buildUrls()) {
                        val bmp = fetchOsmTileBitmap(urlStr)
                        if (bmp != null) {
                            OsmTileMemoryCache.put(key.cacheKey, bmp)
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

    // Strictly clip Canvas to its layout bounds so tiles never overflow into headers/footers
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

        // In STREET_MAP mode, draw loaded OpenStreetMap tiles (with DarkOsmColorFilter when Gelap is active)
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
                val tileFilter = if (tileStyle == StreetTileStyle.DARK) DarkOsmColorFilter else null
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
                            alpha = 0.95f,
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

        val projected = routePoints.map { project(it) }

        // 3. Draw Route Casing, Glow, and Speed-Colored Segments
        if (projected.size >= 2) {
            val fullPath = Path().apply {
                moveTo(projected.first().x, projected.first().y)
                for (i in 1 until projected.size) {
                    lineTo(projected[i].x, projected[i].y)
                }
            }

            // Outer soft halo
            drawPath(
                path = fullPath,
                color = primaryColor.copy(alpha = if (isCompactPreview) 0.18f else 0.24f),
                style = Stroke(
                    width = if (isCompactPreview) 6.dp.toPx() else 12.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Crisp road casing for high contrast over any map tile
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

            // Speed-colored polyline segments
            for (i in 0 until projected.lastIndex) {
                val p1 = projected[i]
                val p2 = projected[i + 1]
                val spd = routePoints[i + 1].speedKmh
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

            // Draw directional chevrons along the trajectory on full map
            if (!isCompactPreview && projected.size >= 6) {
                val step = (projected.size / 4).coerceAtLeast(2)
                for (i in step until projected.lastIndex step step) {
                    val prev = projected[i - 1]
                    val curr = projected[i]
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

        // 4. Draw Start Pin (GREEN) & Finish Pin (RED)
        val firstPt = projected.first()
        val pinRadius = if (isCompactPreview) 3.5.dp.toPx() else 6.5.dp.toPx()

        drawCircle(
            color = primaryColor.copy(alpha = 0.28f),
            radius = pinRadius * 2.1f,
            center = firstPt
        )
        drawCircle(
            color = Color.White,
            radius = pinRadius * 1.25f,
            center = firstPt
        )
        drawCircle(
            color = primaryColor,
            radius = pinRadius,
            center = firstPt
        )

        val lastPt = projected.last()
        drawCircle(
            color = errorColor.copy(alpha = 0.28f),
            radius = pinRadius * 2.1f,
            center = lastPt
        )
        drawCircle(
            color = Color.White,
            radius = pinRadius * 1.25f,
            center = lastPt
        )
        drawCircle(
            color = errorColor,
            radius = pinRadius,
            center = lastPt
        )

        if (!isCompactPreview && selectedPointIndex in projected.indices) {
            val selPt = projected[selectedPointIndex]
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

private fun fetchOsmTileBitmap(urlStr: String): ImageBitmap? {
    return runCatching {
        val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 4000
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            )
            setRequestProperty("Referer", "https://www.openstreetmap.org/")
            setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/png,image/*,*/*;q=0.8")
        }
        connection.connect()
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            connection.inputStream.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                bmp?.asImageBitmap()
            }
        } else {
            null
        }
    }.getOrNull()
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
