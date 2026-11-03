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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RouteJsonCodec
import com.example.data.TripEntity
import com.example.model.SpeedUnit
import com.example.model.TelemetryFormatter
import com.example.ui.components.VectorRouteMapCanvas
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.OrbitronFontFamily
import com.example.ui.theme.PanelBorderGray
import com.example.ui.theme.PanelDarkGray
import com.example.ui.theme.PanelElevatedGray
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondarySilver
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
    onStartSimulationFromEmpty: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTrip by remember { mutableStateOf<TripEntity?>(null) }
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CarbonBlack),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "RIWAYAT PERJALANAN",
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = NeonGreen
                    )
                    Text(
                        text = "${trips.size} perjalanan tercatat otomatis",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondarySilver
                    )
                }

                if (trips.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearAllDialog = true },
                        modifier = Modifier.testTag("btn_clear_all_history")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Hapus Semua Riwayat",
                            tint = CrimsonRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Permanent Total Odometer Protection Notice Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PanelElevatedGray)
                    .border(1.dp, NeonGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Proteksi Odometer Permanen",
                    tint = NeonGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Total Odometer Permanen (${TelemetryFormatter.formatOdometer(totalPermanentOdometerKm, speedUnit.multiplier)} ${speedUnit.distanceSymbol}) tersimpan terpisah dan TIDAK akan berkurang meskipun riwayat dihapus.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = TextPrimaryWhite
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (trips.isEmpty()) {
                // Empty State with helpful tip and quick test simulation button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(PanelElevatedGray)
                                .border(1.dp, PanelBorderGray, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Route,
                                contentDescription = "Belum Ada Riwayat",
                                tint = CyberCyan,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Belum Ada Riwayat Perjalanan",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimaryWhite
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ketika Auto Tracking aktif dan motor mulai bergerak, perjalanan akan otomatis dicatat dan disimpan ke halaman ini saat motor parkir.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondarySilver,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = onStartSimulationFromEmpty,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberCyan,
                                contentColor = CarbonBlack
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("btn_empty_start_simulation")
                        ) {
                            Icon(
                                imageVector = Icons.Default.TwoWheeler,
                                contentDescription = "Uji Simulasi Perjalanan"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Uji Simulasi Berkendara Sekarang",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(trips, key = { it.id }) { trip ->
                        TripHistoryItemCard(
                            trip = trip,
                            speedUnit = speedUnit,
                            onClick = { selectedTrip = trip },
                            onDelete = { onDeleteTrip(trip.id) }
                        )
                    }
                }
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            containerColor = PanelDarkGray,
            titleContentColor = TextPrimaryWhite,
            textContentColor = TextSecondarySilver,
            title = { Text("Hapus Semua Riwayat Perjalanan?") },
            text = {
                Text(
                    "Seluruh daftar riwayat perjalanan akan dihapus dari database. " +
                        "Total Odometer Permanen Anda (${TelemetryFormatter.formatOdometer(totalPermanentOdometerKm, speedUnit.multiplier)} ${speedUnit.distanceSymbol}) TIDAK AKAN TERHAPUS."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllDialog = false
                        onDeleteAllTrips()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonRed,
                        contentColor = Color.White
                    )
                ) {
                    Text("Hapus Riwayat")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Batal", color = TextSecondarySilver)
                }
            }
        )
    }
}

