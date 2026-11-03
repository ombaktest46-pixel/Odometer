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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.RoutePoint
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.PanelBorderGray
import com.example.ui.theme.PanelElevatedGray
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondarySilver
import kotlin.math.max

enum class RouteMapMode(val label: String) {
    STREET_MAP("Peta Jalan"),
    VECTOR_RADAR("Vektor Offline")
}

enum class StreetTileStyle(val label: String, val tileUrl: String) {
    DARK(
        "Gelap",
        "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
    ),
    STREET(
        "Terang (OSM)",
        "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
    )
}

/**
 * Dual-Mode Route Map for MOTO ODO Trip History:
 * 1. Peta Jalan Interaktif (OpenStreetMap / CartoDB Dark & Street Map with real roads, markers & speed-coded polyline)
 * 2. Vektor Offline (Canvas telemetry radar that works 100% offline without internet)
 */
@Composable
fun VectorRouteMapCanvas(
    routePoints: List<RoutePoint>,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
    mapHeight: Dp = 280.dp
) {
    var mapMode by remember { mutableStateOf(RouteMapMode.STREET_MAP) }
    var tileStyle by remember { mutableStateOf(StreetTileStyle.DARK) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CarbonBlack)
            .border(1.dp, PanelBorderGray, RoundedCornerShape(18.dp))
    ) {
        // Top Map Header + Mode Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelElevatedGray)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (mapMode == RouteMapMode.STREET_MAP) Icons.Default.Map else Icons.Default.Explore,
                    contentDescription = "Mode Peta Rute",
                    tint = NeonGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "PETA RUTE (${routePoints.size} Titik GPS)",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonGreen
                )
            }

            // Mode Toggle Chips (Peta Jalan vs Vektor Offline + Tema Peta)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (mapMode == RouteMapMode.STREET_MAP) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CarbonBlack)
                            .border(1.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
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
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Ganti Tema Peta",
                                tint = CyberCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = tileStyle.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan
                            )
                        }
                    }
                }

                RouteMapMode.entries.forEach { mode ->
                    val selected = mapMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) NeonGreen.copy(alpha = 0.18f) else CarbonBlack)
                            .border(
                                1.dp,
                                if (selected) NeonGreen else PanelBorderGray,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { mapMode = mode }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("btn_map_mode_${mode.name}")
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) NeonGreen else TextSecondarySilver
                        )
                    }
                }
            }
        }

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
                // Offline Vector Telemetry Canvas
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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

                    // Zoom controls overlay for Vector mode
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PanelElevatedGray.copy(alpha = 0.9f))
                            .border(1.dp, PanelBorderGray, RoundedCornerShape(10.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { zoom = (zoom * 1.3f).coerceIn(0.7f, 6f) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Perbesar Peta",
                                tint = NeonGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = { zoom = (zoom / 1.3f).coerceIn(0.7f, 6f) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Perkecil Peta",
                                tint = TextSecondarySilver,
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
                                imageVector = Icons.Default.CenterFocusStrong,
                                contentDescription = "Reset Posisi Peta",
                                tint = CyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Map Legend & Start/Finish Coordinates
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelElevatedGray)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot(color = NeonGreen, text = "Awal: $startLabel")
            LegendDot(color = CrimsonRed, text = "Akhir: $endLabel")
        }
    }
}

/**
 * Mini Vector Route Thumbnail for Trip History Cards so users can see the route shape at a glance.
 */
@Composable
fun MiniRoutePreviewCanvas(
    routePoints: List<RoutePoint>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CarbonBlack)
            .border(1.dp, PanelBorderGray, RoundedCornerShape(12.dp))
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
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val gridSpacing = if (isCompactPreview) 18.dp.toPx() else 32.dp.toPx()
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        var gx = 0f
        while (gx <= w) {
            drawLine(
                color = PanelBorderGray.copy(alpha = 0.35f),
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
                color = PanelBorderGray.copy(alpha = 0.35f),
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

        val paddingPx = if (isCompactPreview) 12.dp.toPx() else 36.dp.toPx()
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
                color = NeonGreen.copy(alpha = 0.20f),
                style = Stroke(
                    width = if (isCompactPreview) 6.dp.toPx() else 12.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            for (i in 0 until projected.lastIndex) {
                val p1 = projected[i]
                val p2 = projected[i + 1]
                val spd = routePoints[i + 1].speedKmh
                val segColor = when {
                    spd < 35f -> NeonGreen
                    spd < 65f -> CyberCyan
                    else -> AmberWarning
                }
                drawLine(
                    color = segColor,
                    start = p1,
                    end = p2,
                    strokeWidth = if (isCompactPreview) 2.5.dp.toPx() else 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        val firstPt = projected.first()
        val pinRadius = if (isCompactPreview) 3.5.dp.toPx() else 6.dp.toPx()
        drawCircle(
            color = NeonGreen.copy(alpha = 0.35f),
            radius = pinRadius * 2f,
            center = firstPt
        )
        drawCircle(
            color = NeonGreen,
            radius = pinRadius,
            center = firstPt
        )

        val lastPt = projected.last()
        drawCircle(
            color = CrimsonRed.copy(alpha = 0.35f),
            radius = pinRadius * 2f,
            center = lastPt
        )
        drawCircle(
            color = CrimsonRed,
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
                text = if (isCompactPreview) "Belum ada titik GPS" else "Data koordinat rute belum tersedia",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondarySilver
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
            tileUrl = tileStyle.tileUrl
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#07090C"))
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
                // Prevent parent LazyColumn from intercepting touch drag while panning/zooming the map
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
    tileUrl: String
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
                    background: #07090C;
                    font-family: sans-serif;
                }
                .leaflet-control-attribution {
                    background: rgba(11, 14, 20, 0.75) !important;
                    color: #9BA9B8 !important;
                    font-size: 9px !important;
                }
                .leaflet-control-attribution a {
                    color: #00E5FF !important;
                }
                .pin-start {
                    width: 14px;
                    height: 14px;
                    background: #00FF66;
                    border: 2.5px solid #07090C;
                    border-radius: 50%;
                    box-shadow: 0 0 10px #00FF66;
                }
                .pin-end {
                    width: 14px;
                    height: 14px;
                    background: #FF3B5C;
                    border: 2.5px solid #FFFFFF;
                    border-radius: 50%;
                    box-shadow: 0 0 10px #FF3B5C;
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

                    // Outer neon glow line
                    L.polyline(latlngs, {
                        color: '#00FF66',
                        weight: 9,
                        opacity: 0.25,
                        lineCap: 'round',
                        lineJoin: 'round'
                    }).addTo(map);

                    // Main route line
                    var routeLine = L.polyline(latlngs, {
                        color: '#00FF66',
                        weight: 4.5,
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondarySilver
        )
    }
}
