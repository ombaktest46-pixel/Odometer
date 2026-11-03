package com.example.ui.components

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.RoutePoint
import kotlin.math.max

enum class RouteMapMode(val label: String) {
    STREET_MAP("Peta Jalan"),
    VECTOR_RADAR("Vektor")
}

enum class StreetTileStyle(val label: String, val tileUrl: String) {
    DARK(
        "Gelap",
        "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
    ),
    STREET(
        "Terang",
        "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
    )
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

    MotoCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Map Header + Mode Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cs.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (mapMode == RouteMapMode.STREET_MAP) Icons.Outlined.Map else Icons.Outlined.Explore,
                        contentDescription = "Mode Peta Rute",
                        tint = cs.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Peta Rute (${routePoints.size} Titik)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("btn_toggle_map_theme")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Layers,
                                    contentDescription = "Ganti Tema Peta",
                                    tint = cs.secondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = tileStyle.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.secondary
                                )
                            }
                        }
                    }

                    RouteMapMode.entries.forEach { mode ->
                        val selected = mapMode == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) cs.secondary.copy(alpha = 0.12f) else cs.surface)
                                .border(
                                    1.dp,
                                    if (selected) cs.secondary.copy(alpha = 0.45f) else cs.outline,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { mapMode = mode }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("btn_map_mode_${mode.name}")
                        ) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) cs.secondary else cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = cs.outline)

            // Main Map Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(mapHeight)
                    .testTag("vector_route_map_canvas")
            ) {
                if (mapMode == RouteMapMode.STREET_MAP) {
                    InteractiveLeafletStreetMap(
                        routePoints = routePoints,
                        startLabel = startLabel,
                        endLabel = endLabel,
                        tileStyle = tileStyle,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(cs.surface)
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    zoom = (zoom * gestureZoom).coerceIn(0.7f, 6f)
                                    panOffset += pan
                                }
                            }
                    ) {
                        VectorRadarCanvasBody(
                            routePoints = routePoints,
                            zoom = zoom,
                            panOffset = panOffset
                        )

                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(cs.surface.copy(alpha = 0.94f))
                                .border(1.dp, cs.outline, RoundedCornerShape(10.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { zoom = (zoom * 1.3f).coerceIn(0.7f, 6f) },
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
                                onClick = { zoom = (zoom / 1.3f).coerceIn(0.7f, 6f) },
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
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = cs.outline)

            // Map Legend & Start/Finish Coordinates
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cs.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(color = cs.primary, text = "Awal: $startLabel")
                LegendDot(color = cs.error, text = "Akhir: $endLabel")
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(cs.surfaceVariant)
            .border(1.dp, cs.outline, RoundedCornerShape(10.dp))
    ) {
        VectorRadarCanvasBody(
            routePoints = routePoints,
            zoom = 1f,
            panOffset = Offset.Zero,
            isCompactPreview = true
        )
    }
}