@Composable
private fun TripHistoryItemCard(
    trip: TripEntity,
    speedUnit: SpeedUnit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val indoLocale = remember { Locale("id", "ID") }
    val dateFmt = remember { SimpleDateFormat("EEEE, dd MMM yyyy", indoLocale) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", indoLocale) }

    val dateStr = dateFmt.format(Date(trip.startTimeMillis))
    val timeRangeStr = "${timeFmt.format(Date(trip.startTimeMillis))} - ${timeFmt.format(Date(trip.endTimeMillis))} WIB"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PanelDarkGray)
            .border(1.dp, PanelBorderGray, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(16.dp)
            .testTag("trip_item_card_${trip.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimaryWhite
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Waktu Mulai dan Selesai",
                        tint = CyberCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = timeRangeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan
                    )
                    if (trip.isSimulated) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• SIMULASI",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberWarning
                        )
                    }
                }
            }

            // Distance Badge
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = TelemetryFormatter.formatOdometerHighPrecision(trip.distanceKm, speedUnit.multiplier),
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = NeonGreen
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = speedUnit.distanceSymbol,
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonGreen,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Metrics row inside trip card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(PanelElevatedGray)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TripSubMetric(
                label = "DURASI",
                value = TelemetryFormatter.formatDurationReadable(trip.durationSeconds)
            )
            TripSubMetric(
                label = "RATA-RATA",
                value = "${TelemetryFormatter.formatSpeedOneDecimal(trip.avgSpeedKmh, speedUnit.multiplier)} ${speedUnit.symbol}"
            )
            TripSubMetric(
                label = "MAKSIMAL",
                value = "${TelemetryFormatter.formatSpeedOneDecimal(trip.maxSpeedKmh, speedUnit.multiplier)} ${speedUnit.symbol}"
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = "Lihat Peta Rute",
                    tint = NeonGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "PETA",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonGreen
                )
            }
        }
    }
}

@Composable
private fun TripSubMetric(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = TextSecondarySilver
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimaryWhite
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
    val indoLocale = remember { Locale("id", "ID") }
    val dateFmt = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", indoLocale) }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", indoLocale) }
    val routePoints = remember(trip.routePointsJson) {
        RouteJsonCodec.decodeRoutePoints(trip.routePointsJson)
    }

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
                            modifier = Modifier.testTag("btn_back_from_trip_detail")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali ke Riwayat",
                                tint = NeonGreen
                            )
                        }
                        Column {
                            Text(
                                text = "DETAIL PERJALANAN",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = NeonGreen
                            )
                            Text(
                                text = dateFmt.format(Date(trip.startTimeMillis)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondarySilver
                            )
                        }
                    }

                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Hapus Perjalanan Ini",
                            tint = CrimsonRed
                        )
                    }
                }
            }

            // Interactive Vector Route Map Canvas
            item {
                VectorRouteMapCanvas(
                    routePoints = routePoints,
                    startLabel = trip.startCoordLabel,
                    endLabel = trip.endCoordLabel
                )
            }

            // Detailed Telemetry Summary Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(PanelDarkGray)
                        .border(1.dp, PanelBorderGray, RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "RINGKASAN TELEMETRI PERJALANAN",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberCyan
                    )

                    DetailRow(
                        label = "Total Jarak Tempuh",
                        value = "${TelemetryFormatter.formatOdometerHighPrecision(trip.distanceKm, speedUnit.multiplier)} ${speedUnit.distanceSymbol}",
                        highlight = NeonGreen
                    )
                    DetailRow(
                        label = "Waktu Mulai",
                        value = "${timeFmt.format(Date(trip.startTimeMillis))} WIB"
                    )
                    DetailRow(
                        label = "Waktu Selesai",
                        value = "${timeFmt.format(Date(trip.endTimeMillis))} WIB"
                    )
                    DetailRow(
                        label = "Durasi Berkendara",
                        value = TelemetryFormatter.formatDurationHms(trip.durationSeconds)
                    )
                    DetailRow(
                        label = "Kecepatan Rata-Rata",
                        value = "${TelemetryFormatter.formatSpeedOneDecimal(trip.avgSpeedKmh, speedUnit.multiplier)} ${speedUnit.symbol}"
                    )
                    DetailRow(
                        label = "Kecepatan Maksimal",
                        value = "${TelemetryFormatter.formatSpeedOneDecimal(trip.maxSpeedKmh, speedUnit.multiplier)} ${speedUnit.symbol}",
                        highlight = AmberWarning
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

@Composable
private fun DetailRow(
    label: String,
    value: String,
    highlight: Color = TextPrimaryWhite
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondarySilver
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = highlight
        )
    }
}
