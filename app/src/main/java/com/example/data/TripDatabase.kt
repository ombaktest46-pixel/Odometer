package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.model.GpsAccuracyFilter
import com.example.model.PowerMode
import com.example.model.RoutePoint
import com.example.model.SensitivityLevel
import com.example.model.SpeedUnit
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val distanceKm: Double,
    val durationSeconds: Long,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val startCoordLabel: String,
    val endCoordLabel: String,
    val routePointsJson: String,
    val isSimulated: Boolean = false
)

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startTimeMillis DESC")
    fun getAllTripsFlow(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips ORDER BY startTimeMillis DESC")
    suspend fun getAllTripsOnce(): List<TripEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTrips(trips: List<TripEntity>)

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTripById(tripId: Long)

    @Query("DELETE FROM trips WHERE isSimulated = 1")
    suspend fun deleteSimulatedTrips()

    @Query("DELETE FROM trips")
    suspend fun deleteAllTrips()
}

@Database(entities = [TripEntity::class], version = 1, exportSchema = false)
abstract class MotoOdoDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        @Volatile
        private var INSTANCE: MotoOdoDatabase? = null

        fun getInstance(context: Context): MotoOdoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MotoOdoDatabase::class.java,
                    "moto_odo_trips.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

object RouteJsonCodec {
    fun encodeRoutePoints(points: List<RoutePoint>): String {
        if (points.isEmpty()) return "[]"
        val sampled = if (points.size <= 350) {
            points
        } else {
            val step = points.size.toDouble() / 350.0
            List(350) { idx -> points[(idx * step).toInt().coerceIn(0, points.lastIndex)] }
        }
        return sampled.joinToString(prefix = "[", postfix = "]", separator = ",") { pt ->
            """{"lat":${pt.lat},"lng":${pt.lng},"spd":${pt.speedKmh},"ts":${pt.timestamp}}"""
        }
    }

    fun decodeRoutePoints(json: String): List<RoutePoint> {
        val trimmed = json.trim()
        if (trimmed.isBlank() || trimmed == "[]") return emptyList()
        return runCatching {
            val regex = Regex("""\{"lat":([-0-9.E]+),"lng":([-0-9.E]+),"spd":([-0-9.E]+),"ts":([0-9]+)\}""")
            regex.findAll(trimmed).map { match ->
                RoutePoint(
                    lat = match.groupValues[1].toDouble(),
                    lng = match.groupValues[2].toDouble(),
                    speedKmh = match.groupValues[3].toFloat(),
                    timestamp = match.groupValues[4].toLong()
                )
            }.toList()
        }.getOrDefault(emptyList())
    }
}

