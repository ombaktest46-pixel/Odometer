package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LiveTelemetryState
import com.example.model.MotionStatus
import com.example.model.SettingsState
import com.example.model.TelemetryFormatter
import com.example.ui.components.DigitalSpeedometerGauge
import com.example.ui.components.PermanentOdometerDisplayCard
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

@Composable
fun DashboardScreen(
    telemetryState: LiveTelemetryState,
    settingsState: SettingsState,
    hasLocationPermission: Boolean,
    onRequestPermissionsAndStart: () -> Unit,
    onStopAutoTracking: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onResetTrip: () -> Unit,
    onOpenHistoryTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showResetTripDialog by remember { mutableStateOf(false) }
    val unit = settingsState.speedUnit

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
            // 1. Top Cockpit Header Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "MOTO ODO",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = NeonGreen
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (telemetryState.isAutoTrackingEnabled) {
                                            NeonGreen.copy(alpha = 0.18f)
                                        } else {
                                            PanelElevatedGray
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        if (telemetryState.isAutoTrackingEnabled) NeonGreen else PanelBorderGray,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (telemetryState.isAutoTrackingEnabled) "AUTO ON" else "STANDBY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (telemetryState.isAutoTrackingEnabled) NeonGreen else TextSecondarySilver
                                )
                            }
                        }
                        Text(
                            text = telemetryState.statusDetailMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 12.sp,
                            color = TextSecondarySilver
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Hardware GPS Status Pill (Opens System Location Settings when tapped)
                    val gpsOn = telemetryState.isGpsHardwareEnabled
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (gpsOn) NeonGreen.copy(alpha = 0.15f) else CrimsonRed.copy(alpha = 0.18f)
                            )
                            .border(
                                1.dp,
                                if (gpsOn) NeonGreen else CrimsonRed,
                                RoundedCornerShape(50)
                            )
                            .clickable { onOpenLocationSettings() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("btn_gps_hardware_status")
                    ) {
                        Icon(
                            imageVector = if (gpsOn) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                            contentDescription = "Status Sensor GPS HP",
                            tint = if (gpsOn) NeonGreen else CrimsonRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (gpsOn) "GPS AKTIF" else "GPS MATI",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (gpsOn) NeonGreen else CrimsonRed
                        )
                    }
                }
            }

            // 2A. GPS Hardware Disabled Alert Banner
            if (!telemetryState.isGpsHardwareEnabled) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CrimsonRed.copy(alpha = 0.14f))
                            .border(1.dp, CrimsonRed.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                            .clickable { onOpenLocationSettings() }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsOff,
                            contentDescription = "GPS HP Sedang Mati",
                            tint = CrimsonRed,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sensor Lokasi GPS HP Sedang Mati",
                                style = MaterialTheme.typography.titleMedium,
                                color = CrimsonRed
                            )
                            Text(
                                text = "Speedometer & Odometer dikunci pada 0 KM/JAM. Ketuk di sini untuk menyalakan GPS perangkat.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 12.sp,
                                color = TextPrimaryWhite
                            )
                        }
                    }
                }
            }

            // 2B. Permission Alert Banner if needed
            if (!hasLocationPermission) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AmberWarning.copy(alpha = 0.14f))
                            .border(1.dp, AmberWarning.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                            .clickable { onRequestPermissionsAndStart() }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Peringatan Izin Lokasi",
                            tint = AmberWarning,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Izin Lokasi GPS Diperlukan",
                                style = MaterialTheme.typography.titleMedium,
                                color = AmberWarning
                            )
                            Text(
                                text = "Ketuk untuk memberikan izin lokasi agar Auto Tracking dapat mendeteksi motor berjalan otomatis.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 12.sp,
                                color = TextPrimaryWhite
                            )
                        }
                    }
                }
            }

            // 3. Large Digital Speedometer Cluster
            item {
                DigitalSpeedometerGauge(
                    speedKmh = telemetryState.currentSpeedKmh,
                    motionStatus = telemetryState.motionStatus,
                    gpsSignalQuality = telemetryState.gpsSignalQuality,
                    gpsAccuracyMeters = telemetryState.gpsAccuracyMeters,
                    speedUnit = unit,
                    isSimulationActive = false
                )
            }

            // 4. Permanent Total Odometer & Trip Odometer Drum Card
            item {
                PermanentOdometerDisplayCard(
                    totalOdometerKm = telemetryState.totalOdometerKm,
                    tripOdometerKm = telemetryState.tripOdometerKm,
                    speedUnit = unit
                )
            }

            // 5. Three-Column Live Telemetry Metrics (Duration, Avg Speed, Max Speed)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TelemetryMetricCard(
                        icon = Icons.Default.Timer,
                        label = "DURASI TRIP",
                        value = TelemetryFormatter.formatDurationHms(telemetryState.tripDurationSeconds),
                        unitLabel = "JAM:MNT:DTK",
                        accentColor = NeonGreen,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryMetricCard(
                        icon = Icons.Default.Speed,
                        label = "RATA-RATA",
                        value = TelemetryFormatter.formatSpeedOneDecimal(telemetryState.avgSpeedKmh, unit.multiplier),
                        unitLabel = unit.symbol,
                        accentColor = CyberCyan,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryMetricCard(
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        label = "KEC. MAKS",
                        value = TelemetryFormatter.formatSpeedOneDecimal(telemetryState.maxSpeedKmh, unit.multiplier),
                        unitLabel = unit.symbol,
                        accentColor = AmberWarning,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 6. Live Background Notification Mirror Banner
            if (telemetryState.isAutoTrackingEnabled) {
                item {
                    LiveNotificationStatusCard(
                        telemetryState = telemetryState,
                        speedUnit = unit
                    )
                }
            }

            // 7. Primary Action Buttons
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!telemetryState.isAutoTrackingEnabled) {
                        Button(
                            onClick = onRequestPermissionsAndStart,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonGreen,
                                contentColor = CarbonBlack
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("btn_enable_auto_tracking")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Aktifkan Auto Tracking"
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "AKTIFKAN AUTO TRACKING",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        Button(
                            onClick = onStopAutoTracking,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrimsonRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("btn_disable_auto_tracking")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Matikan Auto Tracking"
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "MATIKAN AUTO TRACKING",
                                fontFamily = OrbitronFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showResetTripDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = CyberCyan
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("btn_reset_trip")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset Trip",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "RESET TRIP",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenHistoryTab,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = NeonGreen
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("btn_trip_history")
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Riwayat Perjalanan",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "RIWAYAT PERJALANAN",
                                style = MaterialTheme.typography.labelLarge,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (showResetTripDialog) {
        AlertDialog(
            onDismissRequest = { showResetTripDialog = false },
            containerColor = PanelDarkGray,
            titleContentColor = TextPrimaryWhite,
            textContentColor = TextSecondarySilver,
            title = {
                Text(
                    text = "Reset Trip Odometer?",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Angka TRIP ODOMETER akan dikembalikan ke 0,0 ${unit.distanceSymbol}. " +
                        "TOTAL ODOMETER PERMANEN (${TelemetryFormatter.formatOdometer(telemetryState.totalOdometerKm, unit.multiplier)} ${unit.distanceSymbol}) dijamin tetap aman dan tidak akan berubah."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetTripDialog = false
                        onResetTrip()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberCyan,
                        contentColor = CarbonBlack
                    ),
                    modifier = Modifier.testTag("confirm_reset_trip_button")
                ) {
                    Text("Ya, Reset Trip")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetTripDialog = false }) {
                    Text("Batal", color = TextSecondarySilver)
                }
            }
        )
    }
}

@Composable
private fun TelemetryMetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    unitLabel: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PanelDarkGray)
            .border(1.dp, PanelBorderGray, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = TextSecondarySilver
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            fontFamily = OrbitronFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            color = TextPrimaryWhite
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = unitLabel,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            maxLines = 1,
            color = accentColor
        )
    }
}

