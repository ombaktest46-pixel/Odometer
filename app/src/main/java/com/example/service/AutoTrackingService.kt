package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.MotoOdoApplication
import com.example.R
import com.example.data.OdometerPreferences
import com.example.data.RouteJsonCodec
import com.example.data.TripEntity
import com.example.data.TripRepository
import com.example.model.GpsSignalQuality
import com.example.model.MotionStatus
import com.example.model.PowerMode
import com.example.model.RoutePoint
import com.example.model.SettingsState
import com.example.model.TelemetryFormatter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 100% Real Hardware GPS Foreground Location Service for MOTO ODO:
 * - Uses only real GPS satellite fixes (blocks movement when GPS is disabled or in Airplane Mode without GPS)
 * - Automatically detects motorcycle moving, stopping, and resuming without pressing START
 * - Updates Permanent Total Odometer & Trip Odometer in DataStore
 * - Updates single persistent Notification ("MOTO ODO 🏍️") in real-time
 * - Automatically saves completed ride history to Room Database when parked
 */
class AutoTrackingService : Service() {

    companion object {
        const val CHANNEL_ID = "moto_odo_live_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_AUTO_TRACKING = "com.example.action.START_AUTO_TRACKING"
        const val ACTION_STOP_AUTO_TRACKING = "com.example.action.STOP_AUTO_TRACKING"
        const val ACTION_SAVE_CURRENT_RIDE_NOW = "com.example.action.SAVE_CURRENT_RIDE_NOW"

        private const val AUTO_PARK_SAVE_TIMEOUT_MS = 45_000L
        private const val MIN_TRIP_DISTANCE_TO_SAVE_KM = 0.02

        fun startService(context: Context, action: String = ACTION_START_AUTO_TRACKING) {
            val intent = Intent(context, AutoTrackingService::class.java).apply {
                this.action = action
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AutoTrackingService::class.java).apply {
                action = ACTION_STOP_AUTO_TRACKING
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var notificationManager: NotificationManager
    private lateinit var odometerPreferences: OdometerPreferences
    private lateinit var tripRepository: TripRepository

    private val motionProcessor = GpsMotionProcessor()
    private var wakeLock: PowerManager.WakeLock? = null

    private var currentSettings = SettingsState()
    private var currentPowerMode: PowerMode? = null
    private var isLocationUpdatesRegistered = false
    private var watchdogJob: Job? = null

    // Active ride session tracking (automatically started on movement, saved on park)
    private var activeSessionStartMs: Long = 0L
    private var activeSessionDistanceKm: Double = 0.0
    private var activeSessionDurationSec: Long = 0L
    private var activeSessionMaxSpeedKmh: Float = 0f
    private val activeRoutePoints = ArrayList<RoutePoint>()
    private var lastMovingTimestampMs: Long = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location: Location = result.lastLocation ?: return

            // Strictly verify GPS hardware is enabled before accepting any location fix
            if (!isGpsProviderEnabled()) {
                motionProcessor.reset()
                TelemetryStateHolder.updateState {
                    it.copy(
                        isGpsHardwareEnabled = false,
                        isSimulationActive = false,
                        currentSpeedKmh = 0f,
                        motionStatus = MotionStatus.SEARCHING_GPS,
                        gpsSignalQuality = GpsSignalQuality.NO_SIGNAL,
                        statusDetailMessage = "GPS HP mati • Nyalakan GPS agar odometer bekerja"
                    )
                }
                updateNotificationNow()
                return
            }

            // Ignore mock locations
            @Suppress("DEPRECATION")
            val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                location.isMock
            } else {
                location.isFromMockProvider
            }
            if (isMock) return

            // Require accurate satellite fix or high-accuracy fused fix with Doppler speed
            if (!location.hasAccuracy() || location.accuracy <= 0f) return

            handleIncomingFix(
                RawGpsFix(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracy,
                    hasSpeed = location.hasSpeed(),
                    speedMetersPerSec = if (location.hasSpeed()) location.speed else 0f,
                    timestampMillis = if (location.time > 0L) location.time else System.currentTimeMillis(),
                    provider = location.provider ?: "gps",
                    isSimulated = false
                )
            )
        }
    }

