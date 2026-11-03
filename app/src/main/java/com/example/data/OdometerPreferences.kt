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
import com.example.model.SpeedAlertMode
import com.example.model.SpeedUnit
import com.example.model.ThemeMode
import com.example.model.WidgetThemeStyle
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

        // Speed Limit Alert Keys
        val KEY_SPEED_LIMIT_ENABLED = booleanPreferencesKey("speed_limit_enabled")
        val KEY_SPEED_LIMIT_KMH = floatPreferencesKey("speed_limit_kmh")
        val KEY_SPEED_ALERT_MODE = stringPreferencesKey("speed_alert_mode")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_BIKE_NAME = stringPreferencesKey("bike_name")
        val KEY_WIDGET_THEME = stringPreferencesKey("widget_theme")
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

        val alertMode = runCatching {
            SpeedAlertMode.valueOf(prefs[KEY_SPEED_ALERT_MODE] ?: SpeedAlertMode.VIBRATE_AND_SOUND.name)
        }.getOrDefault(SpeedAlertMode.VIBRATE_AND_SOUND)

        val themeMode = runCatching {
            ThemeMode.valueOf(prefs[KEY_THEME_MODE] ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)

        val widgetTheme = runCatching {
            WidgetThemeStyle.valueOf(prefs[KEY_WIDGET_THEME] ?: WidgetThemeStyle.LAVENDER.name)
        }.getOrDefault(WidgetThemeStyle.LAVENDER)

        val rawBikeName = prefs[KEY_BIKE_NAME]?.trim().orEmpty()
        val bikeName = if (rawBikeName.isNotEmpty()) rawBikeName else "Motor Saya"

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
                notificationsEnabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: true,
                isSpeedLimitEnabled = prefs[KEY_SPEED_LIMIT_ENABLED] ?: false,
                speedLimitKmh = (prefs[KEY_SPEED_LIMIT_KMH] ?: 80f).coerceIn(20f, 180f),
                speedAlertMode = alertMode,
                themeMode = themeMode,
                bikeName = bikeName,
                widgetTheme = widgetTheme
            )
        )
    }

    suspend fun getSnapshot(): PersistedOdometerData = persistedDataFlow.first()

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

    suspend fun resetTripOdometer() {
        dataStore.edit { prefs ->
            prefs[KEY_TRIP_ODOMETER_KM] = 0.0
            prefs[KEY_TRIP_DURATION_SECONDS] = 0L
            prefs[KEY_TRIP_MAX_SPEED_KMH] = 0f
        }
    }

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

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun setBikeName(name: String) {
        val cleaned = name.trim().ifEmpty { "Motor Saya" }
        dataStore.edit { prefs ->
            prefs[KEY_BIKE_NAME] = cleaned
        }
    }

    suspend fun setWidgetTheme(theme: WidgetThemeStyle) {
        dataStore.edit { prefs ->
            prefs[KEY_WIDGET_THEME] = theme.name
        }
    }

    suspend fun setSpeedLimitEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SPEED_LIMIT_ENABLED] = enabled
        }
    }

    suspend fun setSpeedLimitKmh(limitKmh: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_SPEED_LIMIT_KMH] = limitKmh.coerceIn(20f, 180f)
        }
    }

    suspend fun setSpeedAlertMode(mode: SpeedAlertMode) {
        dataStore.edit { prefs ->
            prefs[KEY_SPEED_ALERT_MODE] = mode.name
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
