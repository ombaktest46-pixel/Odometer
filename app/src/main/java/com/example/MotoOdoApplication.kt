package com.example

import android.app.Application
import com.example.data.MotoOdoDatabase
import com.example.data.OdometerPreferences
import com.example.data.TripRepository
import com.example.data.odometerDataStore

class MotoOdoApplication : Application() {

    lateinit var odometerPreferences: OdometerPreferences
        private set

    lateinit var database: MotoOdoDatabase
        private set

    lateinit var tripRepository: TripRepository
        private set

    override fun onCreate() {
        super.onCreate()
        odometerPreferences = OdometerPreferences(applicationContext.odometerDataStore)
        database = MotoOdoDatabase.getInstance(this)
        tripRepository = TripRepository(database.tripDao(), odometerPreferences)
    }
}