@Composable
private fun VectorRadarCanvasBody(
    routePoints: List<RoutePoint>,
    zoom: Float,
    panOffset: Offset,
    isCompactPreview: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val gridColor = cs.outline.copy(alpha = 0.5f)
    val primaryColor = cs.primary
    val secondaryColor = cs.secondary
    val errorColor = cs.error

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val gridSpacing = if (isCompactPreview) 18.dp.toPx() else 32.dp.toPx()
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
        var gx = 0f
        while (gx <= w) {
            drawLine(
                color = gridColor,
                start = Offset(gx, 0f),
                end = Offset(gx, h),
                strokeWidth = 1f,
                pathEffect = dashEffect
            )
            gx += gridSpacing
        }
        var gy = 0f
        while (gy <= h) {
            drawLine(
                color = gridColor,
                start = Offset(0f, gy),
                end = Offset(w, gy),
                strokeWidth = 1f,
                pathEffect = dashEffect
            )
            gy += gridSpacing
        }

        if (routePoints.isEmpty()) return@Canvas

        val minLat = routePoints.minOf { it.lat }
        val maxLat = routePoints.maxOf { it.lat }
        val minLng = routePoints.minOf { it.lng }
        val maxLng = routePoints.maxOf { it.lng }

        val latSpan = max(maxLat - minLat, 0.00025)
        val lngSpan = max(maxLng - minLng, 0.00025)

        val paddingPx = if (isCompactPreview) 10.dp.toPx() else 34.dp.toPx()
        val usableW = (w - paddingPx * 2).coerceAtLeast(10f)
        val usableH = (h - paddingPx * 2).coerceAtLeast(10f)

        fun project(pt: RoutePoint): Offset {
            val normX = ((pt.lng - minLng) / lngSpan).toFloat()
            val normY = 1f - ((pt.lat - minLat) / latSpan).toFloat()
            val baseX = paddingPx + normX * usableW
            val baseY = paddingPx + normY * usableH
            val centerX = w / 2f
            val centerY = h / 2f
            return Offset(
                x = centerX + (baseX - centerX) * zoom + panOffset.x,
                y = centerY + (baseY - centerY) * zoom + panOffset.y
            )
        }

        val projected = routePoints.map { project(it) }

        if (projected.size >= 2) {
            val fullPath = Path().apply {
                moveTo(projected.first().x, projected.first().y)
                for (i in 1 until projected.size) {
                    lineTo(projected[i].x, projected[i].y)
                }
            }
            drawPath(
                path = fullPath,
                color = secondaryColor.copy(alpha = 0.16f),
                style = Stroke(
                    width = if (isCompactPreview) 4.5.dp.toPx() else 9.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            for (i in 0 until projected.lastIndex) {
                val p1 = projected[i]
                val p2 = projected[i + 1]
                val spd = routePoints[i + 1].speedKmh
                val segColor = when {
                    spd < 45f -> primaryColor
                    else -> secondaryColor
                }
                drawLine(
                    color = segColor,
                    start = p1,
                    end = p2,
                    strokeWidth = if (isCompactPreview) 2.2.dp.toPx() else 3.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        val firstPt = projected.first()
        val pinRadius = if (isCompactPreview) 3.dp.toPx() else 5.5.dp.toPx()
        drawCircle(
            color = primaryColor.copy(alpha = 0.25f),
            radius = pinRadius * 1.9f,
            center = firstPt
        )
        drawCircle(
            color = primaryColor,
            radius = pinRadius,
            center = firstPt
        )

        val lastPt = projected.last()
        drawCircle(
            color = errorColor.copy(alpha = 0.25f),
            radius = pinRadius * 1.9f,
            center = lastPt
        )
        drawCircle(
            color = errorColor,
            radius = pinRadius,
            center = lastPt
        )
    }

    if (routePoints.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = if (isCompactPreview) "No GPS" else "Data koordinat rute belum tersedia",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun InteractiveLeafletStreetMap(
    routePoints: List<RoutePoint>,
    startLabel: String,
    endLabel: String,
    tileStyle: StreetTileStyle,
    modifier: Modifier = Modifier
) {
    val htmlContent = remember(routePoints, tileStyle, startLabel, endLabel) {
        buildLeafletHtml(
            routePoints = routePoints,
            startLabel = startLabel,
            endLabel = endLabel,
            tileUrl = tileStyle.tileUrl,
            isLightMap = tileStyle == StreetTileStyle.STREET
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#E7EAF0"))
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "https://openstreetmap.org",
                htmlContent,
                "text/html",
                "UTF-8",
                null
            )
        }
    )
}

private fun buildLeafletHtml(
    routePoints: List<RoutePoint>,
    startLabel: String,
    endLabel: String,
    tileUrl: String,
    isLightMap: Boolean
): String {
    val coordsJsArray = if (routePoints.isNotEmpty()) {
        routePoints.joinToString(prefix = "[", postfix = "]") { pt ->
            "[${pt.lat}, ${pt.lng}, ${pt.speedKmh}]"
        }
    } else {
        "[]"
    }

    val defaultCenterLat = routePoints.firstOrNull()?.lat ?: -6.200000
    val defaultCenterLng = routePoints.firstOrNull()?.lng ?: 106.816666
    val bgHex = if (isLightMap) "#F6F7F9" else "#101216"
    val lineHex = if (isLightMap) "#16A36A" else "#20B978"
    val endHex = if (isLightMap) "#E5484D" else "#F16B70"

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                html, body, #map {
                    width: 100%;
                    height: 100%;
                    margin: 0;
                    padding: 0;
                    background: $bgHex;
                    font-family: -apple-system, BlinkMacSystemFont, "Inter", sans-serif;
                }
                .leaflet-control-attribution {
                    font-size: 9px !important;
                }
                .pin-start {
                    width: 14px;
                    height: 14px;
                    background: $lineHex;
                    border: 2.5px solid #FFFFFF;
                    border-radius: 50%;
                    box-shadow: 0 1px 4px rgba(0,0,0,0.30);
                }
                .pin-end {
                    width: 14px;
                    height: 14px;
                    background: $endHex;
                    border: 2.5px solid #FFFFFF;
                    border-radius: 50%;
                    box-shadow: 0 1px 4px rgba(0,0,0,0.30);
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var points = $coordsJsArray;
                var map = L.map('map', {
                    zoomControl: true,
                    attributionControl: true
                }).setView([$defaultCenterLat, $defaultCenterLng], 15);

                L.tileLayer('$tileUrl', {
                    maxZoom: 19,
                    attribution: '&copy; OpenStreetMap'
                }).addTo(map);

                if (points.length > 0) {
                    var latlngs = points.map(function(p) { return [p[0], p[1]]; });

                    L.polyline(latlngs, {
                        color: '$lineHex',
                        weight: 8,
                        opacity: 0.20,
                        lineCap: 'round',
                        lineJoin: 'round'
                    }).addTo(map);

                    var routeLine = L.polyline(latlngs, {
                        color: '$lineHex',
                        weight: 4,
                        opacity: 0.95,
                        lineCap: 'round',
                        lineJoin: 'round'
                    }).addTo(map);

                    var startIcon = L.divIcon({
                        className: '',
                        html: '<div class="pin-start"></div>',
                        iconSize: [18, 18],
                        iconAnchor: [9, 9]
                    });
                    var endIcon = L.divIcon({
                        className: '',
                        html: '<div class="pin-end"></div>',
                        iconSize: [18, 18],
                        iconAnchor: [9, 9]
                    });

                    var first = points[0];
                    var last = points[points.length - 1];

                    L.marker([first[0], first[1]], { icon: startIcon })
                        .addTo(map)
                        .bindPopup('<b>Titik Awal</b><br/>$startLabel');

                    if (points.length > 1) {
                        L.marker([last[0], last[1]], { icon: endIcon })
                            .addTo(map)
                            .bindPopup('<b>Titik Akhir</b><br/>$endLabel');
                        map.fitBounds(routeLine.getBounds(), { padding: [28, 28] });
                    } else {
                        map.setView([first[0], first[1]], 16);
                    }
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}

@Composable
private fun LegendDot(color: Color, text: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant
        )
    }
}
