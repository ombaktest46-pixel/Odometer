package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import com.example.model.RoutePoint
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.PanelBorderGray
import com.example.ui.theme.PanelElevatedGray
import com.example.ui.theme.TextSecondarySilver
import kotlin.math.max

/**
 * Interactive Vector Route Map Canvas that renders the recorded GPS coordinates
 * with speed-coded telemetry segments, start/finish pins, grid lines, and pinch/button zoom.
 */
@Composable
fun VectorRouteMapCanvas(
    routePoints: List<RoutePoint>,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CarbonBlack)
            .border(1.dp, PanelBorderGray, RoundedCornerShape(18.dp))
    ) {
        // Top Map Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelElevatedGray)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = "Kompas Peta Rute",
                    tint = CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PETA RUTE VEKTOR GPS (${routePoints.size} Titik)",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberCyan
                )
            }

            // Zoom Controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { zoom = (zoom * 1.3f).coerceIn(0.7f, 6f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Perbesar Peta",
                        tint = NeonGreen,
                        modifier = Modifier.size(18.dp)
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
                        modifier = Modifier.size(18.dp)
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
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Main Interactive Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(0.7f, 6f)
                        panOffset += pan
                    }
                }
                .testTag("vector_route_map_canvas")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw tactical telemetry grid
                val gridSpacing = 32.dp.toPx()
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

                if (routePoints.isEmpty()) {
                    return@Canvas
                }

                val minLat = routePoints.minOf { it.lat }
                val maxLat = routePoints.maxOf { it.lat }
                val minLng = routePoints.minOf { it.lng }
                val maxLng = routePoints.maxOf { it.lng }

                val latSpan = max(maxLat - minLat, 0.00025)
                val lngSpan = max(maxLng - minLng, 0.00025)

                val paddingPx = 36.dp.toPx()
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

                // Glow path underneath
                if (projected.size >= 2) {
                    val fullPath = Path().apply {
                        moveTo(projected.first().x, projected.first().y)
                        for (i in 1 until projected.size) {
                            lineTo(projected[i].x, projected[i].y)
                        }
                    }
                    drawPath(
                        path = fullPath,
                        color = NeonGreen.copy(alpha = 0.18f),
                        style = Stroke(
                            width = 12.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Speed-colored route segments
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
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Draw Start Marker (Neon Green outer + dark center)
                val firstPt = projected.first()
                drawCircle(
                    color = NeonGreen.copy(alpha = 0.35f),
                    radius = 12.dp.toPx(),
                    center = firstPt
                )
                drawCircle(
                    color = NeonGreen,
                    radius = 6.dp.toPx(),
                    center = firstPt
                )

                // Draw Finish Marker (Crimson Red / Amber pin)
                val lastPt = projected.last()
                drawCircle(
                    color = CrimsonRed.copy(alpha = 0.35f),
                    radius = 12.dp.toPx(),
                    center = lastPt
                )
                drawCircle(
                    color = CrimsonRed,
                    radius = 6.dp.toPx(),
                    center = lastPt
                )
            }

            if (routePoints.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = "Data koordinat rute belum tersedia untuk perjalanan ini",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondarySilver
                    )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendDot(color = NeonGreen, text = "Awal: $startLabel")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendDot(color = CrimsonRed, text = "Akhir: $endLabel")
            }
        }
    }
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
