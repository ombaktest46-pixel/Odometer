package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.TwoWheeler
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SpeedUnit
import com.example.model.TelemetryFormatter
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.OrbitronFontFamily
import com.example.ui.theme.PanelBorderGray
import com.example.ui.theme.PanelDarkGray
import com.example.ui.theme.PanelElevatedGray
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondarySilver
import com.example.viewmodel.ChartBarPoint
import com.example.viewmodel.RideStatistics

@Composable
fun StatisticsScreen(
    statistics: RideStatistics,
    speedUnit: SpeedUnit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CarbonBlack),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "STATISTIK BERKENDARA",
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = NeonGreen
                    )
                    Text(
                        text = "Rekapitulasi jarak tempuh, durasi, dan grafik perjalanan",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondarySilver
                    )
                }
            }

            // 1. All-Time Permanent Odometer & Total Driving Duration Hero Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF0E261B),
                                    PanelDarkGray
                                )
                            )
                        )
                        .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(18.dp)
                        .testTag("stats_all_time_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "TOTAL KILOMETER KESELURUHAN",
                                style = MaterialTheme.typography.labelMedium,
                                color = NeonGreen
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = TelemetryFormatter.formatOdometer(
                                        statistics.totalPermanentOdometerKm,
                                        speedUnit.multiplier
                                    ),
                                    fontFamily = OrbitronFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 34.sp,
                                    color = TextPrimaryWhite
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = speedUnit.distanceSymbol,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NeonGreen,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.TwoWheeler,
                            contentDescription = "Total Odometer Motor",
                            tint = NeonGreen.copy(alpha = 0.85f),
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CarbonBlack.copy(alpha = 0.6f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Total Durasi Berkendara",
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "TOTAL DURASI BERKENDARA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondarySilver
                                )
                                Text(
                                    text = TelemetryFormatter.formatDurationHms(statistics.totalDrivingDurationSeconds),
                                    fontFamily = OrbitronFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = CyberCyan
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "KEC. MAKS REKOR",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondarySilver
                            )
                            Text(
                                text = "${TelemetryFormatter.formatSpeedOneDecimal(statistics.allTimeMaxSpeedKmh, speedUnit.multiplier)} ${speedUnit.symbol}",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = AmberWarning
                            )
                        }
                    }
                }
            }

            // 2. Period Distance Cards: Hari Ini, Minggu Ini, Bulan Ini
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PeriodStatCard(
                        icon = Icons.Default.Today,
                        title = "HARI INI",
                        distanceKm = statistics.todayDistanceKm,
                        speedUnit = speedUnit,
                        accentColor = NeonGreen,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stats_today_card")
                    )
                    PeriodStatCard(
                        icon = Icons.Default.DateRange,
                        title = "MINGGU INI",
                        distanceKm = statistics.thisWeekDistanceKm,
                        speedUnit = speedUnit,
                        accentColor = CyberCyan,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stats_week_card")
                    )
                    PeriodStatCard(
                        icon = Icons.Default.CalendarMonth,
                        title = "BULAN INI",
                        distanceKm = statistics.thisMonthDistanceKm,
                        speedUnit = speedUnit,
                        accentColor = AmberWarning,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stats_month_card")
                    )
                }
            }

            // 3. Daily Distance Chart (Last 7 Days)
            item {
                TelemetryBarChartCard(
                    title = "GRAFIK PERJALANAN HARIAN (7 HARI TERAKHIR)",
                    subtitle = "Ketuk batang grafik untuk melihat detail tanggal & jarak",
                    points = statistics.dailyChartPoints,
                    speedUnit = speedUnit,
                    barAccentColor = NeonGreen,
                    testTag = "chart_daily_card"
                )
            }

            // 4. Monthly Distance Chart (Last 6 Months)
            item {
                TelemetryBarChartCard(
                    title = "GRAFIK PERJALANAN BULANAN (6 BULAN TERAKHIR)",
                    subtitle = "Akumulasi jarak tempuh bulanan",
                    points = statistics.monthlyChartPoints,
                    speedUnit = speedUnit,
                    barAccentColor = CyberCyan,
                    testTag = "chart_monthly_card"
                )
            }

            // 5. Additional Performance Summary Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PanelDarkGray)
                        .border(1.dp, PanelBorderGray, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Kecepatan Rata-rata Keseluruhan",
                            tint = CyberCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Rata-Rata Kecepatan Keseluruhan",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimaryWhite
                            )
                            Text(
                                text = "Dari ${statistics.totalRecordedTripsCount} sesi perjalanan tersimpan",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondarySilver
                            )
                        }
                    }

                    Text(
                        text = "${TelemetryFormatter.formatSpeedOneDecimal(statistics.overallAvgSpeedKmh, speedUnit.multiplier)} ${speedUnit.symbol}",
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = NeonGreen
                    )
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
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PanelDarkGray)
            .border(1.dp, PanelBorderGray, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondarySilver
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = TelemetryFormatter.formatOdometer(distanceKm, speedUnit.multiplier),
            fontFamily = OrbitronFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            color = TextPrimaryWhite
        )
        Text(
            text = speedUnit.distanceSymbol,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor
        )
    }
}

@Composable
private fun TelemetryBarChartCard(
    title: String,
    subtitle: String,
    points: List<ChartBarPoint>,
    speedUnit: SpeedUnit,
    barAccentColor: Color,
    testTag: String
) {
    var selectedIndex by remember(points.size) {
        mutableIntStateOf((points.size - 1).coerceAtLeast(0))
    }
    val maxDist = (points.maxOfOrNull { it.distanceKm } ?: 1.0).coerceAtLeast(1.0)
    val selectedPt = points.getOrNull(selectedIndex)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PanelDarkGray)
            .border(1.dp, PanelBorderGray, RoundedCornerShape(18.dp))
            .padding(16.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = title,
                        tint = barAccentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = barAccentColor
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 11.sp,
                    color = TextSecondarySilver
                )
            }

            if (selectedPt != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = selectedPt.fullLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondarySilver
                    )
                    Text(
                        text = "${TelemetryFormatter.formatOdometer(selectedPt.distanceKm, speedUnit.multiplier)} ${speedUnit.distanceSymbol}",
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = barAccentColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Interactive Bar Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            points.forEachIndexed { idx, pt ->
                val isSelected = idx == selectedIndex
                val fraction = (pt.distanceKm / maxDist).toFloat().coerceIn(0.06f, 1f)

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
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = if (isSelected) barAccentColor else TextSecondarySilver
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .weight(1f),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val barH = size.height * fraction
                            val topY = size.height - barH
                            // Track background
                            drawRoundRect(
                                color = PanelElevatedGray,
                                topLeft = Offset(0f, 0f),
                                size = size,
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                            // Active bar
                            drawRoundRect(
                                color = if (isSelected) barAccentColor else barAccentColor.copy(alpha = 0.55f),
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
                        color = if (isSelected) TextPrimaryWhite else TextSecondarySilver
                    )
                }
            }
        }
    }
}
