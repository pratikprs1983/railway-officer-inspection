package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.FirebaseSyncManager

class MyApplication : Application() {
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getDatabase(this)
        try {
            com.example.util.NotificationService.initChannels(this)
        } catch (e: Exception) {
            android.util.Log.w("MyApplication", "Notification channels init error: ${e.message}")
        }
        try {
            FirebaseSyncManager.initialize()
        } catch (e: Exception) {
            android.util.Log.w("MyApplication", "Firebase sync initialization skipped: ${e.message}")
        }
    }

    companion object {
        lateinit var instance: MyApplication
            private set
    }
}
