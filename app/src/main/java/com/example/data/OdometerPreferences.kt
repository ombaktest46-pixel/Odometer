package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.GpsAccuracyFilter
import com.example.model.PowerMode
import com.example.model.SensitivityLevel
import com.example.model.SettingsState
import com.example.model.SpeedUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.odometerDataStore: DataStore<Preferences> by preferencesDataStore(name = "moto_odo_permanent_store")

data class PersistedOdometerData(
    val totalOdometerKm: Double = 0.0,
    val tripOdometerKm: Double = 0.0,
    val tripDurationSeconds: Long = 0L,
    val tripMaxSpeedKmh: Float = 0f,
    val activeSessionDistanceKm: Double = 0.0,
    val activeSessionDurationSeconds: Long = 0L,
    val activeSessionStartMillis: Long = 0L,
    val activeSessionMaxSpeedKmh: Float = 0f,
    val activeSessionRouteJson: String = "[]",
    val settings: SettingsState = SettingsState()
)

class OdometerPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        // Permanent Total Odometer - NEVER reset automatically or when deleting history
        val KEY_TOTAL_ODOMETER_KM = doublePreferencesKey("total_odometer_km")

        // Trip Odometer - Resettable manually by user
        val KEY_TRIP_ODOMETER_KM = doublePreferencesKey("trip_odometer_km")
        val KEY_TRIP_DURATION_SECONDS = longPreferencesKey("trip_duration_seconds")
        val KEY_TRIP_MAX_SPEED_KMH = floatPreferencesKey("trip_max_speed_kmh")

        // Active Auto-Ride Session Recovery (in case OS restarts service mid-ride)
        val KEY_ACTIVE_SESSION_DIST_KM = doublePreferencesKey("active_session_dist_km")
        val KEY_ACTIVE_SESSION_DURATION_SEC = longPreferencesKey("active_session_duration_sec")
        val KEY_ACTIVE_SESSION_START_MS = longPreferencesKey("active_session_start_ms")
        val KEY_ACTIVE_SESSION_MAX_SPEED = floatPreferencesKey("active_session_max_speed")
        val KEY_ACTIVE_SESSION_ROUTE_JSON = stringPreferencesKey("active_session_route_json")

        // Settings Keys
        val KEY_AUTO_TRACKING_ENABLED = booleanPreferencesKey("auto_tracking_enabled")
        val KEY_SENSITIVITY_LEVEL = stringPreferencesKey("sensitivity_level")
        val KEY_GPS_ACCURACY_FILTER = stringPreferencesKey("gps_accuracy_filter")
        val KEY_POWER_MODE = stringPreferencesKey("power_mode")
        val KEY_SPEED_UNIT = stringPreferencesKey("speed_unit")
        val KEY_CALIBRATION_FACTOR = floatPreferencesKey("calibration_factor")
        val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_SIMULATION_CLEANED_V2 = booleanPreferencesKey("simulation_cleaned_v2")
    }

    suspend fun cleanUpSimulationResidueOnce() {
        dataStore.edit { prefs ->
            val alreadyCleaned = prefs[KEY_SIMULATION_CLEANED_V2] ?: false
            if (!alreadyCleaned) {
                prefs[KEY_TOTAL_ODOMETER_KM] = 0.0
                prefs[KEY_TRIP_ODOMETER_KM] = 0.0
                prefs[KEY_TRIP_DURATION_SECONDS] = 0L
                prefs[KEY_TRIP_MAX_SPEED_KMH] = 0f
                prefs[KEY_ACTIVE_SESSION_DIST_KM] = 0.0
                prefs[KEY_ACTIVE_SESSION_DURATION_SEC] = 0L
                prefs[KEY_ACTIVE_SESSION_START_MS] = 0L
                prefs[KEY_ACTIVE_SESSION_MAX_SPEED] = 0f
                prefs[KEY_ACTIVE_SESSION_ROUTE_JSON] = "[]"
                prefs[KEY_SIMULATION_CLEANED_V2] = true
            }
        }
    }

    val persistedDataFlow: Flow<PersistedOdometerData> = dataStore.data.map { prefs ->
        val sensitivity = runCatching {
            SensitivityLevel.valueOf(prefs[KEY_SENSITIVITY_LEVEL] ?: SensitivityLevel.MEDIUM.name)
        }.getOrDefault(SensitivityLevel.MEDIUM)

        val accuracyFilter = runCatching {
            GpsAccuracyFilter.valueOf(prefs[KEY_GPS_ACCURACY_FILTER] ?: GpsAccuracyFilter.BALANCED_22M.name)
        }.getOrDefault(GpsAccuracyFilter.BALANCED_22M)

        val powerMode = runCatching {
            PowerMode.valueOf(prefs[KEY_POWER_MODE] ?: PowerMode.HIGH_PRECISION.name)
        }.getOrDefault(PowerMode.HIGH_PRECISION)

        val speedUnit = runCatching {
            SpeedUnit.valueOf(prefs[KEY_SPEED_UNIT] ?: SpeedUnit.KMH.name)
        }.getOrDefault(SpeedUnit.KMH)

        PersistedOdometerData(
            totalOdometerKm = prefs[KEY_TOTAL_ODOMETER_KM] ?: 0.0,
            tripOdometerKm = prefs[KEY_TRIP_ODOMETER_KM] ?: 0.0,
            tripDurationSeconds = prefs[KEY_TRIP_DURATION_SECONDS] ?: 0L,
            tripMaxSpeedKmh = prefs[KEY_TRIP_MAX_SPEED_KMH] ?: 0f,
            activeSessionDistanceKm = prefs[KEY_ACTIVE_SESSION_DIST_KM] ?: 0.0,
            activeSessionDurationSeconds = prefs[KEY_ACTIVE_SESSION_DURATION_SEC] ?: 0L,
            activeSessionStartMillis = prefs[KEY_ACTIVE_SESSION_START_MS] ?: 0L,
            activeSessionMaxSpeedKmh = prefs[KEY_ACTIVE_SESSION_MAX_SPEED] ?: 0f,
            activeSessionRouteJson = prefs[KEY_ACTIVE_SESSION_ROUTE_JSON] ?: "[]",
            settings = SettingsState(
                isAutoTrackingEnabled = prefs[KEY_AUTO_TRACKING_ENABLED] ?: false,
                sensitivityLevel = sensitivity,
                gpsAccuracyFilter = accuracyFilter,
                powerMode = powerMode,
                speedUnit = speedUnit,
                calibrationFactor = (prefs[KEY_CALIBRATION_FACTOR] ?: 1.0f).coerceIn(0.80f, 1.25f),
                notificationsEnabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: true
            )
        )
    }

    suspend fun getSnapshot(): PersistedOdometerData = persistedDataFlow.first()

    /**
     * Atomically increments both the Permanent Total Odometer and the Trip Odometer.
     */
    suspend fun recordMovementDelta(
        deltaKm: Double,
        deltaDurationSeconds: Long,
        currentSpeedKmh: Float,
        activeSessionDistKm: Double,
        activeSessionDurationSec: Long,
        activeSessionStartMs: Long,
        activeSessionMaxSpeed: Float,
        activeSessionRouteJson: String
    ) {
        if (deltaKm < 0.0) return
        dataStore.edit { prefs ->
            val currentTotal = prefs[KEY_TOTAL_ODOMETER_KM] ?: 0.0
            val currentTrip = prefs[KEY_TRIP_ODOMETER_KM] ?: 0.0
            val currentTripDur = prefs[KEY_TRIP_DURATION_SECONDS] ?: 0L
            val currentTripMax = prefs[KEY_TRIP_MAX_SPEED_KMH] ?: 0f

            prefs[KEY_TOTAL_ODOMETER_KM] = currentTotal + deltaKm
            prefs[KEY_TRIP_ODOMETER_KM] = currentTrip + deltaKm
            prefs[KEY_TRIP_DURATION_SECONDS] = currentTripDur + deltaDurationSeconds.coerceAtLeast(0L)
            if (currentSpeedKmh > currentTripMax) {
                prefs[KEY_TRIP_MAX_SPEED_KMH] = currentSpeedKmh
            }

            prefs[KEY_ACTIVE_SESSION_DIST_KM] = activeSessionDistKm
            prefs[KEY_ACTIVE_SESSION_DURATION_SEC] = activeSessionDurationSec
            prefs[KEY_ACTIVE_SESSION_START_MS] = activeSessionStartMs
            prefs[KEY_ACTIVE_SESSION_MAX_SPEED] = activeSessionMaxSpeed
            prefs[KEY_ACTIVE_SESSION_ROUTE_JSON] = activeSessionRouteJson
        }
    }

    suspend fun clearActiveSessionState() {
        dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_SESSION_DIST_KM] = 0.0
            prefs[KEY_ACTIVE_SESSION_DURATION_SEC] = 0L
            prefs[KEY_ACTIVE_SESSION_START_MS] = 0L
            prefs[KEY_ACTIVE_SESSION_MAX_SPEED] = 0f
            prefs[KEY_ACTIVE_SESSION_ROUTE_JSON] = "[]"
        }
    }

    /**
     * Resets ONLY the Trip Odometer. Permanent Total Odometer is strictly untouched.
     */
    suspend fun resetTripOdometer() {
        dataStore.edit { prefs ->
            prefs[KEY_TRIP_ODOMETER_KM] = 0.0
            prefs[KEY_TRIP_DURATION_SECONDS] = 0L
            prefs[KEY_TRIP_MAX_SPEED_KMH] = 0f
        }
    }

    /**
     * Protected adjustment of the Permanent Total Odometer (e.g. matching physical motorcycle odometer).
     */
    suspend fun setCalibratedTotalOdometer(newTotalKm: Double) {
        val safeTotal = newTotalKm.coerceAtLeast(0.0)
        dataStore.edit { prefs ->
            prefs[KEY_TOTAL_ODOMETER_KM] = safeTotal
        }
    }

    suspend fun setAutoTrackingEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_TRACKING_ENABLED] = enabled
        }
    }

    suspend fun setSensitivityLevel(level: SensitivityLevel) {
        dataStore.edit { prefs ->
            prefs[KEY_SENSITIVITY_LEVEL] = level.name
        }
    }

    suspend fun setGpsAccuracyFilter(filter: GpsAccuracyFilter) {
        dataStore.edit { prefs ->
            prefs[KEY_GPS_ACCURACY_FILTER] = filter.name
        }
    }

    suspend fun setPowerMode(mode: PowerMode) {
        dataStore.edit { prefs ->
            prefs[KEY_POWER_MODE] = mode.name
        }
    }

    suspend fun setSpeedUnit(unit: SpeedUnit) {
        dataStore.edit { prefs ->
            prefs[KEY_SPEED_UNIT] = unit.name
        }
    }

    suspend fun setCalibrationFactor(factor: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_CALIBRATION_FACTOR] = factor.coerceIn(0.80f, 1.25f)
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun restoreFromBackup(
        totalOdometerKm: Double,
        tripOdometerKm: Double,
        tripDurationSeconds: Long,
        tripMaxSpeedKmh: Float,
        calibrationFactor: Float,
        sensitivityLevel: SensitivityLevel,
        gpsAccuracyFilter: GpsAccuracyFilter,
        powerMode: PowerMode,
        speedUnit: SpeedUnit
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_TOTAL_ODOMETER_KM] = totalOdometerKm.coerceAtLeast(0.0)
            prefs[KEY_TRIP_ODOMETER_KM] = tripOdometerKm.coerceAtLeast(0.0)
            prefs[KEY_TRIP_DURATION_SECONDS] = tripDurationSeconds.coerceAtLeast(0L)
            prefs[KEY_TRIP_MAX_SPEED_KMH] = tripMaxSpeedKmh.coerceAtLeast(0f)
            prefs[KEY_CALIBRATION_FACTOR] = calibrationFactor.coerceIn(0.80f, 1.25f)
            prefs[KEY_SENSITIVITY_LEVEL] = sensitivityLevel.name
            prefs[KEY_GPS_ACCURACY_FILTER] = gpsAccuracyFilter.name
            prefs[KEY_POWER_MODE] = powerMode.name
            prefs[KEY_SPEED_UNIT] = speedUnit.name
        }
    }
}
