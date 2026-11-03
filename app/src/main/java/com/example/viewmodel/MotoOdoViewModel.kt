package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.OdometerPreferences
import com.example.data.TripEntity
import com.example.data.TripRepository
import com.example.model.GpsAccuracyFilter
import com.example.model.LiveTelemetryState
import com.example.model.PowerMode
import com.example.model.SensitivityLevel
import com.example.model.SettingsState
import com.example.model.SpeedUnit
import com.example.service.AutoTrackingService
import com.example.service.TelemetryStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MotoOdoViewModel(
    private val odometerPreferences: OdometerPreferences,
    private val tripRepository: TripRepository
) : ViewModel() {

    val telemetryState: StateFlow<LiveTelemetryState> = TelemetryStateHolder.telemetryState

    val settingsState: StateFlow<SettingsState> = odometerPreferences.persistedDataFlow
        .combine(TelemetryStateHolder.telemetryState) { persisted, live ->
            persisted.settings.copy(
                isAutoTrackingEnabled = persisted.settings.isAutoTrackingEnabled || live.isAutoTrackingEnabled
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsState()
        )

    val tripsState: StateFlow<List<TripEntity>> = tripRepository.allTripsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val statisticsState: StateFlow<RideStatistics> = combine(
        tripRepository.allTripsFlow,
        odometerPreferences.persistedDataFlow,
        TelemetryStateHolder.telemetryState
    ) { trips, persisted, live ->
        StatisticsCalculator.compute(
            trips = trips,
            permanentTotalOdometerKm = maxOf(persisted.totalOdometerKm, live.totalOdometerKm),
            activeTripDistanceKm = live.activeRideDistanceKm,
            activeTripDurationSeconds = live.activeRideDurationSeconds
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RideStatistics()
    )

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        viewModelScope.launch {
            odometerPreferences.cleanUpSimulationResidueOnce()
            tripRepository.deleteSimulatedTrips()
            odometerPreferences.persistedDataFlow.collect { persisted ->
                TelemetryStateHolder.syncFromPersisted(
                    totalOdoKm = persisted.totalOdometerKm,
                    tripOdoKm = persisted.tripOdometerKm,
                    tripDurationSec = persisted.tripDurationSeconds,
                    tripMaxSpeed = persisted.tripMaxSpeedKmh,
                    isAutoTracking = persisted.settings.isAutoTrackingEnabled
                )
            }
        }
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun clearMessage() {
        _snackbarMessage.value = null
    }

    fun startAutoTracking(context: Context) {
        viewModelScope.launch {
            odometerPreferences.setAutoTrackingEnabled(true)
            AutoTrackingService.startService(context, AutoTrackingService.ACTION_START_AUTO_TRACKING)
            showMessage("Auto Tracking AKTIF • Memantau pergerakan GPS motor otomatis")
        }
    }

    fun stopAutoTracking(context: Context) {
        viewModelScope.launch {
            odometerPreferences.setAutoTrackingEnabled(false)
            AutoTrackingService.stopService(context)
            TelemetryStateHolder.onTrackingStopped()
            showMessage("Auto Tracking DIMATIKAN • Sesi perjalanan disimpan")
        }
    }

    fun resetTripOdometer(context: Context) {
        viewModelScope.launch {
            // Save any active in-memory ride to history before resetting Trip Odometer
            if (telemetryState.value.activeRideDistanceKm >= 0.01) {
                AutoTrackingService.startService(context, AutoTrackingService.ACTION_SAVE_CURRENT_RIDE_NOW)
            }
            odometerPreferences.resetTripOdometer()
            TelemetryStateHolder.onTripReset()
            showMessage("Trip Odometer di-reset ke 0,0 KM (Total Odometer tetap permanen)")
        }
    }

    fun deleteSingleTrip(tripId: Long) {
        viewModelScope.launch {
            tripRepository.deleteTripById(tripId)
            showMessage("Riwayat perjalanan dihapus • Total Odometer permanen tetap utuh")
        }
    }

    fun deleteAllTripsHistory() {
        viewModelScope.launch {
            tripRepository.deleteAllTripsHistoryOnly()
            showMessage("Seluruh riwayat perjalanan dihapus • Total Odometer permanen tidak berubah")
        }
    }

    fun setSensitivityLevel(level: SensitivityLevel) {
        viewModelScope.launch {
            odometerPreferences.setSensitivityLevel(level)
            showMessage("Sensitivitas gerak diatur ke: ${level.title}")
        }
    }

    fun setGpsAccuracyFilter(filter: GpsAccuracyFilter) {
        viewModelScope.launch {
            odometerPreferences.setGpsAccuracyFilter(filter)
            showMessage("Filter akurasi GPS diatur ke: ${filter.title}")
        }
    }

    fun setPowerMode(mode: PowerMode) {
        viewModelScope.launch {
            odometerPreferences.setPowerMode(mode)
            showMessage("Mode daya GPS diatur ke: ${mode.title}")
        }
    }

    fun setSpeedUnit(unit: SpeedUnit) {
        viewModelScope.launch {
            odometerPreferences.setSpeedUnit(unit)
            showMessage("Satuan kecepatan diubah ke ${unit.symbol}")
        }
    }

    fun setCalibrationFactor(factor: Float) {
        viewModelScope.launch {
            odometerPreferences.setCalibrationFactor(factor)
            showMessage("Kalibrasi GPS disimpan: ${(factor * 100).toInt()}%")
        }
    }

    fun setProtectedTotalOdometer(newTotalKm: Double) {
        viewModelScope.launch {
            odometerPreferences.setCalibratedTotalOdometer(newTotalKm)
            showMessage("Total Odometer permanen disesuaikan ke ${String.format("%.1f", newTotalKm)} KM")
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            odometerPreferences.setNotificationsEnabled(enabled)
        }
    }

    fun exportBackupToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = tripRepository.exportBackupJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Tidak dapat menulis ke file tujuan")
                }
            }
            result.onSuccess {
                showMessage("Backup data MOTO ODO berhasil disimpan ke file JSON")
            }.onFailure { err ->
                showMessage("Gagal melakukan backup: ${err.localizedMessage}")
            }
        }
    }

    fun importBackupFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    } ?: error("Tidak dapat membaca file backup")
                    tripRepository.importBackupJson(json).getOrThrow()
                }
            }
            result.onSuccess { count ->
                showMessage("Restore berhasil! Total Odometer & $count riwayat perjalanan dipulihkan")
            }.onFailure { err ->
                showMessage("Gagal melakukan restore: ${err.localizedMessage}")
            }
        }
    }

    class Factory(
        private val odometerPreferences: OdometerPreferences,
        private val tripRepository: TripRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MotoOdoViewModel(odometerPreferences, tripRepository) as T
        }
    }
}