    private val gpsProviderReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                checkGpsHardwareStatus()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as MotoOdoApplication
        odometerPreferences = app.odometerPreferences
        tripRepository = app.tripRepository
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel()
        acquirePartialWakeLock()

        runCatching {
            registerReceiver(
                gpsProviderReceiver,
                IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
            )
        }

        serviceScope.launch {
            odometerPreferences.cleanUpSimulationResidueOnce()
            val snapshot = odometerPreferences.getSnapshot()
            currentSettings = snapshot.settings
            if (snapshot.activeSessionDistanceKm >= MIN_TRIP_DISTANCE_TO_SAVE_KM && snapshot.activeSessionStartMillis > 0L) {
                activeSessionStartMs = snapshot.activeSessionStartMillis
                activeSessionDistanceKm = snapshot.activeSessionDistanceKm
                activeSessionDurationSec = snapshot.activeSessionDurationSeconds
                activeSessionMaxSpeedKmh = snapshot.activeSessionMaxSpeedKmh
                activeRoutePoints.clear()
                activeRoutePoints.addAll(RouteJsonCodec.decodeRoutePoints(snapshot.activeSessionRouteJson))
            }
            TelemetryStateHolder.syncFromPersisted(
                totalOdoKm = snapshot.totalOdometerKm,
                tripOdoKm = snapshot.tripOdometerKm,
                tripDurationSec = snapshot.tripDurationSeconds,
                tripMaxSpeed = snapshot.tripMaxSpeedKmh,
                isAutoTracking = true
            )
            checkGpsHardwareStatus()
            updateNotificationNow()
        }

        serviceScope.launch {
            odometerPreferences.persistedDataFlow.collectLatest { persisted ->
                val oldPowerMode = currentPowerMode
                currentSettings = persisted.settings
                TelemetryStateHolder.updateState { state ->
                    val avgSpd = if (persisted.tripDurationSeconds > 0L) {
                        ((persisted.tripOdometerKm / (persisted.tripDurationSeconds / 3600.0))).toFloat()
                            .coerceIn(0f, 220f)
                    } else {
                        0f
                    }
                    state.copy(
                        totalOdometerKm = persisted.totalOdometerKm,
                        tripOdometerKm = persisted.tripOdometerKm,
                        tripDurationSeconds = persisted.tripDurationSeconds,
                        avgSpeedKmh = avgSpd,
                        maxSpeedKmh = maxOf(state.maxSpeedKmh, persisted.tripMaxSpeedKmh),
                        isSimulationActive = false
                    )
                }
                if (isLocationUpdatesRegistered && oldPowerMode != persisted.settings.powerMode) {
                    currentPowerMode = persisted.settings.powerMode
                    registerGpsUpdates(persisted.settings.powerMode)
                }
                updateNotificationNow()
            }
        }

        startWatchdogLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_AUTO_TRACKING

        when (action) {
            ACTION_STOP_AUTO_TRACKING -> {
                serviceScope.launch {
                    finalizeAndSaveActiveRideIfEligible()
                    odometerPreferences.setAutoTrackingEnabled(false)
                    unregisterGpsUpdates()
                    TelemetryStateHolder.onTrackingStopped()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return START_NOT_STICKY
            }

            ACTION_SAVE_CURRENT_RIDE_NOW -> {
                serviceScope.launch {
                    finalizeAndSaveActiveRideIfEligible()
                    updateNotificationNow()
                }
            }

            else -> {
                promoteToForeground()
                serviceScope.launch {
                    odometerPreferences.setAutoTrackingEnabled(true)
                    checkGpsHardwareStatus()
                    registerGpsUpdates(currentSettings.powerMode)
                    updateNotificationNow()
                }
            }
        }

        return START_STICKY
    }

