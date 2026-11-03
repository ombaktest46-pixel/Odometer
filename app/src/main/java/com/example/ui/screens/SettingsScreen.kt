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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GpsAccuracyFilter
import com.example.model.PowerMode
import com.example.model.SensitivityLevel
import com.example.model.SettingsState
import com.example.model.SpeedUnit
import com.example.model.TelemetryFormatter
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
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settingsState: SettingsState,
    totalOdometerKm: Double,
    tripOdometerKm: Double,
    onToggleAutoTracking: (Boolean) -> Unit,
    onSensitivitySelected: (SensitivityLevel) -> Unit,
    onAccuracyFilterSelected: (GpsAccuracyFilter) -> Unit,
    onPowerModeSelected: (PowerMode) -> Unit,
    onSpeedUnitSelected: (SpeedUnit) -> Unit,
    onCalibrationChanged: (Float) -> Unit,
    onProtectedTotalOdometerAdjust: (Double) -> Unit,
    onResetTrip: () -> Unit,
    onNotificationsEnabledChanged: (Boolean) -> Unit,
    onExportBackupClick: () -> Unit,
    onImportBackupClick: () -> Unit,
    onOpenBatterySettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderCalibration by remember(settingsState.calibrationFactor) {
        mutableFloatStateOf(settingsState.calibrationFactor)
    }
    var showProtectedOdometerDialog by remember { mutableStateOf(false) }
    var showResetTripDialog by remember { mutableStateOf(false) }

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
                        text = "PENGATURAN SISTEM",
                        fontFamily = OrbitronFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = NeonGreen
                    )
                    Text(
                        text = "Konfigurasi Auto Tracking, akurasi GPS, kalibrasi odometer, dan backup data",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondarySilver
                    )
                }
            }

            // 1. Auto Tracking & Live Notification Switch Card
            item {
                SettingsSectionCard(
                    icon = Icons.Default.Notifications,
                    title = "AUTO TRACKING & NOTIFIKASI LIVE"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto Tracking Otomatis",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimaryWhite
                            )
                            Text(
                                text = "Otomatis mendeteksi motor berjalan & berhenti tanpa tombol START",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 12.sp,
                                color = TextSecondarySilver
                            )
                        }
                        Switch(
                            checked = settingsState.isAutoTrackingEnabled,
                            onCheckedChange = onToggleAutoTracking,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CarbonBlack,
                                checkedTrackColor = NeonGreen
                            ),
                            modifier = Modifier.testTag("switch_auto_tracking")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pembaruan Notifikasi Real-Time",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimaryWhite
                            )
                            Text(
                                text = "Tampilkan Total Odometer, Trip, & Kecepatan saat layar mati/terkunci",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 12.sp,
                                color = TextSecondarySilver
                            )
                        }
                        Switch(
                            checked = settingsState.notificationsEnabled,
                            onCheckedChange = onNotificationsEnabledChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CarbonBlack,
                                checkedTrackColor = CyberCyan
                            ),
                            modifier = Modifier.testTag("switch_notifications")
                        )
                    }
                }
            }

            // 2. Motion Detection Sensitivity
            item {
                SettingsSectionCard(
                    icon = Icons.Default.Tune,
                    title = "SENSITIVITAS DETEKSI GERAKAN MOTOR"
                ) {
                    SensitivityLevel.entries.forEach { level ->
                        val selected = settingsState.sensitivityLevel == level
                        SelectableOptionRow(
                            title = level.title,
                            description = level.description,
                            selected = selected,
                            accentColor = NeonGreen,
                            onClick = { onSensitivitySelected(level) },
                            testTag = "sensitivity_option_${level.name}"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // 3. GPS Accuracy Filter
            item {
                SettingsSectionCard(
                    icon = Icons.Default.GpsFixed,
                    title = "FILTER AKURASI SINYAL GPS (ANTI-DRIFT)"
                ) {
                    GpsAccuracyFilter.entries.forEach { filter ->
                        val selected = settingsState.gpsAccuracyFilter == filter
                        SelectableOptionRow(
                            title = filter.title,
                            description = filter.description,
                            selected = selected,
                            accentColor = CyberCyan,
                            onClick = { onAccuracyFilterSelected(filter) },
                            testTag = "accuracy_option_${filter.name}"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // 4. Speed Unit & Odometer Calibration
            item {
                SettingsSectionCard(
                    icon = Icons.Default.Speed,
                    title = "SATUAN & KALIBRASI ODOMETER"
                ) {
                    Text(
                        text = "Satuan Kecepatan & Jarak",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondarySilver
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SpeedUnit.entries.forEach { unit ->
                            val isSelected = settingsState.speedUnit == unit
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) NeonGreen.copy(alpha = 0.18f) else PanelElevatedGray
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) NeonGreen else PanelBorderGray,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onSpeedUnitSelected(unit) }
                                    .testTag("unit_option_${unit.name}")
                            ) {
                                Text(
                                    text = "${unit.symbol} (${unit.distanceSymbol})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) NeonGreen else TextPrimaryWhite
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val pct = (sliderCalibration * 100).roundToInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Faktor Kalibrasi Odometer GPS",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimaryWhite
                            )
                            Text(
                                text = "Sesuaikan pengukuran GPS dengan speedometer bawaan motor",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 11.sp,
                                color = TextSecondarySilver
                            )
                        }
                        Text(
                            text = "$pct%",
                            fontFamily = OrbitronFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = CyberCyan
                        )
                    }

                    Slider(
                        value = sliderCalibration,
                        onValueChange = { sliderCalibration = it },
                        onValueChangeFinished = { onCalibrationChanged(sliderCalibration) },
                        valueRange = 0.85f..1.15f,
                        steps = 29,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonGreen,
                            activeTrackColor = NeonGreen
                        ),
                        modifier = Modifier.testTag("slider_calibration")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                sliderCalibration = 1.00f
                                onCalibrationChanged(1.00f)
                            }
                        ) {
                            Text("Reset 100% (Standar GPS)", color = CyberCyan)
                        }
                        Text(
                            text = "Rentang: 85% – 115%",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondarySilver,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }

            // 5. Odometer Protection & Trip Reset
            item {
                SettingsSectionCard(
                    icon = Icons.Default.Security,
                    title = "PROTEKSI TOTAL ODOMETER & RESET TRIP"
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PanelElevatedGray)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Proteksi Odometer",
                                    tint = NeonGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Total Odometer Permanen",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NeonGreen
                                )
                            }
                            Text(
                                text = "${TelemetryFormatter.formatOdometer(totalOdometerKm, settingsState.speedUnit.multiplier)} ${settingsState.speedUnit.distanceSymbol} (Dilindungi dari penghapusan tidak sengaja)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 12.sp,
                                color = TextPrimaryWhite
                            )
                        }
                        OutlinedButton(
                            onClick = { showProtectedOdometerDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("btn_open_calibrate_total_odo")
                        ) {
                            Text("Sesuaikan", color = NeonGreen)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showResetTripDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_settings_reset_trip")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset Trip Odometer",
                            tint = CyberCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RESET TRIP ODOMETER (${TelemetryFormatter.formatOdometer(tripOdometerKm, settingsState.speedUnit.multiplier)} ${settingsState.speedUnit.distanceSymbol})",
                            color = CyberCyan
                        )
                    }
                }
            }

            // 6. Battery Saver & Android Optimization Guide
            item {
                SettingsSectionCard(
                    icon = Icons.Default.BatteryChargingFull,
                    title = "PENGATURAN HEMAT BATERAI & BACKGROUND"
                ) {
                    PowerMode.entries.forEach { mode ->
                        val selected = settingsState.powerMode == mode
                        SelectableOptionRow(
                            title = mode.title,
                            description = mode.description,
                            selected = selected,
                            accentColor = AmberWarning,
                            onClick = { onPowerModeSelected(mode) },
                            testTag = "power_mode_${mode.name}"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Catatan Penting Produsen HP: Beberapa sistem Android dapat menghentikan layanan lokasi latar belakang jika penghemat baterai ketat aktif. Nonaktifkan pembatasan baterai untuk MOTO ODO agar pencatatan tidak terputus.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 12.sp,
                        color = TextSecondarySilver
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenBatterySettingsClick,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_open_battery_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = "Pengaturan Optimasi Baterai",
                            tint = AmberWarning
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buka Pengaturan Optimasi Baterai HP", color = AmberWarning)
                    }
                }
            }

            // 7. Backup & Restore Data (JSON)
            item {
                SettingsSectionCard(
                    icon = Icons.Default.CloudUpload,
                    title = "BACKUP & RESTORE DATA (JSON)"
                ) {
                    Text(
                        text = "Simpan cadangan Total Odometer Permanen, pengaturan kalibrasi, dan seluruh riwayat perjalanan ke file JSON, atau pulihkan dari file cadangan.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 12.sp,
                        color = TextSecondarySilver
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onExportBackupClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonGreen,
                                contentColor = CarbonBlack
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_export_backup")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Backup Data JSON",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("BACKUP JSON", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onImportBackupClick,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_import_backup")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Restore Data JSON",
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RESTORE JSON", color = CyberCyan)
                        }
                    }
                }
            }
        }
    }

    // Protected Total Odometer Calibration Dialog (Requires typing "SETUJU" to prevent accidental wipe)
    if (showProtectedOdometerDialog) {
        var inputKmText by remember {
            mutableStateOf(String.format("%.1f", totalOdometerKm).replace(',', '.'))
        }
        var confirmKeyword by remember { mutableStateOf("") }
        val isUnlocked = confirmKeyword.trim().equals("SETUJU", ignoreCase = true)

        AlertDialog(
            onDismissRequest = { showProtectedOdometerDialog = false },
            containerColor = PanelDarkGray,
            titleContentColor = TextPrimaryWhite,
            textContentColor = TextSecondarySilver,
            title = {
                Text("Kalibrasi Total Odometer Permanen")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Fitur ini memungkinkan Anda menyamakan angka Total Odometer aplikasi dengan angka pada odometer fisik motor Anda. Untuk mencegah perubahan tidak sengaja, ketik kata SETUJU di bawah ini.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = inputKmText,
                        onValueChange = { inputKmText = it },
                        label = { Text("Total Odometer Baru (KM)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_protected_total_odo")
                    )
                    OutlinedTextField(
                        value = confirmKeyword,
                        onValueChange = { confirmKeyword = it },
                        label = { Text("Ketik SETUJU untuk membuka kunci") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_confirm_keyword")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = inputKmText.replace(',', '.').toDoubleOrNull()
                        if (parsed != null && parsed >= 0.0) {
                            onProtectedTotalOdometerAdjust(parsed)
                            showProtectedOdometerDialog = false
                        }
                    },
                    enabled = isUnlocked,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = CarbonBlack
                    ),
                    modifier = Modifier.testTag("btn_confirm_protected_odo")
                ) {
                    Text("Simpan Odometer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProtectedOdometerDialog = false }) {
                    Text("Batal", color = TextSecondarySilver)
                }
            }
        )
    }

    if (showResetTripDialog) {
        AlertDialog(
            onDismissRequest = { showResetTripDialog = false },
            containerColor = PanelDarkGray,
            titleContentColor = TextPrimaryWhite,
            textContentColor = TextSecondarySilver,
            title = { Text("Reset Trip Odometer?") },
            text = {
                Text("Hanya Trip Odometer yang akan di-reset ke 0,0. Total Odometer Permanen tetap tersimpan.")
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
                    )
                ) {
                    Text("Reset Trip")
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
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PanelDarkGray)
            .border(1.dp, PanelBorderGray, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = NeonGreen,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = NeonGreen
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun SelectableOptionRow(
    title: String,
    description: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accentColor.copy(alpha = 0.14f) else PanelElevatedGray)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accentColor else PanelBorderGray,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(12.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) accentColor else TextPrimaryWhite
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 12.sp,
                color = TextSecondarySilver
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) accentColor else Color.Transparent)
                .border(2.dp, if (selected) accentColor else TextSecondarySilver, RoundedCornerShape(50))
        )
    }
}
