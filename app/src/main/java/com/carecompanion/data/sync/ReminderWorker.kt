package com.carecompanion.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.carecompanion.data.repository.PatientRepository
import com.carecompanion.utils.NotificationHelper
import com.carecompanion.utils.SharedPreferencesHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Daily WorkManager worker that fires at 08:00 AM.
 *
 * Immediately functional:
 *   - Posts a daily digest with the total active-client count (real DB data).
 *
 * Service-module reminders are stubbed and commented out.
 * Each stub will be activated once the corresponding ART module is built and
 * its repository provides the required query method.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val patientRepository: PatientRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // Respect the global notification toggle.
        if (!SharedPreferencesHelper.areNotificationsEnabled(applicationContext)) {
            return Result.success()
        }

        return try {
            val patientCount = patientRepository.observeActiveCount().first()

            // ── Daily digest ─────────────────────────────────────────────────
            if (SharedPreferencesHelper.isDailyDigestEnabled(applicationContext) && patientCount > 0) {
                NotificationHelper.postDailyDigest(applicationContext, patientCount)
            }

            // ── ART Refills ─────────────────────────────────────────────────
            // Activate once ART refill due-date data is available:
            // if (SharedPreferencesHelper.isArtRefillsReminderEnabled(applicationContext)) {
            //     val due = artRefillRepository.countDueThisWeek()
            //     if (due > 0) NotificationHelper.postArtRefillReminder(applicationContext, due)
            // }

            // ── Missed Appointments ─────────────────────────────────────────
            // if (SharedPreferencesHelper.isMissedApptsReminderEnabled(applicationContext)) {
            //     val missed = appointmentRepository.countMissed()
            //     if (missed > 0) NotificationHelper.postMissedAppointmentAlert(applicationContext, missed)
            // }

            // ── Viral Load ──────────────────────────────────────────────────
            // if (SharedPreferencesHelper.isViralLoadReminderEnabled(applicationContext)) {
            //     val due = vlRepository.countDue()
            //     if (due > 0) NotificationHelper.postViralLoadReminder(applicationContext, due)
            // }

            // ── TPT ─────────────────────────────────────────────────────────
            // if (SharedPreferencesHelper.isTptReminderEnabled(applicationContext)) {
            //     val eligible = tptRepository.countEligible()
            //     if (eligible > 0) NotificationHelper.postTptReminder(applicationContext, eligible)
            // }

            // ── AHD ─────────────────────────────────────────────────────────
            // if (SharedPreferencesHelper.isAhdAlertEnabled(applicationContext)) {
            //     val ahd = ahdRepository.countFlagged()
            //     if (ahd > 0) NotificationHelper.postAhdAlert(applicationContext, ahd)
            // }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "CareCompanionDailyReminder"

        /**
         * Schedules (or reschedules) the daily reminder to fire at 08:00 AM.
         * Safe to call multiple times — uses [ExistingPeriodicWorkPolicy.UPDATE].
         */
        fun scheduleDailyReminder(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelayMs(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /** Milliseconds until 08:00 AM today; rolls to tomorrow if that time has passed. */
        private fun initialDelayMs(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }
}
