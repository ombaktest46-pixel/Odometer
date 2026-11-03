package com.example.service

import com.example.model.GpsAccuracyFilter
import com.example.model.GpsSignalQuality
import com.example.model.MotionStatus
import com.example.model.SensitivityLevel
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
 * Pure, testable GPS movement & anti-drift processor for MOTO ODO.
 * Prevents GPS drift while parked and automatically detects movement start, pause, and resume.
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

    fun evaluateSignalQuality(accuracyMeters: Float, isSimulated: Boolean): GpsSignalQuality {
        if (isSimulated) return GpsSignalQuality.SIMULATED
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
        calibrationFactor: Float
    ): ProcessedMotionResult {
        val signalQuality = evaluateSignalQuality(fix.accuracyMeters, fix.isSimulated)

        // 1. Filter out poor GPS accuracy
        if (!fix.isSimulated && (fix.accuracyMeters <= 0f || fix.accuracyMeters > accuracyFilter.maxAccuracyMeters)) {
            consecutiveStoppedSamples++
            if (consecutiveStoppedSamples >= 2) {
                smoothedSpeedKmh = 0f
            }
            return ProcessedMotionResult(
                acceptedFix = false,
                deltaDistanceKm = 0.0,
                deltaMovingDurationSeconds = 0L,
                currentSpeedKmh = 0f,
                motionStatus = if (lastValidFix == null) MotionStatus.SEARCHING_GPS else MotionStatus.STOPPED,
                signalQuality = signalQuality,
                statusReason = "Akurasi GPS rendah (±${fix.accuracyMeters.toInt()}m), menunggu sinyal stabil"
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
                statusReason = "Sinyal GPS terkunci (±${fix.accuracyMeters.toInt()}m) • Siap mendeteksi gerak"
            )
        }

        // 2. Avoid duplicate or out-of-order timestamps
        val deltaTimeMs = fix.timestampMillis - previous.timestampMillis
        if (deltaTimeMs <= 150L) {
            return ProcessedMotionResult(
                acceptedFix = false,
                deltaDistanceKm = 0.0,
                deltaMovingDurationSeconds = 0L,
                currentSpeedKmh = if (consecutiveMovingSamples >= sensitivity.requiredMovingSamples) smoothedSpeedKmh else 0f,
                motionStatus = if (consecutiveMovingSamples >= sensitivity.requiredMovingSamples) MotionStatus.MOVING else MotionStatus.STOPPED,
                signalQuality = signalQuality,
                statusReason = "Mengabaikan duplikasi pembacaan lokasi"
            )
        }

        val deltaTimeSec = deltaTimeMs / 1000.0
        val rawDistanceMeters = haversineDistanceMeters(
            previous.latitude,
            previous.longitude,
            fix.latitude,
            fix.longitude
        )

        // 3. Calculate coordinate speed and combine with Doppler GPS speed
        val coordSpeedKmh = ((rawDistanceMeters / deltaTimeSec) * 3.6).toFloat()
        val dopplerSpeedKmh = if (fix.hasSpeed && fix.speedMetersPerSec >= 0f) {
            fix.speedMetersPerSec * 3.6f
        } else {
            coordSpeedKmh
        }

        // 4. Filter impossible GPS spikes / teleportation (> 220 km/h or sudden huge jump with 0 Doppler speed)
        if (coordSpeedKmh > 220f || (rawDistanceMeters > 250.0 && dopplerSpeedKmh < 3.0f && !fix.isSimulated)) {
            // Re-anchor reference if time gap was long (> 25s), otherwise ignore spike
            if (deltaTimeSec > 25.0) {
                lastValidFix = fix
            }
            return ProcessedMotionResult(
                acceptedFix = false,
                deltaDistanceKm = 0.0,
                deltaMovingDurationSeconds = 0L,
                currentSpeedKmh = 0f,
                motionStatus = MotionStatus.STOPPED,
                signalQuality = signalQuality,
                statusReason = "Lonjakan GPS diabaikan (Anti-Spike Filter)"
            )
        }

        // Effective speed combines Doppler and coordinate velocity to avoid stationary drift
        val effectiveSpeedKmh = if (fix.hasSpeed) {
            // If Doppler says almost zero (< 1.8 km/h), trust Doppler strongly to block multipath drift
            if (dopplerSpeedKmh < 1.8f) {
                dopplerSpeedKmh * 0.7f + coordSpeedKmh * 0.3f
            } else {
                dopplerSpeedKmh * 0.65f + coordSpeedKmh * 0.35f
            }
        } else {
            coordSpeedKmh
        }

        // Anti-drift radius check: displacement must exceed both sensitivity step AND a fraction of GPS error radius
        val driftThresholdMeters = if (fix.isSimulated) {
            sensitivity.minStepDistanceMeters.toDouble()
        } else {
            max(
                sensitivity.minStepDistanceMeters.toDouble(),
                (fix.accuracyMeters * 0.28f).toDouble()
            )
        }

        val isSampleMoving = effectiveSpeedKmh >= sensitivity.minSpeedKmh &&
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
                    statusReason = "Motor bergerak • Mencatat jarak otomatis"
                )
            } else {
                // First moving sample: anchor position and wait for confirmation sample
                ProcessedMotionResult(
                    acceptedFix = true,
                    deltaDistanceKm = 0.0,
                    deltaMovingDurationSeconds = 0L,
                    currentSpeedKmh = 0f,
                    motionStatus = MotionStatus.STOPPED,
                    signalQuality = signalQuality,
                    statusReason = "Memvalidasi awal pergerakan motor..."
                )
            }
        } else {
            consecutiveStoppedSamples++
            if (consecutiveStoppedSamples >= 2) {
                consecutiveMovingSamples = 0
                smoothedSpeedKmh = 0f
            }
            // Only update anchor if accuracy improved so slow walking/drift doesn't creep
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
                statusReason = "Motor berhenti • Anti-GPS drift aktif"
            )
        }
    }

    /**
     * Called periodically by watchdog timer in case GPS callbacks pause when completely still.
     */
    fun checkWatchdogIdle(nowMillis: Long, idleTimeoutMs: Long = 5500L): Boolean {
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
