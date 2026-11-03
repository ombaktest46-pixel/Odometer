package com.example.model

import java.util.Locale
import kotlin.math.log10

enum class MotionStatus(val labelId: String) {
    MOVING("Motor Berjalan"),
    STOPPED("Motor Berhenti"),
    SEARCHING_GPS("Mencari Sinyal GPS"),
    TRACKING_OFF("Auto Tracking Nonaktif")
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
    KMH("km/j", "km", 1.0f),
    MPH("mph", "mi", 0.621371f)
}

enum class SpeedAlertMode(
    val title: String,
    val description: String,
    val enableVibration: Boolean,
    val enableSound: Boolean
) {
    VIBRATE_AND_SOUND(
        title = "Getar & Suara Sirine",
        description = "Getaran taktis ganda disertai nada peringatan",
        enableVibration = true,
        enableSound = true
    ),
    VIBRATE_ONLY(
        title = "Hanya Getar",
        description = "Getaran tanpa suara (cocok saat HP di saku jaket)",
        enableVibration = true,
        enableSound = false
    ),
    SOUND_ONLY(
        title = "Hanya Suara",
        description = "Nada peringatan audio melalui speaker / interkom helm",
        enableVibration = false,
        enableSound = true
    )
}

enum class ThemeMode(val label: String) {
    SYSTEM("Sistem"),
    LIGHT("Terang"),
    DARK("Gelap")
}

enum class WidgetThemeStyle(
    val label: String,
    val subtitle: String,
    val startColorHex: Long,
    val endColorHex: Long,
    val ringColorHex: Long
) {
    LAVENDER(
        label = "Ungu Lavender",
        subtitle = "Lembut & Estetik",
        startColorHex = 0xFFA695F9,
        endColorHex = 0xFF8472E6,
        ringColorHex = 0xFFFFFFFF
    ),
    OCEAN(
        label = "Biru Langit",
        subtitle = "Modern & Bersih",
        startColorHex = 0xFF4F8EF7,
        endColorHex = 0xFF2563EB,
        ringColorHex = 0xFFFFFFFF
    ),
    EMERALD(
        label = "Hijau Mint",
        subtitle = "Segar & Sporty",
        startColorHex = 0xFF2DD4BF,
        endColorHex = 0xFF0D9488,
        ringColorHex = 0xFFFFFFFF
    ),
    MIDNIGHT(
        label = "Hitam Slate",
        subtitle = "Minimalis Gelap",
        startColorHex = 0xFF243044,
        endColorHex = 0xFF111827,
        ringColorHex = 0xFF38BDF8
    ),
    SUNSET(
        label = "Senja Coral",
        subtitle = "Hangat & Kontras",
        startColorHex = 0xFFFB7185,
        endColorHex = 0xFFE11D48,
        ringColorHex = 0xFFFFFFFF
    )
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
    val notificationsEnabled: Boolean = true,
    val isSpeedLimitEnabled: Boolean = false,
    val speedLimitKmh: Float = 80f,
    val speedAlertMode: SpeedAlertMode = SpeedAlertMode.VIBRATE_AND_SOUND,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val bikeName: String = "Motor Saya",
    val widgetTheme: WidgetThemeStyle = WidgetThemeStyle.LAVENDER
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
    val statusDetailMessage: String = "Siap memantau pergerakan motor otomatis",
    val isSpeedLimitEnabled: Boolean = false,
    val speedLimitKmh: Float = 80f,
    val isOverSpeedLimit: Boolean = false
)

object TelemetryFormatter {
    const val MAX_ODOMETER_VALUE = 999_999.0
    private val indoLocale = Locale("id", "ID")

    fun formatOdometer(km: Double, multiplier: Float = 1.0f): String {
        val value = (km * multiplier).coerceIn(0.0, MAX_ODOMETER_VALUE)
        return String.format(indoLocale, "%,.1f", value)
    }

    fun formatOdometerHighPrecision(km: Double, multiplier: Float = 1.0f): String {
        val value = (km * multiplier).coerceIn(0.0, MAX_ODOMETER_VALUE)
        return String.format(indoLocale, "%,.2f", value)
    }

    /**
     * Calculates the circular ring progress (0.0f .. 1.0f) following the Odometer value
     * across the range 1 to 999.999.
     * - When odometer is 0, returns 0f (empty ring).
     * - From 1 up to 999.999, smoothly fills the circular ring proportional to the odometer reading.
     */
    fun calculateOdometerRingProgress(km: Double, multiplier: Float = 1.0f): Float {
        val value = (km * multiplier).coerceIn(0.0, MAX_ODOMETER_VALUE)
        if (value <= 0.0) return 0f
        if (value < 1.0) {
            return (value.toFloat() * 0.04f).coerceIn(0.015f, 0.04f)
        }
        val logProgress = (log10(value + 1.0) / log10(MAX_ODOMETER_VALUE + 1.0)).toFloat()
        val linearProgress = (value / MAX_ODOMETER_VALUE).toFloat()
        return (0.65f * logProgress + 0.35f * linearProgress).coerceIn(0.04f, 1.0f)
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
            else -> "${seconds} dtk"
        }
    }
}
