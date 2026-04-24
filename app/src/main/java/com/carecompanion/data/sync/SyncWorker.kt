package com.carecompanion.data.sync

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.carecompanion.data.repository.SyncRepository
import com.carecompanion.data.repository.SyncResult
import com.carecompanion.utils.SharedPreferencesHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        showSyncNotification()
        val result = when (val result = syncRepository.syncAll()) {
            is SyncResult.Success -> {
                cancelSyncNotification()
                Result.success(
                    workDataOf(
                        "patients_added" to result.patientsAdded,
                        "biometrics_added" to result.biometricsAdded
                    )
                )
            }
            is SyncResult.Error -> {
                cancelSyncNotification()
                if (runAttemptCount < 3) Result.retry() else Result.failure(workDataOf("error" to result.message))
            }
            is SyncResult.NoNetwork -> {
                cancelSyncNotification()
                Result.retry()
            }
            is SyncResult.NotConfigured -> {
                cancelSyncNotification()
                Result.failure(workDataOf("error" to "App not configured"))
            }
        }

        maybeScheduleNextAutoSync(applicationContext)
        return result
    }

    private fun maybeScheduleNextAutoSync(context: Context) {
        if (!SharedPreferencesHelper.isAutoSyncEnabled(context)) return
        val interval = SharedPreferencesHelper.getSyncInterval(context).coerceAtLeast(5).toLong()
        scheduleNextSync(context, interval)
    }

    private fun showSyncNotification() {
        val notification = NotificationCompat.Builder(applicationContext, "sync_channel")
            .setContentTitle("Syncing patient data...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .build()
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun cancelSyncNotification() {
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(NOTIFICATION_ID)
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val WORK_NAME = "CareCompanionSync"

        private fun buildOneTimeRequest(delayMinutes: Long): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()
        }

        fun schedulePeriodicSync(context: Context, intervalMinutes: Long = 5) {
            scheduleNextSync(context, intervalMinutes)
        }

        fun scheduleNextSync(context: Context, delayMinutes: Long = 5): androidx.work.Operation {
            val request = buildOneTimeRequest(delayMinutes.coerceAtLeast(5))
            return WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun runOnceNow(context: Context): androidx.work.Operation {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            return WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME + "_now", ExistingWorkPolicy.REPLACE, request)
        }

        fun cancelSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}