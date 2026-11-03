package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.AutoTrackingService
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatisticsScreen
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.PanelDarkGray
import com.example.ui.theme.PanelElevatedGray
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondarySilver
import com.example.viewmodel.MotoOdoViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MotoOdoTab(
    val routeId: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    DASHBOARD(
        routeId = "dashboard",
        label = "Dashboard",
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard,
        testTag = "nav_tab_dashboard"
    ),
    HISTORY(
        routeId = "history",
        label = "Riwayat",
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_tab_history"
    ),
    STATISTICS(
        routeId = "statistics",
        label = "Statistik",
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
        testTag = "nav_tab_statistics"
    ),
    SETTINGS(
        routeId = "settings",
        label = "Pengaturan",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_settings"
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MotoOdoApplication
        val factory = MotoOdoViewModel.Factory(
            odometerPreferences = app.odometerPreferences,
            tripRepository = app.tripRepository
        )

        setContent {
            MyApplicationTheme {
                val viewModel: MotoOdoViewModel = viewModel(factory = factory)
                MotoOdoMainApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MotoOdoMainApp(viewModel: MotoOdoViewModel) {
    val context = LocalContext.current
    val telemetryState by viewModel.telemetryState.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val tripsState by viewModel.tripsState.collectAsStateWithLifecycle()
    val statisticsState by viewModel.statisticsState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(MotoOdoTab.DASHBOARD) }
    var hasLocationPermission by remember { mutableStateOf(checkLocationPermissions(context)) }
    var showBackgroundLocationRationale by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    // Automatically resume Foreground Service if Auto Tracking was previously enabled
    LaunchedEffect(settingsState.isAutoTrackingEnabled, hasLocationPermission) {
        if (settingsState.isAutoTrackingEnabled && hasLocationPermission) {
            AutoTrackingService.startService(context)
        }
    }

    // Background Location Permission Launcher (Android 10+)
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.startAutoTracking(context)
    }

    // Foreground Location + Notification Permission Launcher
    val primaryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = fineGranted || coarseGranted || checkLocationPermissions(context)

        if (hasLocationPermission) {
            viewModel.startAutoTracking(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !checkBackgroundLocationPermission(context)) {
                showBackgroundLocationRationale = true
            }
        } else {
            // Even if denied in an emulator without GPS permissions, start service or inform user
            viewModel.startAutoTracking(context)
        }
    }

    // JSON Backup & Restore Launchers
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportBackupToUri(context, uri)
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importBackupFromUri(context, uri)
        }
    }

    val requestPermissionsAndStartTracking: () -> Unit = {
        val permsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (checkLocationPermissions(context)) {
            hasLocationPermission = true
            viewModel.startAutoTracking(context)
        } else {
            primaryPermissionLauncher.launch(permsToRequest.toTypedArray())
        }
    }

    // Handle system back button on secondary tabs to return to Dashboard
    if (currentTab != MotoOdoTab.DASHBOARD) {
        BackHandler {
            currentTab = MotoOdoTab.DASHBOARD
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CarbonBlack,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = PanelDarkGray,
                tonalElevation = 8.dp
            ) {
                MotoOdoTab.entries.forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CarbonBlack,
                            selectedTextColor = NeonGreen,
                            indicatorColor = NeonGreen,
                            unselectedIconColor = TextSecondarySilver,
                            unselectedTextColor = TextSecondarySilver
                        ),
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        when (currentTab) {
            MotoOdoTab.DASHBOARD -> {
                DashboardScreen(
                    telemetryState = telemetryState,
                    settingsState = settingsState,
                    hasLocationPermission = hasLocationPermission,
                    onRequestPermissionsAndStart = requestPermissionsAndStartTracking,
                    onStopAutoTracking = { viewModel.stopAutoTracking(context) },
                    onToggleSimulation = { viewModel.toggleSimulationMode(context) },
                    onResetTrip = { viewModel.resetTripOdometer(context) },
                    onOpenHistoryTab = { currentTab = MotoOdoTab.HISTORY },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            MotoOdoTab.HISTORY -> {
                HistoryScreen(
                    trips = tripsState,
                    totalPermanentOdometerKm = telemetryState.totalOdometerKm,
                    speedUnit = settingsState.speedUnit,
                    onDeleteTrip = { id -> viewModel.deleteSingleTrip(id) },
                    onDeleteAllTrips = { viewModel.deleteAllTripsHistory() },
                    onStartSimulationFromEmpty = {
                        viewModel.toggleSimulationMode(context)
                        currentTab = MotoOdoTab.DASHBOARD
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            MotoOdoTab.STATISTICS -> {
                StatisticsScreen(
                    statistics = statisticsState,
                    speedUnit = settingsState.speedUnit,
                    modifier = Modifier.padding(innerPadding)
                )
            }

            MotoOdoTab.SETTINGS -> {
                SettingsScreen(
                    settingsState = settingsState,
                    totalOdometerKm = telemetryState.totalOdometerKm,
                    tripOdometerKm = telemetryState.tripOdometerKm,
                    onToggleAutoTracking = { enabled ->
                        if (enabled) {
                            requestPermissionsAndStartTracking()
                        } else {
                            viewModel.stopAutoTracking(context)
                        }
                    },
                    onSensitivitySelected = { viewModel.setSensitivityLevel(it) },
                    onAccuracyFilterSelected = { viewModel.setGpsAccuracyFilter(it) },
                    onPowerModeSelected = { viewModel.setPowerMode(it) },
                    onSpeedUnitSelected = { viewModel.setSpeedUnit(it) },
                    onCalibrationChanged = { viewModel.setCalibrationFactor(it) },
                    onProtectedTotalOdometerAdjust = { viewModel.setProtectedTotalOdometer(it) },
                    onResetTrip = { viewModel.resetTripOdometer(context) },
                    onNotificationsEnabledChanged = { viewModel.setNotificationsEnabled(it) },
                    onExportBackupClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        exportBackupLauncher.launch("moto_odo_backup_$stamp.json")
                    },
                    onImportBackupClick = {
                        importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                    onOpenBatterySettingsClick = {
                        openBatteryOptimizationSettings(context)
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }

    if (showBackgroundLocationRationale && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        AlertDialog(
            onDismissRequest = { showBackgroundLocationRationale = false },
            containerColor = PanelDarkGray,
            titleContentColor = TextPrimaryWhite,
            textContentColor = TextSecondarySilver,
            title = { Text("Optimalkan Pelacakan Latar Belakang") },
            text = {
                Text(
                    "Agar MOTO ODO tetap mencatat kilometer dengan akurasi maksimal saat aplikasi ditutup atau layar HP terkunci, pilih opsi 'Izinkan sepanjang waktu' (Allow all the time) pada pengaturan izin lokasi berikutnya."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBackgroundLocationRationale = false
                        backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberCyan,
                        contentColor = CarbonBlack
                    )
                ) {
                    Text("Lanjutkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundLocationRationale = false }) {
                    Text("Nanti Saja", color = PanelElevatedGray)
                }
            }
        )
    }
}

private fun checkLocationPermissions(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

private fun checkBackgroundLocationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun openBatteryOptimizationSettings(context: Context) {
    runCatching {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }.onFailure {
        runCatching {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
        }
    }
}