    private fun promoteToForeground() {
        val notification = buildLiveNotification(TelemetryStateHolder.telemetryState.value)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            TelemetryStateHolder.updateState {
                it.copy(
                    motionStatus = MotionStatus.SEARCHING_GPS,
                    statusDetailMessage = "Menunggu izin lokasi GPS diaktifkan"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerGpsUpdates(powerMode: PowerMode) {
        if (!hasLocationPermission()) {
            TelemetryStateHolder.updateState {
                it.copy(
                    motionStatus = MotionStatus.SEARCHING_GPS,
                    gpsSignalQuality = GpsSignalQuality.NO_SIGNAL,
                    statusDetailMessage = "Izin lokasi GPS diperlukan untuk Auto Tracking"
                )
            }
            return
        }

        if (isLocationUpdatesRegistered) {
            runCatching { fusedLocationClient.removeLocationUpdates(locationCallback) }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, powerMode.intervalMs)
            .setMinUpdateIntervalMillis(powerMode.minUpdateIntervalMs)
            .setWaitForAccurateLocation(true)
            .build()

        runCatching {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
            currentPowerMode = powerMode
            isLocationUpdatesRegistered = true
        }
    }

    private fun unregisterGpsUpdates() {
        if (isLocationUpdatesRegistered) {
            runCatching { fusedLocationClient.removeLocationUpdates(locationCallback) }
            isLocationUpdatesRegistered = false
        }
    }

    private fun handleIncomingFix(fix: RawGpsFix) {
        serviceScope.launch {
            val nowMs = System.currentTimeMillis()
            val result = motionProcessor.processFix(
                fix = fix,
                sensitivity = currentSettings.sensitivityLevel,
                accuracyFilter = currentSettings.gpsAccuracyFilter,
                calibrationFactor = currentSettings.calibrationFactor,
                nowMillis = nowMs
            )

            if (result.motionStatus == MotionStatus.MOVING && result.deltaDistanceKm > 0.0) {
                val now = fix.timestampMillis
                if (activeSessionStartMs == 0L) {
                    activeSessionStartMs = now
                    activeSessionDistanceKm = 0.0
                    activeSessionDurationSec = 0L
                    activeSessionMaxSpeedKmh = 0f
                    activeRoutePoints.clear()
                }

                lastMovingTimestampMs = now
                activeSessionDistanceKm += result.deltaDistanceKm
                activeSessionDurationSec += result.deltaMovingDurationSeconds
                if (result.currentSpeedKmh > activeSessionMaxSpeedKmh) {
                    activeSessionMaxSpeedKmh = result.currentSpeedKmh
                }

                activeRoutePoints.add(
                    RoutePoint(
                        lat = fix.latitude,
                        lng = fix.longitude,
                        speedKmh = result.currentSpeedKmh,
                        timestamp = now
                    )
                )

                val encodedRoute = RouteJsonCodec.encodeRoutePoints(activeRoutePoints)
                odometerPreferences.recordMovementDelta(
                    deltaKm = result.deltaDistanceKm,
                    deltaDurationSeconds = result.deltaMovingDurationSeconds,
                    currentSpeedKmh = result.currentSpeedKmh,
                    activeSessionDistKm = activeSessionDistanceKm,
                    activeSessionDurationSec = activeSessionDurationSec,
                    activeSessionStartMs = activeSessionStartMs,
                    activeSessionMaxSpeed = activeSessionMaxSpeedKmh,
                    activeSessionRouteJson = encodedRoute
                )
            }

            TelemetryStateHolder.updateState { current ->
                current.copy(
                    isAutoTrackingEnabled = true,
                    isSimulationActive = false,
                    isGpsHardwareEnabled = true,
                    currentSpeedKmh = result.currentSpeedKmh,
                    motionStatus = result.motionStatus,
                    gpsSignalQuality = result.signalQuality,
                    gpsAccuracyMeters = fix.accuracyMeters,
                    lastLatitude = fix.latitude,
                    lastLongitude = fix.longitude,
                    activeRideDistanceKm = activeSessionDistanceKm,
                    activeRideDurationSeconds = activeSessionDurationSec,
                    currentRoutePoints = activeRoutePoints.toList(),
                    statusDetailMessage = result.statusReason
                )
            }

            updateNotificationNow()
        }
    }

    private fun startWatchdogLoop() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(2000L)
                val gpsEnabled = isGpsProviderEnabled()
                if (!gpsEnabled) {
                    motionProcessor.reset()
                    TelemetryStateHolder.updateState { state ->
                        state.copy(
                            isGpsHardwareEnabled = false,
                            isSimulationActive = false,
                            currentSpeedKmh = 0f,
                            gpsAccuracyMeters = 0f,
                            motionStatus = MotionStatus.SEARCHING_GPS,
                            gpsSignalQuality = GpsSignalQuality.NO_SIGNAL,
                            statusDetailMessage = "GPS HP sedang mati • Aktifkan lokasi GPS"
                        )
                    }
                    updateNotificationNow()
                    continue
                }

                val now = System.currentTimeMillis()
                val becameIdle = motionProcessor.checkWatchdogIdle(now, idleTimeoutMs = 4500L)
                if (becameIdle) {
                    TelemetryStateHolder.updateState { state ->
                        if (state.isAutoTrackingEnabled && state.motionStatus == MotionStatus.MOVING) {
                            state.copy(
                                currentSpeedKmh = 0f,
                                motionStatus = MotionStatus.STOPPED,
                                statusDetailMessage = "Motor berhenti • Kecepatan 0 KM/JAM"
                            )
                        } else {
                            state
                        }
                    }
                    updateNotificationNow()
                }

                // Auto-save completed ride when parked for AUTO_PARK_SAVE_TIMEOUT_MS
                if (activeSessionStartMs > 0L &&
                    lastMovingTimestampMs > 0L &&
                    (now - lastMovingTimestampMs) >= AUTO_PARK_SAVE_TIMEOUT_MS &&
                    activeSessionDistanceKm >= MIN_TRIP_DISTANCE_TO_SAVE_KM
                ) {
                    finalizeAndSaveActiveRideIfEligible()
                    TelemetryStateHolder.updateState { state ->
                        state.copy(
                            activeRideDistanceKm = 0.0,
                            activeRideDurationSeconds = 0L,
                            statusDetailMessage = "Motor parkir • Perjalanan otomatis disimpan ke Riwayat"
                        )
                    }
                    updateNotificationNow()
                }
            }
        }
    }

