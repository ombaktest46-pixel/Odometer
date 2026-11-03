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
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
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
import com.example.model.ThemeMode
import com.example.ui.components.MotoCard
import com.example.ui.theme.InterFontFamily
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settingsState: SettingsState,
    totalOdometerKm: Double,
    tripOdometerKm: Double,
    onToggleAutoTracking: (Boolean) -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
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
    val cs = MaterialTheme.colorScheme
    var sliderCalibration by remember(settingsState.calibrationFactor) {
        mutableFloatStateOf(settingsState.calibrationFactor)
    }
    var showProtectedOdometerDialog by remember { mutableStateOf(false) }
    var showResetTripDialog by remember { mutableStateOf(false) }

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
            item {
                Column {
                    Text(
                        text = "Pengaturan",
                        style = MaterialTheme.typography.headlineMedium,
                        color = cs.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Konfigurasi tampilan, pelacakan GPS, odometer, dan sistem",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            // 1. APPEARANCE: Light mode, Dark mode, System default
            item {
                SettingsGroupCard(
                    icon = Icons.Outlined.Palette,
                    sectionTitle = "Tampilan (Appearance)"
                ) {
                    Text(
                        text = "Pilih tema visual aplikasi yang nyaman untuk berkendara siang maupun malam.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            val isSelected = settingsState.themeMode == mode
                            val icon = when (mode) {
                                ThemeMode.SYSTEM -> Icons.Outlined.SettingsBrightness
                                ThemeMode.LIGHT -> Icons.Outlined.LightMode
                                ThemeMode.DARK -> Icons.Outlined.DarkMode
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) cs.secondary.copy(alpha = 0.12f) else cs.surfaceVariant
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) cs.secondary else cs.outline,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onThemeModeSelected(mode) }
                                    .testTag("theme_option_${mode.name}")
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = mode.label,
                                    tint = if (isSelected) cs.secondary else cs.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSelected) cs.secondary else cs.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 2. GENERAL: Application preferences & Measurement settings
            item {
                SettingsGroupCard(
                    icon = Icons.Outlined.Speed,
                    sectionTitle = "Umum & Pengukuran (General)"
                ) {
                    Text(
                        text = "Satuan Kecepatan & Jarak",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant
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
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) cs.secondary.copy(alpha = 0.12f) else cs.surfaceVariant
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) cs.secondary else cs.outline,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onSpeedUnitSelected(unit) }
                                    .testTag("unit_option_${unit.name}")
                            ) {
                                Text(
                                    text = "${unit.symbol.uppercase()} (${unit.distanceSymbol.uppercase()})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSelected) cs.secondary else cs.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(thickness = 1.dp, color = cs.outline)
                    Spacer(modifier = Modifier.height(14.dp))

                    val pct = (sliderCalibration * 100).roundToInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Kalibrasi Pengukuran GPS",
                                style = MaterialTheme.typography.titleSmall,
                                color = cs.onSurface
                            )
                            Text(
                                text = "Sesuaikan pembacaan jarak dengan speedometer motor",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "$pct%",
                            style = TextStyle(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                fontFeatureSettings = "tnum"
                            ),
                            color = cs.secondary
                        )
                    }

                    Slider(
                        value = sliderCalibration,
                        onValueChange = { sliderCalibration = it },
                        onValueChangeFinished = { onCalibrationChanged(sliderCalibration) },
                        valueRange = 0.85f..1.15f,
                        steps = 29,
                        colors = SliderDefaults.colors(
                            thumbColor = cs.secondary,
                            activeTrackColor = cs.secondary
                        ),
                        modifier = Modifier.testTag("slider_calibration")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                sliderCalibration = 1.00f
                                onCalibrationChanged(1.00f)
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("Reset Standar (100%)", color = cs.secondary, fontSize = 12.sp)
                        }
                        Text(
                            text = "Rentang: 85% – 115%",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. TRACKING: Automatic tracking, GPS settings, Background tracking, Location permissions
            item {
                SettingsGroupCard(
                    icon = Icons.Outlined.GpsFixed,
                    sectionTitle = "Pelacakan & GPS (Tracking)"
                ) {
                    // Auto Tracking Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pelacakan Otomatis (Auto Tracking)",
                                style = MaterialTheme.typography.titleSmall,
                                color = cs.onSurface
                            )
                            Text(
                                text = "Deteksi pergerakan motor dan catat jarak secara otomatis via GPS",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settingsState.isAutoTrackingEnabled,
                            onCheckedChange = onToggleAutoTracking,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = cs.primary
                            ),
                            modifier = Modifier.testTag("switch_auto_tracking")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(thickness = 1.dp, color = cs.outline)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Sensitivity
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = cs.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sensitivitas Deteksi Gerakan",
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SensitivityLevel.entries.forEach { level ->
                        val selected = settingsState.sensitivityLevel == level
                        SelectableOptionRow(
                            title = level.title,
                            description = level.description,
                            selected = selected,
                            accentColor = cs.primary,
                            onClick = { onSensitivitySelected(level) },
                            testTag = "sensitivity_option_${level.name}"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(thickness = 1.dp, color = cs.outline)
                    Spacer(modifier = Modifier.height(14.dp))

                    // GPS Accuracy Filter
                    Text(
                        text = "Filter Akurasi Sinyal GPS",
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    GpsAccuracyFilter.entries.forEach { filter ->
                        val selected = settingsState.gpsAccuracyFilter == filter
                        SelectableOptionRow(
                            title = filter.title,
                            description = filter.description,
                            selected = selected,
                            accentColor = cs.secondary,
                            onClick = { onAccuracyFilterSelected(filter) },
                            testTag = "accuracy_option_${filter.name}"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(thickness = 1.dp, color = cs.outline)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Background Power Mode & Battery Settings
                    Text(
                        text = "Mode Daya & Pelacakan Latar Belakang",
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PowerMode.entries.forEach { mode ->
                        val selected = settingsState.powerMode == mode
                        SelectableOptionRow(
                            title = mode.title,
                            description = mode.description,
                            selected = selected,
                            accentColor = cs.secondary,
                            onClick = { onPowerModeSelected(mode) },
                            testTag = "power_mode_${mode.name}"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onOpenBatterySettingsClick,
                        shape = RoundedCornerShape(10.dp),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = SolidColor(cs.outline)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_open_battery_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BatteryChargingFull,
                            contentDescription = "Pengaturan Optimasi Baterai",
                            tint = cs.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pengaturan Izin Baterai Latar Belakang",
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.onSurface
                        )
                    }
                }
            }

            // 4. ODOMETER: Permanent odometer information & Trip odometer settings
            item {
                SettingsGroupCard(
                    icon = Icons.Outlined.Lock,
                    sectionTitle = "Odometer"
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(cs.surfaceVariant)
                            .border(1.dp, cs.outline, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = "Proteksi Odometer",
                                    tint = cs.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Odometer Permanen",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = cs.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${TelemetryFormatter.formatOdometer(totalOdometerKm, settingsState.speedUnit.multiplier)} ${settingsState.speedUnit.distanceSymbol} • Terkunci dari reset",
                                style = TextStyle(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = cs.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { showProtectedOdometerDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                brush = SolidColor(cs.outline)
                            ),
                            modifier = Modifier.testTag("btn_open_calibrate_total_odo")
                        ) {
                            Text("Sesuaikan", color = cs.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showResetTripDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = SolidColor(cs.outline)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_settings_reset_trip")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RestartAlt,
                            contentDescription = "Reset Trip Odometer",
                            tint = cs.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reset Trip Odometer (${TelemetryFormatter.formatOdometer(tripOdometerKm, settingsState.speedUnit.multiplier)} ${settingsState.speedUnit.distanceSymbol})",
                            style = TextStyle(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                fontFeatureSettings = "tnum"
                            ),
                            color = cs.secondary
                        )
                    }
                }
            }

            // 5. SYSTEM: Notifications, Backup & Restore, About application, Application version
            item {
                SettingsGroupCard(
                    icon = Icons.Outlined.Info,
                    sectionTitle = "Sistem & Tentang Aplikasi (System)"
                ) {
                    // Notifications switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notifikasi Latar Belakang",
                                style = MaterialTheme.typography.titleSmall,
                                color = cs.onSurface
                            )
                            Text(
                                text = "Tampilkan panel kecepatan & odometer saat layar terkunci",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settingsState.notificationsEnabled,
                            onCheckedChange = onNotificationsEnabledChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = cs.secondary
                            ),
                            modifier = Modifier.testTag("switch_notifications")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(thickness = 1.dp, color = cs.outline)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Backup & Restore JSON
                    Text(
                        text = "Cadangan & Pemulihan Data (JSON)",
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ekspor atau pulihkan Total Odometer Permanen beserta seluruh riwayat perjalanan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onExportBackupClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = cs.primary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("btn_export_backup")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudUpload,
                                contentDescription = "Backup Data JSON",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Backup JSON", fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = onImportBackupClick,
                            shape = RoundedCornerShape(10.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                brush = SolidColor(cs.outline)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("btn_import_backup")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudDownload,
                                contentDescription = "Restore Data JSON",
                                tint = cs.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore JSON", color = cs.secondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(thickness = 1.dp, color = cs.outline)
                    Spacer(modifier = Modifier.height(12.dp))

                    // About & Version Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "MOTO ODO — Automatic GPS Odometer",
                                style = MaterialTheme.typography.titleSmall,
                                color = cs.onSurface
                            )
                            Text(
                                text = "Pencatat odometer permanen & telemetri GPS presisi tinggi",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "v1.0.0",
                            style = TextStyle(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                fontFeatureSettings = "tnum"
                            ),
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showProtectedOdometerDialog) {
        var inputKmText by remember {
            mutableStateOf(String.format("%.1f", totalOdometerKm).replace(',', '.'))
        }
        var confirmKeyword by remember { mutableStateOf("") }
        val isUnlocked = confirmKeyword.trim().equals("SETUJU", ignoreCase = true)

        AlertDialog(
            onDismissRequest = { showProtectedOdometerDialog = false },
            containerColor = cs.surface,
            titleContentColor = cs.onSurface,
            textContentColor = cs.onSurfaceVariant,
            title = {
                Text("Kalibrasi Total Odometer Permanen")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Samakan angka Total Odometer aplikasi dengan odometer fisik motor Anda. Untuk mencegah perubahan tidak sengaja, ketik kata SETUJU di bawah ini.",
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
                        containerColor = cs.primary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("btn_confirm_protected_odo")
                ) {
                    Text("Simpan Odometer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProtectedOdometerDialog = false }) {
                    Text("Batal", color = cs.onSurfaceVariant)
                }
            }
        )
    }

    if (showResetTripDialog) {
        AlertDialog(
            onDismissRequest = { showResetTripDialog = false },
            containerColor = cs.surface,
            titleContentColor = cs.onSurface,
            textContentColor = cs.onSurfaceVariant,
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
                        containerColor = cs.secondary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Reset Trip")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetTripDialog = false }) {
                    Text("Batal", color = cs.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun SettingsGroupCard(
    icon: ImageVector,
    sectionTitle: String,
    content: @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    MotoCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = sectionTitle,
                    tint = cs.onSurface,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
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
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accentColor.copy(alpha = 0.10f) else cs.surfaceVariant)
            .border(
                width = 1.dp,
                color = if (selected) accentColor.copy(alpha = 0.55f) else cs.outline,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) accentColor else cs.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) accentColor else Color.Transparent)
                .border(1.5.dp, if (selected) accentColor else cs.onSurfaceVariant, RoundedCornerShape(50))
        )
    }
}
