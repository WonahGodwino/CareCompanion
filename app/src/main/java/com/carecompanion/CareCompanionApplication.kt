package com.carecompanion

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.carecompanion.biometric.BiometricManager
import com.carecompanion.data.sync.ReminderWorker
import com.carecompanion.data.sync.SyncWorker
import com.carecompanion.utils.NotificationHelper
import com.carecompanion.utils.SharedPreferencesHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CareCompanionApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var biometricManager: BiometricManager

    companion object {
        private lateinit var instance: CareCompanionApplication
        fun getInstance(): CareCompanionApplication = instance
        fun getAppContext(): Context = instance.applicationContext
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        biometricManager.initialize(applicationContext)
        if (!SharedPreferencesHelper.isFirstLaunch(this)) {
            if (SharedPreferencesHelper.isAutoSyncEnabled(this)) {
                val interval = SharedPreferencesHelper.getSyncInterval(this)
                SyncWorker.schedulePeriodicSync(this, interval.toLong())
            }
            if (SharedPreferencesHelper.areNotificationsEnabled(this)) {
                ReminderWorker.scheduleDailyReminder(this)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Existing: background sync (low importance — no sound/peek)
            manager.createNotificationChannel(
                NotificationChannel(
                    "sync_channel",
                    "Data Sync",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Background patient data synchronisation" }
            )

            // Clinical reminders: daily digest, ART refills, viral load, TPT (default importance)
            manager.createNotificationChannel(
                NotificationChannel(
                    NotificationHelper.CHANNEL_REMINDERS,
                    "Clinical Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Daily patient summary and service-module reminders"
                    enableVibration(false)
                }
            )

            // Clinical alerts: missed appointments, AHD (high importance — shows as heads-up)
            manager.createNotificationChannel(
                NotificationChannel(
                    NotificationHelper.CHANNEL_ALERTS,
                    "Clinical Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Urgent alerts for missed appointments and AHD priority clients"
                    enableVibration(true)
                }
            )
        }
    }
}