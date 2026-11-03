package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SpeedUnit
import com.example.model.TelemetryFormatter
import com.example.ui.components.MotoCard
import com.example.ui.theme.InterFontFamily
import com.example.viewmodel.ChartBarPoint
import com.example.viewmodel.RideStatistics

@Composable
fun StatisticsScreen(
    statistics: RideStatistics,
    speedUnit: SpeedUnit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(cs.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            item {
                Column {
                    Text(
                        text = "Statistik",
                        style = MaterialTheme.typography.headlineMedium,
                        color = cs.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Analitik jarak tempuh, durasi berkendara, dan tren perjalanan",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            // 1. SUMMARY CARD: Total kilometers, Total riding duration, Maximum recorded speed
            item {
                MotoCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stats_all_time_card")
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "TOTAL KILOMETER",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = cs.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = TelemetryFormatter.formatOdometer(
                                            statistics.totalPermanentOdometerKm,
                                            speedUnit.multiplier
                                        ),
                                        style = TextStyle(
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 36.sp,
                                            letterSpacing = (-0.8).sp,
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = cs.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = speedUnit.distanceSymbol,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = cs.primary,
                                        modifier = Modifier.padding(bottom = 5.dp)
                                    )
                                }
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(cs.primary.copy(alpha = 0.12f))
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.TwoWheeler,
                                    contentDescription = "Total Odometer Motor",
                                    tint = cs.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(thickness = 1.dp, color = cs.outline)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Schedule,
                                    contentDescription = "Total Durasi Berkendara",
                                    tint = cs.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Total Durasi Berkendara",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cs.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = TelemetryFormatter.formatDurationHms(statistics.totalDrivingDurationSeconds),
                                        style = TextStyle(
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = cs.onSurface
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Kecepatan Maksimum",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${TelemetryFormatter.formatSpeedOneDecimal(statistics.allTimeMaxSpeedKmh, speedUnit.multiplier)} ${speedUnit.symbol}",
                                    style = TextStyle(
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = cs.secondary
                                )
                            }
                        }
                    }
                }
            }

            // 2. PERIOD CARDS: Today, This week, This month
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PeriodStatCard(
                        icon = Icons.Outlined.Today,
                        title = "Hari Ini",
                        distanceKm = statistics.todayDistanceKm,
                        speedUnit = speedUnit,
                        iconTint = cs.primary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stats_today_card")
                    )
                    PeriodStatCard(
                        icon = Icons.Outlined.DateRange,
                        title = "Minggu Ini",
                        distanceKm = statistics.thisWeekDistanceKm,
                        speedUnit = speedUnit,
                        iconTint = cs.secondary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stats_week_card")
                    )
                    PeriodStatCard(
                        icon = Icons.Outlined.CalendarMonth,
                        title = "Bulan Ini",
                        distanceKm = statistics.thisMonthDistanceKm,
                        speedUnit = speedUnit,
                        iconTint = cs.secondary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stats_month_card")
                    )
                }
            }

            // 3. DAILY STATISTICS: Seven-day bar chart with minimal grid lines & Green selected bar
            item {
                TelemetryBarChartCard(
                    title = "Statistik Harian (7 Hari)",
                    subtitle = "Ketuk batang untuk melihat jarak harian",
                    points = statistics.dailyChartPoints,
                    speedUnit = speedUnit,
                    selectedBarColor = cs.primary, // GREEN selected bar
                    testTag = "chart_daily_card"
                )
            }

            // 4. MONTHLY STATISTICS: Six-month distance chart with Blue data visualization
            item {
                TelemetryBarChartCard(
                    title = "Statistik Bulanan (6 Bulan)",
                    subtitle = "Akumulasi jarak tempuh per bulan",
                    points = statistics.monthlyChartPoints,
                    speedUnit = speedUnit,
                    selectedBarColor = cs.secondary, // BLUE data visualization
                    testTag = "chart_monthly_card"
                )
            }

            // 5. Overall Average Speed Summary Card
            item {
                MotoCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(cs.secondary.copy(alpha = 0.10f))
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Speed,
                                    contentDescription = "Rata-rata Kecepatan",
                                    tint = cs.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Rata-Rata Kecepatan",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = cs.onSurface
                                )
                                Text(
                                    text = "${statistics.totalRecordedTripsCount} perjalanan tercatat",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = "${TelemetryFormatter.formatSpeedOneDecimal(statistics.overallAvgSpeedKmh, speedUnit.multiplier)} ${speedUnit.symbol}",
                            style = TextStyle(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                fontFeatureSettings = "tnum"
                            ),
                            color = cs.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodStatCard(
    icon: ImageVector,
    title: String,
    distanceKm: Double,
    speedUnit: SpeedUnit,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme

    MotoCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = TelemetryFormatter.formatOdometer(distanceKm, speedUnit.multiplier),
                style = TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    fontFeatureSettings = "tnum"
                ),
                color = cs.onSurface
            )
            Text(
                text = speedUnit.distanceSymbol,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TelemetryBarChartCard(
    title: String,
    subtitle: String,
    points: List<ChartBarPoint>,
    speedUnit: SpeedUnit,
    selectedBarColor: Color,
    testTag: String
) {
    val cs = MaterialTheme.colorScheme
    val gridLineColor = cs.outline
    val unselectedBarColor = selectedBarColor.copy(alpha = 0.25f)

    var selectedIndex by remember(points.size) {
        mutableIntStateOf((points.size - 1).coerceAtLeast(0))
    }
    val maxDist = (points.maxOfOrNull { it.distanceKm } ?: 1.0).coerceAtLeast(1.0)
    val selectedPt = points.getOrNull(selectedIndex)

    MotoCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.BarChart,
                            contentDescription = title,
                            tint = selectedBarColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = cs.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }

                if (selectedPt != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = selectedPt.fullLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant
                        )
                        Text(
                            text = "${TelemetryFormatter.formatOdometer(selectedPt.distanceKm, speedUnit.multiplier)} ${speedUnit.distanceSymbol}",
                            style = TextStyle(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                fontFeatureSettings = "tnum"
                            ),
                            color = selectedBarColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(156.dp)
            ) {
                // Minimal subtle horizontal grid lines
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 18.dp, bottom = 24.dp)
                ) {
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    val levels = listOf(0f, 0.5f, 1f)
                    levels.forEach { frac ->
                        val y = size.height * frac
                        drawLine(
                            color = gridLineColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (frac < 1f) dashEffect else null
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    points.forEachIndexed { idx, pt ->
                        val isSelected = idx == selectedIndex
                        val fraction = if (pt.distanceKm <= 0.0) {
                            0.04f
                        } else {
                            (pt.distanceKm / maxDist).toFloat().coerceIn(0.08f, 1f)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedIndex = idx }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = if (pt.distanceKm > 0.0) {
                                    TelemetryFormatter.formatOdometer(pt.distanceKm, speedUnit.multiplier)
                                } else {
                                    "0"
                                },
                                style = TextStyle(
                                    fontFamily = InterFontFamily,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 10.sp,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = if (isSelected) selectedBarColor else cs.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.58f)
                                    .weight(1f),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val barH = size.height * fraction
                                    val topY = size.height - barH
                                    drawRoundRect(
                                        color = if (isSelected) selectedBarColor else unselectedBarColor,
                                        topLeft = Offset(0f, topY),
                                        size = Size(size.width, barH),
                                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = pt.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) cs.onSurface else cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
