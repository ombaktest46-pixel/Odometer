package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.GpsNotFixed
import androidx.compose.material.icons.outlined.GpsOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GpsSignalQuality
import com.example.model.MotionStatus
import com.example.model.SpeedUnit
import com.example.model.TelemetryFormatter
import com.example.model.WidgetThemeStyle
import com.example.ui.theme.InterFontFamily

/**
 * Standardized Swiss/Minimalist Card component used across all screens.
 * - Corner radius: 16.dp (14–18px spec)
 * - Thin neutral border: 1.dp (#E7EAF0 in Light Mode, #2A3340 in Dark Mode)
 * - Subtle shadow in Light Mode, clean elevated surface in Dark Mode
 */
@Composable
fun MotoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isLight = cs.background.luminance() > 0.5f
    val shape = RoundedCornerShape(16.dp)
    val border = BorderStroke(1.dp, cs.outline)
    val shadow = if (isLight) 1.dp else 0.dp

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = cs.surface,
            border = border,
            shadowElevation = shadow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content
            )
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = cs.surface,
            border = border,
            shadowElevation = shadow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content
            )
        }
    }
}

/**
 * Clean Minimalist Speed Card:
 * - Large tabular speed number
 * - KM/H unit
 * - Minimal linear + segmented speed visualization
 * - GPS signal status & Live telemetry status
 */
