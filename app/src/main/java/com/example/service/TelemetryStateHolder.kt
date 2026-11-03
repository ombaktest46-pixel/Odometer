package com.example.service

import com.example.model.GpsSignalQuality
import com.example.model.LiveTelemetryState
import com.example.model.MotionStatus
import com.example.model.RoutePoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide reactive bridge between AutoTrackingService (Foreground Location Service)
 * and the Jetpack Compose UI / ViewModel.
 */
object TelemetryStateHolder {

    private val _telemetryState = MutableStateFlow(LiveTelemetryState())
    val telemetryState: StateFlow<LiveTelemetryState> = _telemetryState.asStateFlow()

    fun updateState(transform: (LiveTelemetryState) -> LiveTelemetryState) {
        _telemetryState.update(transform)
    }

    fun syncFromPersisted(
        totalOdoKm: Double,
        tripOdoKm: Double,
        tripDurationSec: Long,
        tripMaxSpeed: Float,
        isAutoTracking: Boolean
    ) {
        _telemetryState.update { current ->
            val avgSpd = if (tripDurationSec > 0L) {
                ((tripOdoKm / (tripDurationSec / 3600.0))).toFloat().coerceIn(0f, 220f)
            } else {
                0f
            }
            current.copy(
                totalOdometerKm = totalOdoKm,
                tripOdometerKm = tripOdoKm,
                tripDurationSeconds = tripDurationSec,
                maxSpeedKmh = maxOf(current.maxSpeedKmh, tripMaxSpeed),
                avgSpeedKmh = avgSpd,
                isAutoTrackingEnabled = isAutoTracking,
                motionStatus = if (!isAutoTracking && !current.isSimulationActive) {
                    MotionStatus.TRACKING_OFF
                } else if (current.motionStatus == MotionStatus.TRACKING_OFF) {
                    MotionStatus.SEARCHING_GPS
                } else {
                    current.motionStatus
                }
            )
        }
    }

    fun onTrackingStopped() {
        _telemetryState.update { current ->
            current.copy(
                isAutoTrackingEnabled = false,
                isSimulationActive = false,
                currentSpeedKmh = 0f,
                activeRideDistanceKm = 0.0,
                activeRideDurationSeconds = 0L,
                motionStatus = MotionStatus.TRACKING_OFF,
                gpsSignalQuality = GpsSignalQuality.NO_SIGNAL,
                currentRoutePoints = emptyList(),
                statusDetailMessage = "Auto Tracking dimatikan"
            )
        }
    }

    fun onTripReset() {
        _telemetryState.update { current ->
            current.copy(
                tripOdometerKm = 0.0,
                tripDurationSeconds = 0L,
                avgSpeedKmh = 0f,
                maxSpeedKmh = 0f,
                activeRideDistanceKm = 0.0,
                activeRideDurationSeconds = 0L,
                currentRoutePoints = emptyList()
            )
        }
    }
}
