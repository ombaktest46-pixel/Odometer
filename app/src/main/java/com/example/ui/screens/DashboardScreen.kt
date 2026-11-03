package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GpsOff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.LiveTelemetryState
import com.example.model.MotionStatus
import com.example.model.SettingsState
import com.example.model.TelemetryFormatter
import com.example.model.WidgetThemeStyle
import com.example.ui.components.DigitalSpeedometerGauge
import com.example.ui.components.MotoCard
import com.example.ui.components.PermanentOdometerDisplayCard
import com.example.ui.components.SquareCircularOdometerWidgetPreview

@Composable
fun DashboardScreen(
    telemetryState: LiveTelemetryState,
    settingsState: SettingsState,
    hasLocationPermission: Boolean,
    onRequestPermissionsAndStart: () -> Unit,
    onStopAutoTracking: () -> Unit,
    onOpenLocationSettings: () -> Unit = {},
    onResetTrip: () -> Unit,
    onOpenHistoryTab: () -> Unit,
    onPinWidgetClick: () -> Unit = {},
    onToggleThemeMode: () -> Unit = {},
    onBikeNameChanged: (String) -> Unit = {},
    onWidgetThemeSelected: (WidgetThemeStyle) -> Unit = {},
    onQuickCalibrateOdometer: (Double) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val isLight = cs.background.luminance() > 0.5f

    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showEditBikeNameDialog by remember { mutableStateOf(false) }
    var showQuickCalibrateDialog by remember { mutableStateOf(false) }
    var showFullWidgetGallerySheet by remember { mutableStateOf(false) }

    val isTrackingActive = telemetryState.isAutoTrackingEnabled || settingsState.isAutoTrackingEnabled
    val speedUnit = settingsState.speedUnit

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
            // 1. HEADER: MOTO ODO Branding, Clean GPS & Tracking Status Badge, Theme Toggle
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
                                style = MaterialTheme.typography.headlineMedium,
                                color = cs.onBackground
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            // Tracking Status Badge (GREEN when active, Neutral when off)
                            val badgeColor = if (isTrackingActive) cs.primary else cs.onSurfaceVariant
                            val badgeBg = if (isTrackingActive) {
                                cs.primary.copy(alpha = 0.12f)
                            } else {
                                cs.surfaceVariant
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(badgeBg)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(badgeColor)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isTrackingActive) "Auto Aktif" else "Siaga",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showEditBikeNameDialog = true }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "${settingsState.bikeName} • ${telemetryState.statusDetailMessage}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Ubah Nama Motor",
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { showFullWidgetGallerySheet = true },
                            shape = RoundedCornerShape(10.dp),
                            color = cs.surface,
                            border = BorderStroke(1.dp, cs.outline),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Widgets,
                                    contentDescription = "Widget Odometer",
                                    tint = cs.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Widget",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = cs.onSurface
                                )
                            }
                        }

                        Surface(
                            onClick = onToggleThemeMode,
                            shape = RoundedCornerShape(10.dp),
                            color = cs.surface,
                            border = BorderStroke(1.dp, cs.outline),
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("btn_toggle_theme_mode")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isLight) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                                    contentDescription = "Ganti Tema Terang / Gelap",
                                    tint = cs.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. RED WARNING ALERT (Only when GPS is off or Location permission missing)
            if (!hasLocationPermission) {
                item {
                    WarningStatusCard(
                        icon = Icons.Outlined.LocationOn,
                        title = "Izin Lokasi GPS Diperlukan",
                        description = "Berikan izin lokasi agar odometer otomatis mendeteksi saat motor berjalan.",
                        actionLabel = "Izinkan",
                        onActionClick = onRequestPermissionsAndStart,
                        testTag = "btn_grant_location"
                    )
                }
            } else if (!telemetryState.isGpsHardwareEnabled) {
                item {
                    WarningStatusCard(
                        icon = Icons.Outlined.GpsOff,
                        title = "GPS Perangkat Nonaktif",
                        description = "Aktifkan layanan lokasi HP untuk menghitung kecepatan dan jarak.",
                        actionLabel = "Aktifkan GPS",
                        onActionClick = onOpenLocationSettings,
                        testTag = "btn_enable_gps_hardware"
                    )
                }
            }

            // 3. SPEED CARD
            item {
                DigitalSpeedometerGauge(
                    speedKmh = telemetryState.currentSpeedKmh,
                    speedUnit = speedUnit,
                    motionStatus = telemetryState.motionStatus,
                    gpsSignalQuality = telemetryState.gpsSignalQuality,
                    gpsAccuracyMeters = telemetryState.gpsAccuracyMeters
                )
            }

            // 4. PERMANENT ODOMETER CARD & TRIP ODOMETER CARD
            item {
                PermanentOdometerDisplayCard(
                    totalOdometerKm = telemetryState.totalOdometerKm,
                    tripOdometerKm = telemetryState.tripOdometerKm,
                    speedUnit = speedUnit,
                    onResetTripClick = { showResetConfirmDialog = true },
                    onCalibrateTotalClick = { showQuickCalibrateDialog = true }
                )
            }

            // 5. TRIP STATISTICS (Three Compact Cards: Duration, Average Speed, Maximum Speed)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CompactStatisticCard(
                        icon = Icons.Outlined.Timer,
                        label = "Durasi Trip",
                        value = TelemetryFormatter.formatDurationHms(telemetryState.tripDurationSeconds),
                        unit = "jam:mnt:dtk",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("metric_trip_duration")
                    )
                    CompactStatisticCard(
                        icon = Icons.Outlined.Speed,
                        label = "Rata-rata",
                        value = TelemetryFormatter.formatSpeedOneDecimal(
                            telemetryState.avgSpeedKmh,
                            speedUnit.multiplier
                        ),
                        unit = speedUnit.symbol.uppercase(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("metric_avg_speed")
                    )
                    CompactStatisticCard(
                        icon = Icons.Outlined.TrendingUp,
                        label = "Maksimal",
                        value = TelemetryFormatter.formatSpeedOneDecimal(
                            telemetryState.maxSpeedKmh,
                            speedUnit.multiplier
                        ),
                        unit = speedUnit.symbol.uppercase(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("metric_max_speed")
                    )
                }
            }

            // 6. TRACKING CONTROL CARD
            item {
                MotoCard(contentPadding = PaddingValues(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Kontrol Auto Tracking",
                                style = MaterialTheme.typography.titleMedium,
                                color = cs.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isTrackingActive) {
                                    "Layanan latar belakang aktif • Notifikasi live berjalan"
                                } else {
                                    "Otomatis mencatat saat motor bergerak tanpa tombol manual"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 12.sp,
                                color = cs.onSurfaceVariant
                            )
                        }

                        // Status indicator dot + label
                        val ctrlColor = if (isTrackingActive) cs.primary else cs.onSurfaceVariant
                        Text(
                            text = if (isTrackingActive) "AKTIF" else "NONAKTIF",
                            style = MaterialTheme.typography.labelSmall,
                            color = ctrlColor
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (!isTrackingActive) {
                        // GREEN primary action button for starting Auto Tracking
                        Button(
                            onClick = onRequestPermissionsAndStart,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = cs.primary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_activate_auto_tracking")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = "Aktifkan Auto Tracking",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Aktifkan Auto Tracking",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                    } else {
                        // RED destructive/stop action button for stopping Auto Tracking
                        OutlinedButton(
                            onClick = onStopAutoTracking,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, cs.error),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = cs.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_deactivate_auto_tracking")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Stop,
                                contentDescription = "Matikan Auto Tracking",
                                tint = cs.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Matikan Auto Tracking",
                                style = MaterialTheme.typography.titleMedium,
                                color = cs.error
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = cs.outline
                    )

                    // Quick link to Trip History inside the grouped card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenHistoryTab() }
                            .padding(vertical = 4.dp)
                            .testTag("btn_open_history"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = "Riwayat Perjalanan",
                                tint = cs.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Lihat Riwayat & Peta Rute Perjalanan",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = cs.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = "Buka Riwayat",
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 7. COMPACT WIDGET CARD (Preserves 2x2 Home Screen Widget & Customization)
            item {
                MotoCard(contentPadding = PaddingValues(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Widget Layar Utama 2×2",
                                style = MaterialTheme.typography.titleMedium,
                                color = cs.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Menampilkan ${settingsState.bikeName}, Odometer (1–999.999) & Trip KM",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 12.sp,
                                color = cs.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { showFullWidgetGallerySheet = true }) {
                            Text(
                                text = "Kustomisasi",
                                style = MaterialTheme.typography.labelLarge,
                                color = cs.secondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showEditBikeNameDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, cs.outline),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Nama Motor",
                                tint = cs.onSurface,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Nama Motor",
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.onSurface
                            )
                        }

                        Button(
                            onClick = onPinWidgetClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = cs.secondary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("btn_pin_home_widget")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AddToHomeScreen,
                                contentDescription = "Pasang Widget",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pasang Widget",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    // Full-Screen Widget Preview & Customizer Modal
    if (showFullWidgetGallerySheet) {
        Dialog(
            onDismissRequest = { showFullWidgetGallerySheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(cs.background)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showFullWidgetGallerySheet = false }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Tutup",
                                    tint = cs.onBackground
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.TwoWheeler,
                                    contentDescription = null,
                                    tint = cs.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Pratinjau Widget 2×2",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = cs.onBackground
                                )
                            }
                            Spacer(modifier = Modifier.width(48.dp))
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Odo · ${settingsState.bikeName}",
                            style = MaterialTheme.typography.headlineLarge,
                            color = cs.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Cincin melingkar mengikuti angka odometer (1 – 999.999 ${speedUnit.distanceSymbol.uppercase()})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SquareCircularOdometerWidgetPreview(
                            bikeName = settingsState.bikeName,
                            totalOdometerKm = telemetryState.totalOdometerKm,
                            tripOdometerKm = telemetryState.tripOdometerKm,
                            speedUnit = speedUnit,
                            widgetTheme = settingsState.widgetTheme,
                            isMoving = telemetryState.motionStatus == MotionStatus.MOVING && isTrackingActive,
                            sizeDp = 212.dp
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WidgetThemeStyle.entries.forEach { theme ->
                                val selected = settingsState.widgetTheme == theme
                                Box(
                                    modifier = Modifier
                                        .size(if (selected) 28.dp else 22.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    Color(theme.startColorHex),
                                                    Color(theme.endColorHex)
                                                )
                                            )
                                        )
                                        .border(
                                            width = if (selected) 2.dp else 0.dp,
                                            color = if (selected) cs.onBackground else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { onWidgetThemeSelected(theme) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tema: ${settingsState.widgetTheme.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                showFullWidgetGallerySheet = false
                                onPinWidgetClick()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = cs.secondary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = "Pasang Widget ke Layar Utama",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }

                        OutlinedButton(
                            onClick = { showEditBikeNameDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, cs.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = "Ubah Nama Motor (${settingsState.bikeName})",
                                style = MaterialTheme.typography.titleMedium,
                                color = cs.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditBikeNameDialog) {
        var nameInput by remember(settingsState.bikeName) { mutableStateOf(settingsState.bikeName) }
        AlertDialog(
            onDismissRequest = { showEditBikeNameDialog = false },
            containerColor = cs.surface,
            titleContentColor = cs.onSurface,
            textContentColor = cs.onSurfaceVariant,
            title = { Text("Ubah Nama Motor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Nama motor ini ditampilkan pada header Dashboard dan Widget 2×2 Layar Utama.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Nama Motor") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onBikeNameChanged(nameInput)
                        showEditBikeNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.primary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditBikeNameDialog = false }) {
                    Text("Batal", color = cs.onSurfaceVariant)
                }
            }
        )
    }

    if (showQuickCalibrateDialog) {
        var odoInput by remember(telemetryState.totalOdometerKm) {
            mutableStateOf(String.format("%.1f", telemetryState.totalOdometerKm).replace(',', '.'))
        }
        AlertDialog(
            onDismissRequest = { showQuickCalibrateDialog = false },
            containerColor = cs.surface,
            titleContentColor = cs.onSurface,
            textContentColor = cs.onSurfaceVariant,
            title = { Text("Sesuaikan Angka Odometer Motor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Masukkan angka kilometer dari odometer fisik motor Anda (1 – 999.999).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = odoInput,
                        onValueChange = { odoInput = it },
                        label = { Text("Total Odometer (${speedUnit.distanceSymbol.uppercase()})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = odoInput.replace(',', '.').toDoubleOrNull()
                        if (parsed != null && parsed >= 0.0) {
                            onQuickCalibrateOdometer(parsed)
                            showQuickCalibrateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.primary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickCalibrateDialog = false }) {
                    Text("Batal", color = cs.onSurfaceVariant)
                }
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            containerColor = cs.surface,
            titleContentColor = cs.onSurface,
            textContentColor = cs.onSurfaceVariant,
            title = {
                Text(
                    text = "Reset Trip Odometer?",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Trip Odometer akan dikembalikan ke 0,00 ${speedUnit.distanceSymbol.uppercase()}. " +
                        "Total Odometer Permanen (${TelemetryFormatter.formatOdometer(telemetryState.totalOdometerKm, speedUnit.multiplier)} ${speedUnit.distanceSymbol.uppercase()}) tetap tersimpan.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        onResetTrip()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.error,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("btn_confirm_reset_trip")
                ) {
                    Text("Reset Trip")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Batal", color = cs.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun WarningStatusCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    testTag: String
) {
    val cs = MaterialTheme.colorScheme
    MotoCard(contentPadding = PaddingValues(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(cs.error.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = cs.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.error
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.error,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag(testTag)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun CompactStatisticCard(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    MotoCard(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = cs.secondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontSize = 17.sp,
            color = cs.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant
        )
    }
}