@Composable
fun DigitalSpeedometerGauge(
    speedKmh: Float,
    speedUnit: SpeedUnit,
    motionStatus: MotionStatus,
    gpsSignalQuality: GpsSignalQuality,
    gpsAccuracyMeters: Float,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val displaySpeed = (speedKmh * speedUnit.multiplier).coerceAtLeast(0f)
    val maxGaugeSpeed = if (speedUnit == SpeedUnit.KMH) 120f else 80f
    val targetFraction = (displaySpeed / maxGaugeSpeed).coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "minimal_speed_bar"
    )

    val isMoving = motionStatus == MotionStatus.MOVING
    // Functional Color Hierarchy:
    // GREEN = Moving / Active Tracking
    // BLUE = Idle / Stopped with Tracking Ready
    // NEUTRAL = Tracking Off
    val statusColor = when (motionStatus) {
        MotionStatus.MOVING -> cs.primary
        MotionStatus.STOPPED -> cs.secondary
        MotionStatus.SEARCHING_GPS -> cs.secondary
        MotionStatus.TRACKING_OFF -> cs.onSurfaceVariant
    }

    MotoCard(
        modifier = modifier.testTag("speedometer_gauge_card"),
        contentPadding = PaddingValues(20.dp)
    ) {
        // Top Row: Live Telemetry Status + GPS Signal Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor.copy(alpha = 0.10f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("motion_status_pill")
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = motionStatus.labelId,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }

            GpsSignalBadge(
                quality = gpsSignalQuality,
                accuracyMeters = gpsAccuracyMeters
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Main Speed Readout (Tabular Numerals)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = TelemetryFormatter.formatSpeedInt(speedKmh, speedUnit.multiplier),
                    style = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 56.sp,
                        lineHeight = 58.sp,
                        letterSpacing = (-1.5).sp,
                        fontFeatureSettings = "tnum"
                    ),
                    color = cs.onSurface,
                    modifier = Modifier.testTag("live_speed_value")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (speedUnit == SpeedUnit.KMH) "KM/H" else "MPH",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "KECEPATAN LIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
                Text(
                    text = if (isMoving) "Sensor GPS Aktif" else "Siaga (0 ${speedUnit.symbol})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isMoving) cs.primary else cs.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Minimalist Automotive Speed Bar Visualization
        val trackColor = cs.surfaceVariant
        val barColor = if (isMoving) cs.primary else cs.secondary

        Column(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            ) {
                val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = radius
                )
                if (animatedProgress > 0.005f) {
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset.Zero,
                        size = Size(size.width * animatedProgress, size.height),
                        cornerRadius = radius
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val scaleLabels = if (speedUnit == SpeedUnit.KMH) {
                    listOf("0", "30", "60", "90", "120+")
                } else {
                    listOf("0", "20", "40", "60", "80+")
                }
                scaleLabels.forEach { mark ->
                    Text(
                        text = mark,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun GpsSignalBadge(
    quality: GpsSignalQuality,
    accuracyMeters: Float,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    // Functional Colors:
    // GREEN = Active / Good GPS
    // BLUE = Fair / Searching
    // RED = No Signal / Error
    val (tintColor, icon) = when (quality) {
        GpsSignalQuality.EXCELLENT, GpsSignalQuality.GOOD, GpsSignalQuality.SIMULATED ->
            cs.primary to Icons.Outlined.GpsFixed
        GpsSignalQuality.FAIR, GpsSignalQuality.WEAK ->
            cs.secondary to Icons.Outlined.GpsNotFixed
        GpsSignalQuality.NO_SIGNAL ->
            cs.error to Icons.Outlined.GpsOff
    }

    val accText = if (accuracyMeters > 0f && quality != GpsSignalQuality.NO_SIGNAL) {
        "±${accuracyMeters.toInt()}m"
    } else {
        ""
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(cs.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("gps_signal_badge")
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Status Sinyal GPS",
            tint = tintColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (accText.isNotEmpty()) "GPS ${quality.label} ($accText)" else "GPS ${quality.label}",
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurface
        )
    }
}

/**
 * Clean Permanent Odometer Card + Trip Odometer Card following the Card-Based UI System.
 */
@Composable
fun PermanentOdometerDisplayCard(
    totalOdometerKm: Double,
    tripOdometerKm: Double,
    speedUnit: SpeedUnit,
    onResetTripClick: () -> Unit = {},
    onCalibrateTotalClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val unitText = speedUnit.distanceSymbol.uppercase()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. PERMANENT ODOMETER CARD
        MotoCard(
            modifier = Modifier.testTag("total_odometer_card"),
            contentPadding = PaddingValues(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Odometer Permanen",
                        tint = cs.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Total Odometer Permanen",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onCalibrateTotalClick() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Sesuaikan Odometer",
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Sesuaikan",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = TelemetryFormatter.formatOdometer(totalOdometerKm, speedUnit.multiplier),
                    style = MaterialTheme.typography.displayLarge,
                    color = cs.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = unitText,
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Tersimpan permanen di perangkat • Tidak terhapus saat riwayat atau trip di-reset",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 12.sp,
                color = cs.onSurfaceVariant
            )
        }

        // 2. TRIP ODOMETER CARD
        MotoCard(
            modifier = Modifier.testTag("trip_odometer_display"),
            contentPadding = PaddingValues(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Trip Odometer",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Jarak perjalanan sesi ini • Dapat di-reset kapan saja",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = onResetTripClick,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, cs.outline),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("btn_reset_trip")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = "Reset Trip",
                        tint = cs.secondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Reset Trip",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = TelemetryFormatter.formatOdometerHighPrecision(tripOdometerKm, speedUnit.multiplier),
                    style = MaterialTheme.typography.displayMedium,
                    color = cs.secondary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = unitText,
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.secondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

/**
 * 2x2 Square Circular Ring Widget Preview (1 - 999.999 Odometer ring progression).
 */
@Composable
fun SquareCircularOdometerWidgetPreview(
    bikeName: String,
    totalOdometerKm: Double,
    tripOdometerKm: Double,
    speedUnit: SpeedUnit,
    widgetTheme: WidgetThemeStyle,
    isMoving: Boolean,
    sizeDp: Dp = 184.dp,
    modifier: Modifier = Modifier
) {
    val odoStr = TelemetryFormatter.formatOdometer(totalOdometerKm, speedUnit.multiplier)
    val tripStr = "Trip ${TelemetryFormatter.formatOdometerHighPrecision(tripOdometerKm, speedUnit.multiplier)} ${speedUnit.distanceSymbol}"
    val ringColor = Color(widgetTheme.ringColorHex)

    val targetOdoProgress = TelemetryFormatter.calculateOdometerRingProgress(
        km = totalOdometerKm,
        multiplier = speedUnit.multiplier
    )
    val animatedOdoProgress by animateFloatAsState(
        targetValue = targetOdoProgress,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "widget_odo_ring_progress"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(sizeDp)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(32.dp))
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(widgetTheme.startColorHex),
                        Color(widgetTheme.endColorHex)
                    )
                )
            )
            .padding(14.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 9.dp.toPx()
            val inset = strokePx / 2f + 4.dp.toPx()
            val arcDiameter = size.minDimension - inset * 2f
            val topLeft = Offset(inset, inset)
            val arcSize = Size(arcDiameter, arcDiameter)

            drawArc(
                color = Color.White.copy(alpha = 0.22f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            if (animatedOdoProgress > 0.001f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedOdoProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            } else {
                val topCenter = Offset(size.width / 2f, inset)
                drawCircle(
                    color = ringColor.copy(alpha = 0.7f),
                    radius = strokePx / 2f,
                    center = topCenter
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = bikeName,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = odoStr,
                style = TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = when {
                        odoStr.length >= 9 -> 20.sp
                        odoStr.length >= 7 -> 24.sp
                        else -> 27.sp
                    },
                    fontFeatureSettings = "tnum"
                ),
                color = Color.White,
                maxLines = 1
            )

            Text(
                text = "ODO / 999.999 ${speedUnit.distanceSymbol.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                color = Color.White.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(5.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = 9.dp, vertical = 2.dp)
            ) {
                Text(
                    text = tripStr,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.5.sp,
                    color = Color.White,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isMoving) Color(0xFF20B978) else Color.White.copy(alpha = 0.7f))
                )
                Spacer(modifier = Modifier.width(5.dp))
                Icon(
                    imageVector = Icons.Outlined.TwoWheeler,
                    contentDescription = "Motor",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
