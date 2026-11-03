package com.example.model

import java.util.Locale

enum class MotionStatus(val labelId: String) {
    MOVING("MOTOR BERJALAN"),
    STOPPED("MOTOR BERHENTI"),
    SEARCHING_GPS("MENCARI SINYAL GPS"),
    TRACKING_OFF("AUTO TRACKING NONAKTIF")
}

enum class GpsSignalQuality(val label: String, val bars: Int) {
    EXCELLENT("Sangat Akurat", 4),
    GOOD("Akurat", 3),
    FAIR("Cukup", 2),
    WEAK("Lemah", 1),
    NO_SIGNAL("Tanpa Sinyal", 0),
    SIMULATED("Simulasi Uji", 4)
}

enum class SensitivityLevel(
    val title: String,
    val description: String,
    val minSpeedKmh: Float,
    val minStepDistanceMeters: Float,
    val requiredMovingSamples: Int
) {
    HIGH(
        title = "Tinggi (Responsif)",
        description = "Mendeteksi gerakan mulai 2.5 km/jam (cocok untuk macet padat)",
        minSpeedKmh = 2.5f,
        minStepDistanceMeters = 2.2f,
        requiredMovingSamples = 2
    ),
    MEDIUM(
        title = "Sedang (Rekomendasi)",
        description = "Mendeteksi gerakan mulai 3.8 km/jam, seimbang cegah GPS drift",
        minSpeedKmh = 3.8f,
        minStepDistanceMeters = 3.5f,
        requiredMovingSamples = 2
    ),
    LOW(
        title = "Rendah (Anti-Drift Ketat)",
        description = "Mendeteksi gerakan mulai 5.5 km/jam (area gedung tinggi/parkir basement)",
        minSpeedKmh = 5.5f,
        minStepDistanceMeters = 5.5f,
        requiredMovingSamples = 3
    )
}

enum class GpsAccuracyFilter(
    val title: String,
    val description: String,
    val maxAccuracyMeters: Float
) {
    STRICT_12M("Ketat (≤ 12m)", "Hanya menerima sinyal GPS kualitas sangat tinggi", 12f),
    BALANCED_22M("Seimbang (≤ 22m)", "Standar optimal untuk jalan raya & perkotaan", 22f),
    RELAXED_35M("Longgar (≤ 35m)", "Tetap mencatat saat cuaca mendung atau jalan tertutup pohon", 35f)
}

enum class PowerMode(
    val title: String,
    val description: String,
    val intervalMs: Long,
    val minUpdateIntervalMs: Long
) {
    HIGH_PRECISION("Presisi Tinggi (1 Detik)", "Update speedometer & odometer setiap 1 detik", 1000L, 500L),
    BALANCED("Seimbang (2 Detik)", "Optimal antara akurasi tinggi dan konsumsi baterai", 2000L, 1000L),
    BATTERY_SAVER("Hemat Baterai (4 Detik)", "Untuk perjalanan touring jarak jauh berjam-jam", 4000L, 2000L)
}

enum class SpeedUnit(val symbol: String, val distanceSymbol: String, val multiplier: Float) {
    KMH("KM/JAM", "KM", 1.0f),
    MPH("MPH", "MI", 0.621371f)
}

data class RoutePoint(
    val lat: Double,
    val lng: Double,
    val speedKmh: Float,
    val timestamp: Long
)

data class SettingsState(
    val isAutoTrackingEnabled: Boolean = false,
    val sensitivityLevel: SensitivityLevel = SensitivityLevel.MEDIUM,
    val gpsAccuracyFilter: GpsAccuracyFilter = GpsAccuracyFilter.BALANCED_22M,
    val powerMode: PowerMode = PowerMode.HIGH_PRECISION,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val calibrationFactor: Float = 1.00f,
    val notificationsEnabled: Boolean = true
)

data class LiveTelemetryState(
    val isAutoTrackingEnabled: Boolean = false,
    val isSimulationActive: Boolean = false,
    val currentSpeedKmh: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val totalOdometerKm: Double = 0.0,
    val tripOdometerKm: Double = 0.0,
    val tripDurationSeconds: Long = 0L,
    val activeRideDistanceKm: Double = 0.0,
    val activeRideDurationSeconds: Long = 0L,
    val motionStatus: MotionStatus = MotionStatus.TRACKING_OFF,
    val gpsSignalQuality: GpsSignalQuality = GpsSignalQuality.NO_SIGNAL,
    val gpsAccuracyMeters: Float = 0f,
    val isGpsHardwareEnabled: Boolean = true,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val currentRoutePoints: List<RoutePoint> = emptyList(),
    val statusDetailMessage: String = "Siap memantau pergerakan motor otomatis"
)

object TelemetryFormatter {
    private val indoLocale = Locale("id", "ID")

    fun formatOdometer(km: Double, multiplier: Float = 1.0f): String {
        val value = (km * multiplier).coerceAtLeast(0.0)
        return String.format(indoLocale, "%,.1f", value)
    }

    fun formatOdometerHighPrecision(km: Double, multiplier: Float = 1.0f): String {
        val value = (km * multiplier).coerceAtLeast(0.0)
        return String.format(indoLocale, "%,.2f", value)
    }

    fun formatSpeedInt(speedKmh: Float, multiplier: Float = 1.0f): String {
        val value = (speedKmh * multiplier).coerceAtLeast(0f)
        return String.format(indoLocale, "%.0f", value)
    }

    fun formatSpeedOneDecimal(speedKmh: Float, multiplier: Float = 1.0f): String {
        val value = (speedKmh * multiplier).coerceAtLeast(0f)
        return String.format(indoLocale, "%.1f", value)
    }

    fun formatDurationHms(totalSeconds: Long): String {
        val safeSec = totalSeconds.coerceAtLeast(0L)
        val hours = safeSec / 3600
        val minutes = (safeSec % 3600) / 60
        val seconds = safeSec % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatDurationReadable(totalSeconds: Long): String {
        val safeSec = totalSeconds.coerceAtLeast(0L)
        val hours = safeSec / 3600
        val minutes = (safeSec % 3600) / 60
        val seconds = safeSec % 60
        return when {
            hours > 0 -> "${hours}j ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}d"
            else -> "${seconds} detik"
        }
    }
}