    private suspend fun finalizeAndSaveActiveRideIfEligible() {
        val dist = activeSessionDistanceKm
        val startMs = activeSessionStartMs
        val durSec = activeSessionDurationSec.coerceAtLeast(1L)

        if (startMs > 0L && dist >= 0.01) {
            val endMs = if (lastMovingTimestampMs > startMs) lastMovingTimestampMs else System.currentTimeMillis()
            val avgSpd = ((dist / (durSec / 3600.0))).toFloat().coerceIn(0f, 220f)
            val maxSpd = maxOf(activeSessionMaxSpeedKmh, avgSpd)
            val firstPt = activeRoutePoints.firstOrNull()
            val lastPt = activeRoutePoints.lastOrNull()

            val startLabel = if (firstPt != null) {
                String.format(Locale.US, "%.4f, %.4f", firstPt.lat, firstPt.lng)
            } else {
                "Titik Awal GPS"
            }
            val endLabel = if (lastPt != null) {
                String.format(Locale.US, "%.4f, %.4f", lastPt.lat, lastPt.lng)
            } else {
                "Titik Parkir GPS"
            }

            val tripEntity = TripEntity(
                startTimeMillis = startMs,
                endTimeMillis = endMs,
                distanceKm = dist,
                durationSeconds = durSec,
                avgSpeedKmh = avgSpd,
                maxSpeedKmh = maxSpd,
                startCoordLabel = startLabel,
                endCoordLabel = endLabel,
                routePointsJson = RouteJsonCodec.encodeRoutePoints(activeRoutePoints),
                isSimulated = false
            )
            tripRepository.insertTrip(tripEntity)
        }

        activeSessionStartMs = 0L
        activeSessionDistanceKm = 0.0
        activeSessionDurationSec = 0L
        activeSessionMaxSpeedKmh = 0f
        lastMovingTimestampMs = 0L
        activeRoutePoints.clear()
        odometerPreferences.clearActiveSessionState()
    }

    private fun isGpsProviderEnabled(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return runCatching {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)
    }

