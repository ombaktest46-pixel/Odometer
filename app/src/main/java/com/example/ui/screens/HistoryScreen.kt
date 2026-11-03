package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RouteJsonCodec
import com.example.data.TripEntity
import com.example.model.SpeedUnit
import com.example.model.TelemetryFormatter
import com.example.ui.components.MiniRoutePreviewCanvas
import com.example.ui.components.MotoCard
import com.example.ui.components.VectorRouteMapCanvas
import com.example.ui.theme.InterFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    trips: List<TripEntity>,
    totalPermanentOdometerKm: Double,
    speedUnit: SpeedUnit,
    onDeleteTrip: (Long) -> Unit,
    onDeleteAllTrips: () -> Unit,
    onStartSimulationFromEmpty: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme

    var selectedTrip by remember { mutableStateOf<TripEntity?>(null) }
    var showOverviewMap by remember { mutableStateOf(true) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    if (selectedTrip != null) {
        BackHandler {
            selectedTrip = null
        }
        TripDetailView(
            trip = selectedTrip!!,
            speedUnit = speedUnit,
            onBack = { selectedTrip = null },
            onDelete = {
                onDeleteTrip(selectedTrip!!.id)
                selectedTrip = null
            },
            modifier = modifier
        )
        return
    }

    val latestTripWithRoute = remember(trips) {
        trips.firstOrNull { RouteJsonCodec.decodeRoutePoints(it.routePointsJson).isNotEmpty() }
            ?: trips.firstOrNull()
    }
    val overviewRouteSegments = remember(trips) {
        trips.mapNotNull { trip ->
            RouteJsonCodec.decodeRoutePoints(trip.routePointsJson).takeIf { it.isNotEmpty() }
        }
    }
    val overviewRoutePoints = remember(overviewRouteSegments) {
        overviewRouteSegments.flatten()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(cs.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // 1. HEADER: Riwayat Perjalanan + Total recorded trips + Clean subtitle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Riwayat Perjalanan",
                        style = MaterialTheme.typography.headlineMedium,
                        color = cs.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${trips.size} perjalanan tercatat • Ketuk kartu untuk melihat peta detail",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (showOverviewMap) cs.secondary.copy(alpha = 0.12f) else cs.surface)
                            .border(
                                width = 1.dp,
                                color = if (showOverviewMap) cs.secondary.copy(alpha = 0.4f) else cs.outline,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { showOverviewMap = !showOverviewMap }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("btn_toggle_history_overview_map")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Map,
                                contentDescription = "Tampilkan Peta Rute",
                                tint = if (showOverviewMap) cs.secondary else cs.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showOverviewMap) "Tutup Peta" else "Buka Peta",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (showOverviewMap) cs.secondary else cs.onSurfaceVariant
                            )
                        }
                    }

                    if (trips.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { showClearAllDialog = true },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(cs.error.copy(alpha = 0.10f))
                                .border(1.dp, cs.error.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                                .testTag("btn_clear_all_history")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = "Hapus Semua Riwayat",
                                tint = cs.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Small Informational Card for Permanent Odometer Protection
            MotoCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(cs.primary.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = "Proteksi Odometer Permanen",
                            tint = cs.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Proteksi Odometer Permanen: Total ${TelemetryFormatter.formatOdometer(totalPermanentOdometerKm, speedUnit.multiplier)} ${speedUnit.distanceSymbol} tersimpan aman dan tidak terpengaruh saat riwayat dihapus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Trip Cards List + Optional Overview Map + Clean Empty State
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (showOverviewMap) {
                    item {
                        VectorRouteMapCanvas(
                            routePoints = overviewRoutePoints,
                            routeSegments = overviewRouteSegments,
                            startLabel = latestTripWithRoute?.startCoordLabel ?: "Menunggu Rute GPS",
                            endLabel = latestTripWithRoute?.endCoordLabel ?: "Titik Parkir GPS",
                            mapHeight = 220.dp
                        )
                    }
                }

                if (trips.isEmpty()) {
                    item {
                        MotoCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(cs.secondary.copy(alpha = 0.10f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Route,
                                        contentDescription = "Belum Ada Riwayat",
                                        tint = cs.secondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Belum Ada Riwayat Perjalanan",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = cs.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Saat Auto Tracking aktif dan motor mulai bergerak, setiap sesi perjalanan beserta rute GPS dan statistiknya akan tersimpan otomatis di sini.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(trips, key = { it.id }) { trip ->
                        TripHistoryItemCard(
                            trip = trip,
                            speedUnit = speedUnit,
                            onClick = { selectedTrip = trip }
                        )
                    }
                }
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            containerColor = cs.surface,
            titleContentColor = cs.onSurface,
            textContentColor = cs.onSurfaceVariant,
            title = { Text("Hapus Semua Riwayat Perjalanan?") },
            text = {
                Text(
                    "Seluruh daftar riwayat perjalanan akan dihapus. " +
                        "Total Odometer Permanen Anda (${TelemetryFormatter.formatOdometer(totalPermanentOdometerKm, speedUnit.multiplier)} ${speedUnit.distanceSymbol}) tetap utuh dan tidak akan terhapus."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllDialog = false
                        onDeleteAllTrips()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.error,
                        contentColor = Color.White
                    )
                ) {
                    Text("Hapus Riwayat")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Batal", color = cs.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun TripHistoryItemCard(
    trip: TripEntity,
    speedUnit: SpeedUnit,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val indoLocale = remember { Locale("id", "ID") }
    val dateFmt = remember { SimpleDateFormat("EEEE, dd MMM yyyy", indoLocale) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", indoLocale) }
    val routePoints = remember(trip.routePointsJson) {
        RouteJsonCodec.decodeRoutePoints(trip.routePointsJson)
    }

    val dateStr = dateFmt.format(Date(trip.startTimeMillis))
    val startTimeStr = timeFmt.format(Date(trip.startTimeMillis))
    val endTimeStr = timeFmt.format(Date(trip.endTimeMillis))

    MotoCard(
        onClick = onClick,
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trip_item_card_${trip.id}")
    ) {
        // Top Row: Route Preview, Date + Start/End Time, Distance
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniRoutePreviewCanvas(
                routePoints = routePoints,
                modifier = Modifier.size(width = 64.dp, height = 50.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = "Waktu Mulai dan Selesai",
                        tint = cs.secondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$startTimeStr – $endTimeStr",
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        color = cs.secondary
                    )
                }
            }

            // Distance value
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = TelemetryFormatter.formatOdometerHighPrecision(trip.distanceKm, speedUnit.multiplier),
                    style = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp,
                        fontFeatureSettings = "tnum"
                    ),
                    color = cs.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = speedUnit.distanceSymbol,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.secondary,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(thickness = 1.dp, color = cs.outline)
        Spacer(modifier = Modifier.height(10.dp))

        // Bottom Row: Duration, Average Speed, Maximum Speed
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TripSubMetric(
                label = "Durasi",
                value = TelemetryFormatter.formatDurationReadable(trip.durationSeconds)
            )
            TripSubMetric(
                label = "Rata-rata",
                value = "${TelemetryFormatter.formatSpeedOneDecimal(trip.avgSpeedKmh, speedUnit.multiplier)} ${speedUnit.symbol}"
            )
            TripSubMetric(
                label = "Maksimal",
                value = "${TelemetryFormatter.formatSpeedOneDecimal(trip.maxSpeedKmh, speedUnit.multiplier)} ${speedUnit.symbol}"
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(cs.secondary.copy(alpha = 0.10f))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Map,
                    contentDescription = "Buka Peta Rute Lengkap",
                    tint = cs.secondary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Detail",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.secondary
                )
            }
        }
    }
}

@Composable
private fun TripSubMetric(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                fontFeatureSettings = "tnum"
            ),
            color = cs.onSurface
        )
    }
}

