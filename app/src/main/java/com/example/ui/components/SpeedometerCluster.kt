package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GpsSignalQuality
import com.example.model.MotionStatus
import com.example.model.SpeedUnit
import com.example.model.TelemetryFormatter
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.OrbitronFontFamily
import com.example.ui.theme.PanelBorderGray
import com.example.ui.theme.PanelDarkGray
import com.example.ui.theme.PanelElevatedGray
import com.example.ui.theme.TextMutedSlate
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondarySilver
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DigitalSpeedometerGauge(
    speedKmh: Float,
    motionStatus: MotionStatus,
    gpsSignalQuality: GpsSignalQuality,
    gpsAccuracyMeters: Float,
    speedUnit: SpeedUnit,
    isSimulationActive: Boolean,
    modifier: Modifier = Modifier
) {
    val displayedSpeed = (speedKmh * speedUnit.multiplier).coerceAtLeast(0f)
    val animatedSpeed by animateFloatAsState(
        targetValue = displayedSpeed,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "speed_anim"
    )

    val maxScaleSpeed = if (speedUnit == SpeedUnit.KMH) 160f else 100f
    val progress = (animatedSpeed / maxScaleSpeed).coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val statusColor by animateColorAsState(
        targetValue = when (motionStatus) {
            MotionStatus.MOVING -> NeonGreen
            MotionStatus.STOPPED -> AmberWarning
            MotionStatus.SEARCHING_GPS -> CyberCyan
            MotionStatus.TRACKING_OFF -> TextMutedSlate
        },
        label = "status_color"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0E1520),
                        PanelDarkGray,
                        CarbonBlack
                    )
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        statusColor.copy(alpha = 0.65f),
                        PanelBorderGray,
                        CyberCyan.copy(alpha = 0.35f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .testTag("speedometer_cluster_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top bar inside gauge: GPS Indicator + Simulation/Live tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GpsSignalBadge(
                    signalQuality = gpsSignalQuality,
                    accuracyMeters = gpsAccuracyMeters
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(PanelElevatedGray)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TwoWheeler,
                        contentDescription = "Ikon Motor",
                        tint = if (motionStatus == MotionStatus.MOVING) NeonGreen else TextSecondarySilver,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSimulationActive) "MODE UJI SIMULASI" else "LIVE TELEMETRY",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSimulationActive) CyberCyan else TextSecondarySilver
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Arc Gauge + Big Speed Number
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(235.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 14.dp.toPx()
                    val padding = strokeWidth + 10.dp.toPx()
                    val arcSize = Size(size.width - padding * 2, size.height - padding * 2)
                    val topLeft = Offset(padding, padding)
                    val startAngle = 135f
                    val totalSweep = 270f

                    // Subtle outer glow ring
                    drawArc(
                        color = statusColor.copy(alpha = 0.10f),
                        startAngle = startAngle,
                        sweepAngle = totalSweep,
                        useCenter = false,
                        topLeft = Offset(padding - 6.dp.toPx(), padding - 6.dp.toPx()),
                        size = Size(arcSize.width + 12.dp.toPx(), arcSize.height + 12.dp.toPx()),
                        style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Background track arc
                    drawArc(
                        color = PanelElevatedGray,
                        startAngle = startAngle,
                        sweepAngle = totalSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Segmented tick marks around the speedometer
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val outerRadius = (size.width / 2f) - 4.dp.toPx()
                    val innerRadiusMajor = outerRadius - 10.dp.toPx()
                    val innerRadiusMinor = outerRadius - 5.dp.toPx()
                    val totalTicks = 27
                    for (i in 0..totalTicks) {
                        val tickFraction = i.toFloat() / totalTicks
                        val angleDeg = startAngle + (tickFraction * totalSweep)
                        val angleRad = Math.toRadians(angleDeg.toDouble())
                        val isMajor = i % 3 == 0
                        val rStart = if (isMajor) innerRadiusMajor else innerRadiusMinor
                        val startPt = Offset(
                            x = center.x + (rStart * cos(angleRad)).toFloat(),
                            y = center.y + (rStart * sin(angleRad)).toFloat()
                        )
                        val endPt = Offset(
                            x = center.x + (outerRadius * cos(angleRad)).toFloat(),
                            y = center.y + (outerRadius * sin(angleRad)).toFloat()
                        )
                        val tickColor = if (tickFraction <= progress && progress > 0.01f) {
                            if (tickFraction > 0.78f) AmberWarning else NeonGreen
                        } else {
                            PanelBorderGray
                        }
                        drawLine(
                            color = tickColor,
                            start = startPt,
                            end = endPt,
                            strokeWidth = if (isMajor) 2.5.dp.toPx() else 1.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Active Speed Sweep Arc
                    if (progress > 0.005f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    NeonGreen,
                                    CyberCyan,
                                    NeonGreen,
                                    AmberWarning
                                )
                            ),
                            startAngle = startAngle,
                            sweepAngle = totalSweep * progress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                // Digital Speed Readout in Center
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = TelemetryFormatter.formatSpeedInt(animatedSpeed, 1.0f),
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 68.sp,
                        color = if (motionStatus == MotionStatus.MOVING) NeonGreen else TextPrimaryWhite,
                        modifier = Modifier.testTag("live_speed_value")
                    )
                    Text(
                        text = speedUnit.symbol,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 2.sp,
                        color = CyberCyan
                    )
                }
            }

            // Motion Status Pill (● MOTOR BERJALAN / ● MOTOR BERHENTI)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(statusColor.copy(alpha = 0.14f))
                    .border(1.dp, statusColor.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .testTag("motion_status_badge")
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            statusColor.copy(
                                alpha = if (motionStatus == MotionStatus.MOVING) pulseAlpha else 1f
                            )
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = motionStatus.labelId,
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.2.sp,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
fun GpsSignalBadge(
    signalQuality: GpsSignalQuality,
    accuracyMeters: Float
) {
    val tint = when (signalQuality) {
        GpsSignalQuality.EXCELLENT, GpsSignalQuality.GOOD -> NeonGreen
        GpsSignalQuality.SIMULATED -> CyberCyan
        GpsSignalQuality.FAIR -> AmberWarning
        GpsSignalQuality.WEAK, GpsSignalQuality.NO_SIGNAL -> CrimsonRed
    }
    val icon = when (signalQuality) {
        GpsSignalQuality.EXCELLENT, GpsSignalQuality.GOOD, GpsSignalQuality.SIMULATED -> Icons.Default.GpsFixed
        GpsSignalQuality.FAIR, GpsSignalQuality.WEAK -> Icons.Default.GpsNotFixed
        GpsSignalQuality.NO_SIGNAL -> Icons.Default.GpsOff
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(PanelElevatedGray)
            .border(1.dp, tint.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("gps_signal_badge")
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Indikator Sinyal GPS",
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        val accuracySuffix = if (accuracyMeters > 0f) " (±${accuracyMeters.toInt()}m)" else ""
        Text(
            text = "GPS: ${signalQuality.label}$accuracySuffix",
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

@Composable
fun PermanentOdometerDisplayCard(
    totalOdometerKm: Double,
    tripOdometerKm: Double,
    speedUnit: SpeedUnit,
    modifier: Modifier = Modifier
) {
    val scaledTotal = (totalOdometerKm * speedUnit.multiplier).coerceAtLeast(0.0)
    val wholePart = scaledTotal.toLong()
    val tenthPart = ((scaledTotal - wholePart) * 10.0).toInt().coerceIn(0, 9)
    val paddedDigits = String.format("%06d", wholePart.coerceAtMost(999999L))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PanelDarkGray)
            .border(1.dp, PanelBorderGray, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        // Header for Permanent Total Odometer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Odometer Permanen Terkunci",
                    tint = NeonGreen,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "TOTAL ODOMETER PERMANEN",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonGreen
                )
            }
            Text(
                text = speedUnit.distanceSymbol,
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondarySilver
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Segmented Digital Drum Odometer Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("total_odometer_display"),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            paddedDigits.forEach { digitChar ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CarbonBlack)
                        .border(1.dp, PanelBorderGray, RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = digitChar.toString(),
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 23.sp,
                        color = TextPrimaryWhite
                    )
                }
            }

            Text(
                text = ",",
                fontFamily = OrbitronFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = NeonGreen
            )

            // Decimal 1/10th KM Drum (Highlighted in Neon Green)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeonGreen.copy(alpha = 0.15f))
                    .border(1.5.dp, NeonGreen, RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = tenthPart.toString(),
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 23.sp,
                    color = NeonGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Secondary Row: TRIP ODOMETER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PanelElevatedGray)
                .border(1.dp, CyberCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("trip_odometer_display"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TRIP ODOMETER",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberCyan
                )
                Text(
                    text = "Dapat di-reset manual kapan saja",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 11.sp,
                    color = TextSecondarySilver
                )
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = TelemetryFormatter.formatOdometerHighPrecision(tripOdometerKm, speedUnit.multiplier),
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = CyberCyan
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = speedUnit.distanceSymbol,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondarySilver,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}