    private fun checkGpsHardwareStatus() {
        val isGpsEnabled = isGpsProviderEnabled()
        TelemetryStateHolder.updateState { current ->
            if (!isGpsEnabled) {
                motionProcessor.reset()
                current.copy(
                    isGpsHardwareEnabled = false,
                    isSimulationActive = false,
                    currentSpeedKmh = 0f,
                    gpsAccuracyMeters = 0f,
                    motionStatus = MotionStatus.SEARCHING_GPS,
                    gpsSignalQuality = GpsSignalQuality.NO_SIGNAL,
                    statusDetailMessage = "GPS HP sedang mati • Aktifkan lokasi GPS agar Auto Tracking bekerja"
                )
            } else {
                current.copy(
                    isGpsHardwareEnabled = true,
                    isSimulationActive = false,
                    motionStatus = if (current.motionStatus == MotionStatus.TRACKING_OFF) {
                        MotionStatus.STOPPED
                    } else {
                        current.motionStatus
                    },
                    statusDetailMessage = "GPS aktif • Memantau pergerakan motor otomatis"
                )
            }
        }
        updateNotificationNow()
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildLiveNotification(state: com.example.model.LiveTelemetryState): Notification {
        val unit = currentSettings.speedUnit
        val totalFormatted = TelemetryFormatter.formatOdometer(state.totalOdometerKm, unit.multiplier)
        val tripFormatted = TelemetryFormatter.formatOdometer(state.tripOdometerKm, unit.multiplier)
        val speedFormatted = TelemetryFormatter.formatSpeedInt(state.currentSpeedKmh, unit.multiplier)

        val statusText = when {
            !state.isGpsHardwareEnabled -> "● GPS MATI (MENUNGGU GPS)"
            state.motionStatus == MotionStatus.MOVING -> "● MOTOR BERJALAN"
            state.motionStatus == MotionStatus.STOPPED -> "● MOTOR BERHENTI"
            state.motionStatus == MotionStatus.SEARCHING_GPS -> "● MENCARI SINYAL GPS"
            else -> "● MOTOR BERHENTI"
        }

        val bigTextBody = buildString {
            appendLine("TOTAL ODOMETER")
            appendLine("$totalFormatted ${unit.distanceSymbol}")
            appendLine()
            appendLine("TRIP")
            appendLine("$tripFormatted ${unit.distanceSymbol}")
            appendLine()
            appendLine("KECEPATAN")
            appendLine("$speedFormatted ${unit.symbol}")
            appendLine()
            append("STATUS\n$statusText")
        }

        val compactSummary = "$speedFormatted ${unit.symbol} • ODO: $totalFormatted ${unit.distanceSymbol} • TRIP: $tripFormatted ${unit.distanceSymbol}"

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            if (!state.isGpsHardwareEnabled) {
                action = com.example.widget.MotoOdoWidgetProvider.ACTION_WIDGET_ENABLE_GPS
                putExtra(com.example.widget.MotoOdoWidgetProvider.EXTRA_PROMPT_ENABLE_GPS, true)
            }
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            101,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AutoTrackingService::class.java).apply {
            action = ACTION_STOP_AUTO_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val primaryActionLabel = if (!state.isGpsHardwareEnabled) {
            "Hidupkan GPS"
        } else {
            getString(R.string.notif_action_open)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_moto_notification)
            .setContentTitle("MOTO ODO \uD83C\uDFCD\uFE0F")
            .setContentText(compactSummary)
            .setSubText(statusText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("MOTO ODO \uD83C\uDFCD\uFE0F")
                    .bigText(bigTextBody)
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openPendingIntent)
            .addAction(
                R.drawable.ic_moto_notification,
                primaryActionLabel,
                openPendingIntent
            )
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.notif_action_stop),
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotificationNow() {
        com.example.widget.MotoOdoWidgetProvider.updateAllWidgets(this)
        if (!currentSettings.notificationsEnabled) return
        val state = TelemetryStateHolder.telemetryState.value
        if (!state.isAutoTrackingEnabled) return
        runCatching {
            notificationManager.notify(NOTIFICATION_ID, buildLiveNotification(state))
        }
    }

    private fun acquirePartialWakeLock() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MotoOdo::AutoTrackingWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(6 * 60 * 60 * 1000L)
            }
        }
    }

    override fun onDestroy() {
        watchdogJob?.cancel()
        unregisterGpsUpdates()
        runCatching { unregisterReceiver(gpsProviderReceiver) }
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