@Composable
private fun TripDetailView(
    trip: TripEntity,
    speedUnit: SpeedUnit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val indoLocale = remember { Locale("id", "ID") }
    val dateFmt = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", indoLocale) }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", indoLocale) }
    val routePoints = remember(trip.routePointsJson) {
        RouteJsonCodec.decodeRoutePoints(trip.routePointsJson)
    }

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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(cs.surface)
                                .border(1.dp, cs.outline, RoundedCornerShape(10.dp))
                                .testTag("btn_back_from_trip_detail")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Kembali ke Riwayat",
                                tint = cs.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Detail Perjalanan",
                                style = MaterialTheme.typography.titleLarge,
                                color = cs.onBackground
                            )
                            Text(
                                text = dateFmt.format(Date(trip.startTimeMillis)),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(cs.error.copy(alpha = 0.10f))
                            .border(1.dp, cs.error.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Hapus Perjalanan Ini",
                            tint = cs.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            item {
                VectorRouteMapCanvas(
                    routePoints = routePoints,
                    startLabel = trip.startCoordLabel,
                    endLabel = trip.endCoordLabel,
                    mapHeight = 300.dp
                )
            }

            item {
                MotoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Ringkasan Perjalanan",
                            style = MaterialTheme.typography.titleMedium,
                            color = cs.onSurface
                        )
                        HorizontalDivider(thickness = 1.dp, color = cs.outline)

                        DetailRow(
                            label = "Total Jarak Tempuh",
                            value = "${TelemetryFormatter.formatOdometerHighPrecision(trip.distanceKm, speedUnit.multiplier)} ${speedUnit.distanceSymbol}",
                            highlight = cs.primary
                        )
                        DetailRow(
                            label = "Waktu Mulai",
                            value = timeFmt.format(Date(trip.startTimeMillis))
                        )
                        DetailRow(
                            label = "Waktu Selesai",
                            value = timeFmt.format(Date(trip.endTimeMillis))
                        )
                        DetailRow(
                            label = "Durasi Berkendara",
                            value = TelemetryFormatter.formatDurationHms(trip.durationSeconds)
                        )
                        DetailRow(
                            label = "Kecepatan Rata-Rata",
                            value = "${TelemetryFormatter.formatSpeedOneDecimal(trip.avgSpeedKmh, speedUnit.multiplier)} ${speedUnit.symbol}",
                            highlight = cs.secondary
                        )
                        DetailRow(
                            label = "Kecepatan Maksimal",
                            value = "${TelemetryFormatter.formatSpeedOneDecimal(trip.maxSpeedKmh, speedUnit.multiplier)} ${speedUnit.symbol}",
                            highlight = cs.secondary
                        )
                        DetailRow(
                            label = "Koordinat Awal",
                            value = trip.startCoordLabel
                        )
                        DetailRow(
                            label = "Koordinat Akhir",
                            value = trip.endCoordLabel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    highlight: Color = MaterialTheme.colorScheme.onSurface
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant
        )
        Text(
            text = value,
            style = TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                fontFeatureSettings = "tnum"
            ),
            color = highlight
        )
    }
}
