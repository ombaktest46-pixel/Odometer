package com.example.service

import com.example.model.GpsAccuracyFilter
import com.example.model.GpsSignalQuality
import com.example.model.MotionStatus
import com.example.model.SensitivityLevel
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class RawGpsFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val hasSpeed: Boolean,
    val speedMetersPerSec: Float,
    val timestampMillis: Long,
    val provider: String = "gps",
    val isSimulated: Boolean = false
)

data class ProcessedMotionResult(
    val acceptedFix: Boolean,
    val deltaDistanceKm: Double,
    val deltaMovingDurationSeconds: Long,
    val currentSpeedKmh: Float,
    val motionStatus: MotionStatus,
    val signalQuality: GpsSignalQuality,
    val statusReason: String
)

/**
 * Strict Hardware GPS Movement & Anti-Drift Processor for MOTO ODO.
 * - Rejects network/Wi-Fi/cell triangulation when GPS is off or inaccurate
 * - Requires real GPS Doppler velocity when available to block stationary multipath drift
 * - Rejects stale cached locations and impossible coordinate spikes
 */
class GpsMotionProcessor {

    private var lastValidFix: RawGpsFix? = null
    private var consecutiveMovingSamples: Int = 0
    private var consecutiveStoppedSamples: Int = 0
    private var lastMovementTimestampMs: Long = 0L
    private var smoothedSpeedKmh: Float = 0f

    fun reset() {
        lastValidFix = null
        consecutiveMovingSamples = 0
        consecutiveStoppedSamples = 0
        lastMovementTimestampMs = 0L
        smoothedSpeedKmh = 0f
    }

    fun evaluateSignalQuality(accuracyMeters: Float): GpsSignalQuality {
        return when {
            accuracyMeters <= 0f -> GpsSignalQuality.NO_SIGNAL
            accuracyMeters <= 8f -> GpsSignalQuality.EXCELLENT
            accuracyMeters <= 16f -> GpsSignalQuality.GOOD
            accuracyMeters <= 28f -> GpsSignalQuality.FAIR
            else -> GpsSignalQuality.WEAK
        }
    }

