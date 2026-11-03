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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Foreground Location Service that powers MOTO ODO:
 * - Continuously monitors GPS in background and when screen is off
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
        const val ACTION_START_SIMULATION = "com.example.action.START_SIMULATION"
        const val ACTION_STOP_SIMULATION = "com.example.action.STOP_SIMULATION"
        const val ACTION_SAVE_CURRENT_RIDE_NOW = "com.example.action.SAVE_CURRENT_RIDE_NOW"

        // Automatically finalize and save a trip to history after 45s of continuous parking
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
    private var simulationJob: Job? = null
    private var watchdogJob: Job? = null

    // Active ride session tracking (automatically started on movement, saved on park)
    private var activeSessionStartMs: Long = 0L
    private var activeSessionDistanceKm: Double = 0.0
    private var activeSessionDurationSec: Long = 0L
    private var activeSessionMaxSpeedKmh: Float = 0f
    private val activeRoutePoints = ArrayList<RoutePoint>()
    private var lastMovingTimestampMs: Long = 0L
    private var activeSessionSimulated: Boolean = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (simulationJob?.isActive == true) return
            val location: Location = result.lastLocation ?: return
            handleIncomingFix(
                RawGpsFix(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = if (location.hasAccuracy()) location.accuracy else 15f,
                    hasSpeed = location.hasSpeed(),
                    speedMetersPerSec = if (location.hasSpeed()) location.speed else 0f,
                    timestampMillis = if (location.time > 0L) location.time else System.currentTimeMillis(),
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

        // Restore any unfinished session if service was restarted by Android OS
        serviceScope.launch {
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
            updateNotificationNow()
        }

        // Reactively observe settings changes (e.g. calibration, sensitivity, power mode, unit)
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
                        maxSpeedKmh = maxOf(state.maxSpeedKmh, persisted.tripMaxSpeedKmh)
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
                    stopSimulationInternal()
                    finalizeAndSaveActiveRideIfEligible()
                    odometerPreferences.setAutoTrackingEnabled(false)
                    unregisterGpsUpdates()
                    TelemetryStateHolder.onTrackingStopped()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return START_NOT_STICKY
            }

            ACTION_START_SIMULATION -> {
                promoteToForeground()
                serviceScope.launch {
                    odometerPreferences.setAutoTrackingEnabled(true)
                    startSimulationInternal()
                }
            }

            ACTION_STOP_SIMULATION -> {
                serviceScope.launch {
                    stopSimulationInternal()
                    finalizeAndSaveActiveRideIfEligible()
                    checkGpsHardwareStatus()
                    registerGpsUpdates(currentSettings.powerMode)
                    updateNotificationNow()
                }
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
        } catch (e: Exception) {
            // If location permission wasn't granted yet when invoked, update state gracefully
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
            .setWaitForAccurateLocation(false)
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
            val result = motionProcessor.processFix(
                fix = fix,
                sensitivity = currentSettings.sensitivityLevel,
                accuracyFilter = currentSettings.gpsAccuracyFilter,
                calibrationFactor = currentSettings.calibrationFactor
            )

            if (result.motionStatus == MotionStatus.MOVING && result.deltaDistanceKm > 0.0) {
                val now = fix.timestampMillis
                if (activeSessionStartMs == 0L) {
                    activeSessionStartMs = now
                    activeSessionDistanceKm = 0.0
                    activeSessionDurationSec = 0L
                    activeSessionMaxSpeedKmh = 0f
                    activeRoutePoints.clear()
                    activeSessionSimulated = fix.isSimulated
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
                    isSimulationActive = fix.isSimulated,
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

    /**
     * Watchdog loop runs every 2 seconds to:
     * 1. Reset speed to 0 KM/JAM and status to MOTOR BERHENTI if no movement fixes arrive
     * 2. Auto-save the active ride to Room Database if the motorcycle has been parked for >= 45s
     */
    private fun startWatchdogLoop() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(2000L)
                val now = System.currentTimeMillis()
                val becameIdle = motionProcessor.checkWatchdogIdle(now, idleTimeoutMs = 5000L)
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
                isSimulated = activeSessionSimulated
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

    /**
     * Realistic Indoor Ride Simulation Mode so the user can test Auto Tracking,
     * Speedometer, Odometer accumulation, Red-light Auto-Stop, Auto-Resume, and
     * Live Notification even while indoors.
     */
    private fun startSimulationInternal() {
        if (simulationJob?.isActive == true) return
        motionProcessor.reset()

        simulationJob = serviceScope.launch {
            var simLat = -6.200000 // Central Jakarta reference coordinate
            var simLng = 106.816666
            var headingRad = 0.45
            var tick = 0

            while (isActive) {
                // Realistic motorcycle speed profile:
                // Ticks 0..2: Starting engine / stationary (0 km/h)
                // Ticks 3..14: Accelerating & cruising (32 -> 68 km/h)
                // Ticks 15..20: Stopping at traffic light (0 km/h -> MOTOR BERHENTI)
                // Ticks 21..35: Resuming automatically & spirited ride (42 -> 78 km/h)
                val cycleTick = tick % 36
                val targetSpeedKmh: Float = when (cycleTick) {
                    in 0..2 -> 0f
                    in 3..6 -> 28f + (cycleTick - 3) * 8f
                    in 7..14 -> 58f + ((cycleTick % 3) * 4f)
                    in 15..20 -> 0f // Traffic light stop!
                    in 21..25 -> 34f + (cycleTick - 21) * 7f
                    else -> 64f + ((cycleTick % 4) * 3.5f)
                }

                val speedMps = targetSpeedKmh / 3.6f
                // Curve heading gently to create a realistic winding route on the vector map
                headingRad += sin(tick * 0.18) * 0.09
                val distanceMeters = speedMps * 1.0 // 1-second simulation steps

                val deltaLat = (distanceMeters * cos(headingRad)) / 111320.0
                val deltaLng = (distanceMeters * sin(headingRad)) / (111320.0 * cos(Math.toRadians(simLat)))

                simLat += deltaLat
                simLng += deltaLng

                handleIncomingFix(
                    RawGpsFix(
                        latitude = simLat,
                        longitude = simLng,
                        accuracyMeters = 4.5f,
                        hasSpeed = true,
                        speedMetersPerSec = speedMps,
                        timestampMillis = System.currentTimeMillis(),
                        isSimulated = true
                    )
                )

                tick++
                delay(1000L)
            }
        }
    }

    private fun stopSimulationInternal() {
        simulationJob?.cancel()
        simulationJob = null
        motionProcessor.reset()
        TelemetryStateHolder.updateState {
            it.copy(
                isSimulationActive = false,
                currentSpeedKmh = 0f,
                motionStatus = if (it.isAutoTrackingEnabled) MotionStatus.STOPPED else MotionStatus.TRACKING_OFF,
                gpsSignalQuality = GpsSignalQuality.NO_SIGNAL,
                statusDetailMessage = "Mode simulasi dihentikan • Kembali ke sensor GPS asli"
            )
        }
    }

    private fun checkGpsHardwareStatus() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = runCatching {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)

        TelemetryStateHolder.updateState { current ->
            if (!isGpsEnabled && !current.isSimulationActive) {
                current.copy(
                    isGpsHardwareEnabled = false,
                    currentSpeedKmh = 0f,
                    motionStatus = MotionStatus.SEARCHING_GPS,
                    gpsSignalQuality = GpsSignalQuality.NO_SIGNAL,
                    statusDetailMessage = "GPS HP sedang mati • Aktifkan lokasi agar Auto Tracking bekerja"
                )
            } else {
                current.copy(
                    isGpsHardwareEnabled = true,
                    statusDetailMessage = if (current.motionStatus == MotionStatus.SEARCHING_GPS) {
                        "GPS aktif • Memantau pergerakan motor otomatis"
                    } else {
                        current.statusDetailMessage
                    }
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
                NotificationManager.IMPORTANCE_LOW // Silent real-time updates without annoying sound/vibration every second
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
            !state.isGpsHardwareEnabled && !state.isSimulationActive -> "● GPS MATI (MENUNGGU GPS)"
            state.motionStatus == MotionStatus.MOVING -> "● MOTOR BERJALAN"
            state.motionStatus == MotionStatus.STOPPED -> "● MOTOR BERHENTI"
            state.motionStatus == MotionStatus.SEARCHING_GPS -> "● MENCARI SINYAL GPS"
            else -> "● SIAGA"
        }

        // Exact format requested by user for live notification:
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
                getString(R.string.notif_action_open),
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
        if (!currentSettings.notificationsEnabled) return
        val state = TelemetryStateHolder.telemetryState.value
        if (!state.isAutoTrackingEnabled && !state.isSimulationActive) return
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
                acquire(6 * 60 * 60 * 1000L) // 6-hour safety cap, renewed on start
            }
        }
    }

    override fun onDestroy() {
        watchdogJob?.cancel()
        simulationJob?.cancel()
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