class TripRepository(
    private val tripDao: TripDao,
    private val odometerPreferences: OdometerPreferences
) {
    val allTripsFlow: Flow<List<TripEntity>> = tripDao.getAllTripsFlow()

    suspend fun insertTrip(trip: TripEntity): Long = tripDao.insertTrip(trip)

    suspend fun deleteSimulatedTrips() = tripDao.deleteSimulatedTrips()

    /**
     * Deletes a single trip from history. Total Odometer is NEVER reduced.
     */
    suspend fun deleteTripById(id: Long) = tripDao.deleteTripById(id)

    /**
     * Deletes all trip history records. Total Odometer in DataStore remains 100% intact.
     */
    suspend fun deleteAllTripsHistoryOnly() = tripDao.deleteAllTrips()

    suspend fun exportBackupJson(): String {
        val snapshot = odometerPreferences.getSnapshot()
        val trips = tripDao.getAllTripsOnce()

        val root = JSONObject()
        root.put("app", "MOTO_ODO_BACKUP")
        root.put("version", 1)
        root.put("exportedAtMillis", System.currentTimeMillis())
        root.put("totalOdometerKm", snapshot.totalOdometerKm)
        root.put("tripOdometerKm", snapshot.tripOdometerKm)
        root.put("tripDurationSeconds", snapshot.tripDurationSeconds)
        root.put("tripMaxSpeedKmh", snapshot.tripMaxSpeedKmh.toDouble())

        val settingsObj = JSONObject()
        settingsObj.put("calibrationFactor", snapshot.settings.calibrationFactor.toDouble())
        settingsObj.put("sensitivityLevel", snapshot.settings.sensitivityLevel.name)
        settingsObj.put("gpsAccuracyFilter", snapshot.settings.gpsAccuracyFilter.name)
        settingsObj.put("powerMode", snapshot.settings.powerMode.name)
        settingsObj.put("speedUnit", snapshot.settings.speedUnit.name)
        root.put("settings", settingsObj)

        val tripsArray = JSONArray()
        for (trip in trips) {
            val tObj = JSONObject()
            tObj.put("startTimeMillis", trip.startTimeMillis)
            tObj.put("endTimeMillis", trip.endTimeMillis)
            tObj.put("distanceKm", trip.distanceKm)
            tObj.put("durationSeconds", trip.durationSeconds)
            tObj.put("avgSpeedKmh", trip.avgSpeedKmh.toDouble())
            tObj.put("maxSpeedKmh", trip.maxSpeedKmh.toDouble())
            tObj.put("startCoordLabel", trip.startCoordLabel)
            tObj.put("endCoordLabel", trip.endCoordLabel)
            tObj.put("routePointsJson", trip.routePointsJson)
            tObj.put("isSimulated", trip.isSimulated)
            tripsArray.put(tObj)
        }
        root.put("trips", tripsArray)

        return root.toString(2)
    }

    suspend fun importBackupJson(jsonString: String): Result<Int> {
        return runCatching {
            val root = JSONObject(jsonString)
            require(root.optString("app") == "MOTO_ODO_BACKUP") {
                "Format file backup MOTO ODO tidak valid"
            }

            val totalOdo = root.optDouble("totalOdometerKm", 0.0)
            val tripOdo = root.optDouble("tripOdometerKm", 0.0)
            val tripDur = root.optLong("tripDurationSeconds", 0L)
            val tripMax = root.optDouble("tripMaxSpeedKmh", 0.0).toFloat()

            val settingsObj = root.optJSONObject("settings")
            val calibration = settingsObj?.optDouble("calibrationFactor", 1.0)?.toFloat() ?: 1.0f
            val sensitivity = runCatching {
                SensitivityLevel.valueOf(settingsObj?.optString("sensitivityLevel") ?: "MEDIUM")
            }.getOrDefault(SensitivityLevel.MEDIUM)
            val accFilter = runCatching {
                GpsAccuracyFilter.valueOf(settingsObj?.optString("gpsAccuracyFilter") ?: "BALANCED_22M")
            }.getOrDefault(GpsAccuracyFilter.BALANCED_22M)
            val powerMode = runCatching {
                PowerMode.valueOf(settingsObj?.optString("powerMode") ?: "HIGH_PRECISION")
            }.getOrDefault(PowerMode.HIGH_PRECISION)
            val speedUnit = runCatching {
                SpeedUnit.valueOf(settingsObj?.optString("speedUnit") ?: "KMH")
            }.getOrDefault(SpeedUnit.KMH)

            odometerPreferences.restoreFromBackup(
                totalOdometerKm = totalOdo,
                tripOdometerKm = tripOdo,
                tripDurationSeconds = tripDur,
                tripMaxSpeedKmh = tripMax,
                calibrationFactor = calibration,
                sensitivityLevel = sensitivity,
                gpsAccuracyFilter = accFilter,
                powerMode = powerMode,
                speedUnit = speedUnit
            )

            val tripsArray = root.optJSONArray("trips") ?: JSONArray()
            val importedList = ArrayList<TripEntity>(tripsArray.length())
            for (i in 0 until tripsArray.length()) {
                val tObj = tripsArray.getJSONObject(i)
                importedList.add(
                    TripEntity(
                        startTimeMillis = tObj.getLong("startTimeMillis"),
                        endTimeMillis = tObj.getLong("endTimeMillis"),
                        distanceKm = tObj.getDouble("distanceKm"),
                        durationSeconds = tObj.getLong("durationSeconds"),
                        avgSpeedKmh = tObj.getDouble("avgSpeedKmh").toFloat(),
                        maxSpeedKmh = tObj.getDouble("maxSpeedKmh").toFloat(),
                        startCoordLabel = tObj.optString("startCoordLabel", "Rute Tersimpan"),
                        endCoordLabel = tObj.optString("endCoordLabel", "Titik Akhir"),
                        routePointsJson = tObj.optString("routePointsJson", "[]"),
                        isSimulated = tObj.optBoolean("isSimulated", false)
                    )
                )
            }
            if (importedList.isNotEmpty()) {
                tripDao.insertAllTrips(importedList)
            }
            importedList.size
        }
    }
}