    fun processFix(
        fix: RawGpsFix,
        sensitivity: SensitivityLevel,
        accuracyFilter: GpsAccuracyFilter,
        calibrationFactor: Float,
        nowMillis: Long = fix.timestampMillis
    ): ProcessedMotionResult {
        val signalQuality = evaluateSignalQuality(fix.accuracyMeters)

        // 0. Reject stale cached fixes (older than 12 seconds compared to system clock)
        if (nowMillis > 0L && abs(nowMillis - fix.timestampMillis) > 12_000L) {
            return ProcessedMotionResult(
                acceptedFix = false,
                deltaDistanceKm = 0.0,
                deltaMovingDurationSeconds = 0L,
                currentSpeedKmh = 0f,
                motionStatus = MotionStatus.STOPPED,
                signalQuality = signalQuality,
                statusReason = "Mengabaikan lokasi cache lama • Menunggu sinyal GPS baru"
            )
        }

        // 1. Filter out poor GPS accuracy
        if (fix.accuracyMeters <= 0f || fix.accuracyMeters > accuracyFilter.maxAccuracyMeters) {
            consecutiveStoppedSamples++
            if (consecutiveStoppedSamples >= 2) {
                consecutiveMovingSamples = 0
                smoothedSpeedKmh = 0f
            }
            return ProcessedMotionResult(
                acceptedFix = false,
                deltaDistanceKm = 0.0,
                deltaMovingDurationSeconds = 0L,
                currentSpeedKmh = 0f,
                motionStatus = if (lastValidFix == null) MotionStatus.SEARCHING_GPS else MotionStatus.STOPPED,
                signalQuality = signalQuality,
                statusReason = "Akurasi GPS rendah (±${fix.accuracyMeters.toInt()}m), menunggu sinyal satelit stabil"
            )
        }

        val previous = lastValidFix
        if (previous == null) {
            lastValidFix = fix
            return ProcessedMotionResult(
                acceptedFix = true,
                deltaDistanceKm = 0.0,
                deltaMovingDurationSeconds = 0L,
                currentSpeedKmh = 0f,
                motionStatus = MotionStatus.STOPPED,
                signalQuality = signalQuality,
                statusReason = "Sinyal GPS terkunci (±${fix.accuracyMeters.toInt()}m) • Motor diam (0 KM/JAM)"
            )
        }

        // 2. Avoid duplicate or out-of-order timestamps
        val deltaTimeMs = fix.timestampMillis - previous.timestampMillis
        if (deltaTimeMs <= 250L) {
            return ProcessedMotionResult(
                acceptedFix = false,
                deltaDistanceKm = 0.0,
                deltaMovingDurationSeconds = 0L,
                currentSpeedKmh = if (consecutiveMovingSamples >= sensitivity.requiredMovingSamples) smoothedSpeedKmh else 0f,
                motionStatus = if (consecutiveMovingSamples >= sensitivity.requiredMovingSamples) MotionStatus.MOVING else MotionStatus.STOPPED,
                signalQuality = signalQuality,
                statusReason = "Menunggu interval GPS berikutnya"
            )
        }

        val deltaTimeSec = deltaTimeMs / 1000.0
        val rawDistanceMeters = haversineDistanceMeters(
            previous.latitude,
            previous.longitude,
            fix.latitude,
            fix.longitude
        )

        // 3. Calculate coordinate speed and Doppler GPS speed
        val coordSpeedKmh = ((rawDistanceMeters / deltaTimeSec) * 3.6).toFloat()
        val dopplerSpeedKmh = if (fix.hasSpeed && fix.speedMetersPerSec >= 0f) {
            fix.speedMetersPerSec * 3.6f
        } else {
            -1f
        }

        // 4. Filter impossible GPS spikes / cell-tower jumps
        if (coordSpeedKmh > 200f || (rawDistanceMeters > 150.0 && dopplerSpeedKmh in 0f..2.5f)) {
            if (deltaTimeSec > 20.0) {
                lastValidFix = fix
            }
            consecutiveStoppedSamples++
            smoothedSpeedKmh = 0f
            return ProcessedMotionResult(
                acceptedFix = false,
                deltaDistanceKm = 0.0,
                deltaMovingDurationSeconds = 0L,
                currentSpeedKmh = 0f,
                motionStatus = MotionStatus.STOPPED,
                signalQuality = signalQuality,
                statusReason = "Lonjakan koordinat diabaikan (Anti-Drift Aktif)"
            )
        }

        // 5. Strict Stationary Anti-Drift Check:
        // When real GPS hardware reports Doppler speed (hasSpeed == true), if Doppler speed is below
        // 2.2 km/h (~0.6 m/s), the phone is physically stationary! Block any coordinate drift.
        val isPhysicallyStationaryByDoppler = fix.hasSpeed && dopplerSpeedKmh < (sensitivity.minSpeedKmh * 0.65f)

        val effectiveSpeedKmh = if (isPhysicallyStationaryByDoppler) {
            0f
        } else if (fix.hasSpeed && dopplerSpeedKmh >= 0f) {
            // Blend 75% Doppler satellite speed + 25% Haversine coordinate speed
            dopplerSpeedKmh * 0.75f + coordSpeedKmh * 0.25f
        } else {
            coordSpeedKmh
        }

        // Displacement must exceed both minimum step distance AND 35% of GPS horizontal uncertainty radius
        val driftThresholdMeters = max(
            sensitivity.minStepDistanceMeters.toDouble(),
            (fix.accuracyMeters * 0.35f).toDouble()
        )

        val isSampleMoving = !isPhysicallyStationaryByDoppler &&
            effectiveSpeedKmh >= sensitivity.minSpeedKmh &&
            rawDistanceMeters >= driftThresholdMeters

        return if (isSampleMoving) {
            consecutiveMovingSamples++
            consecutiveStoppedSamples = 0
            lastMovementTimestampMs = fix.timestampMillis
            lastValidFix = fix

            if (consecutiveMovingSamples >= sensitivity.requiredMovingSamples) {
                val calibratedSpeed = (effectiveSpeedKmh * calibrationFactor).coerceAtLeast(0f)
                smoothedSpeedKmh = if (smoothedSpeedKmh <= 0.5f) {
                    calibratedSpeed
                } else {
                    smoothedSpeedKmh * 0.35f + calibratedSpeed * 0.65f
                }

                val calibratedDistanceKm = ((rawDistanceMeters * calibrationFactor.toDouble()) / 1000.0)
                    .coerceAtLeast(0.0)
                val stepDurationSec = deltaTimeSec.toLong().coerceIn(1L, 15L)

                ProcessedMotionResult(
                    acceptedFix = true,
                    deltaDistanceKm = calibratedDistanceKm,
                    deltaMovingDurationSeconds = stepDurationSec,
                    currentSpeedKmh = smoothedSpeedKmh,
                    motionStatus = MotionStatus.MOVING,
                    signalQuality = signalQuality,
                    statusReason = "Motor berjalan • Mencatat jarak GPS otomatis"
                )
            } else {
                ProcessedMotionResult(
                    acceptedFix = true,
                    deltaDistanceKm = 0.0,
                    deltaMovingDurationSeconds = 0L,
                    currentSpeedKmh = 0f,
                    motionStatus = MotionStatus.STOPPED,
                    signalQuality = signalQuality,
                    statusReason = "Memvalidasi pergerakan GPS satelit..."
                )
            }
        } else {
            consecutiveStoppedSamples++
            if (consecutiveStoppedSamples >= 2) {
                consecutiveMovingSamples = 0
                smoothedSpeedKmh = 0f
            }
            if (fix.accuracyMeters < previous.accuracyMeters) {
                lastValidFix = fix
            }
            ProcessedMotionResult(
                acceptedFix = false,
                deltaDistanceKm = 0.0,
                deltaMovingDurationSeconds = 0L,
                currentSpeedKmh = 0f,
                motionStatus = MotionStatus.STOPPED,
                signalQuality = signalQuality,
                statusReason = "Motor berhenti (0 KM/JAM) • Anti-GPS drift aktif"
            )
        }
    }

    fun checkWatchdogIdle(nowMillis: Long, idleTimeoutMs: Long = 4500L): Boolean {
        if (lastMovementTimestampMs > 0L && (nowMillis - lastMovementTimestampMs) > idleTimeoutMs) {
            consecutiveMovingSamples = 0
            smoothedSpeedKmh = 0f
            return true
        }
        return false
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6371000.0

        fun haversineDistanceMeters(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double
        ): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val rLat1 = Math.toRadians(lat1)
            val rLat2 = Math.toRadians(lat2)

            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return EARTH_RADIUS_METERS * c
        }
    }
}