@Composable
private fun LiveNotificationStatusCard(
    telemetryState: LiveTelemetryState,
    speedUnit: com.example.model.SpeedUnit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PanelElevatedGray)
            .border(1.dp, NeonGreen.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(12.dp)
            .testTag("live_notification_banner"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(NeonGreen.copy(alpha = 0.16f))
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = "Notifikasi Background Aktif",
                tint = NeonGreen,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "NOTIFIKASI BACKGROUND LIVE AKTIF (MOTO ODO \uD83C\uDFCD\uFE0F)",
                style = MaterialTheme.typography.labelMedium,
                color = NeonGreen
            )
            val statusStr = when {
                !telemetryState.isGpsHardwareEnabled -> "● GPS MATI"
                telemetryState.motionStatus == MotionStatus.MOVING -> "● MOTOR BERJALAN"
                else -> "● MOTOR BERHENTI"
            }
            Text(
                text = "ODO: ${TelemetryFormatter.formatOdometer(telemetryState.totalOdometerKm, speedUnit.multiplier)} ${speedUnit.distanceSymbol} • " +
                    "TRIP: ${TelemetryFormatter.formatOdometer(telemetryState.tripOdometerKm, speedUnit.multiplier)} ${speedUnit.distanceSymbol} • " +
                    "${TelemetryFormatter.formatSpeedInt(telemetryState.currentSpeedKmh, speedUnit.multiplier)} ${speedUnit.symbol} ($statusStr)",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 12.sp,
                color = TextPrimaryWhite
            )
        }
    }
}
