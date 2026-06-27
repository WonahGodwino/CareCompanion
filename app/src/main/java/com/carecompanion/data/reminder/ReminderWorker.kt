package com.carecompanion.data.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.carecompanion.data.messaging.ReminderGateway
import com.carecompanion.data.messaging.ReminderResult
import com.carecompanion.data.messaging.ReminderTemplates
import com.carecompanion.data.risk.AssessedClient
import com.carecompanion.data.risk.RiskAssessmentService
import com.carecompanion.utils.SharedPreferencesHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Background job that automatically sends appointment reminders to clients the AI
 * monitoring engine has flagged (forecast / approaching / established tiers), using
 * each tier's saved message template. De-duplicates so a client is texted at most
 * once per calendar day. Self-reschedules to honour the configured interval.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val riskService: RiskAssessmentService,
    private val reminderGateway: ReminderGateway,
    private val auditLogger: ReminderAuditLogger,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!SharedPreferencesHelper.isAutoReminderEnabled()) return Result.success()

        val today = ReminderScheduler.todayEpochDay()
        var sent = 0
        var failed = 0

        val audience = ReminderAudience.fromName(SharedPreferencesHelper.getAutoReminderAudience())

        val result = runCatching {
            val assessment = riskService.assessOnce()
            // The AI decides who is due today within the chosen audience — pre-appointment
            // cadence (risk-adaptive), in-window daily chase, and weekly post-IIT nudge.
            for (client in ReminderScheduler.candidates(assessment, audience)) {
                val lastSent = SharedPreferencesHelper.getReminderLastSentEpochDay(client.score.uuid)
                if (!ReminderScheduler.isDueToday(client, lastSent, today)) continue
                if (deliver(client)) {
                    SharedPreferencesHelper.setReminderLastSentEpochDay(client.score.uuid, today)
                    sent++
                } else {
                    failed++
                }
            }
        }

        maybeScheduleNext(applicationContext)

        return when {
            result.isFailure -> if (runAttemptCount < 3) Result.retry()
                else Result.failure(workDataOf("error" to (result.exceptionOrNull()?.message ?: "unknown")))
            else -> Result.success(workDataOf("sent" to sent, "failed" to failed))
        }
    }

    private suspend fun deliver(client: AssessedClient): Boolean {
        val message = ReminderTemplates.render(client.type, client.context)
        val result = reminderGateway.sendAppointmentReminder(client.target, message)
        auditLogger.record(client, result, auto = true)
        return result is ReminderResult.Sent
    }

    private fun maybeScheduleNext(context: Context) {
        if (!SharedPreferencesHelper.isAutoReminderEnabled()) return
        scheduleNext(context, SharedPreferencesHelper.getAutoReminderIntervalHours().toLong())
    }

    companion object {
        const val WORK_NAME = "CareCompanionReminders"

        private fun buildRequest(delayHours: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .setInitialDelay(delayHours.coerceAtLeast(1), TimeUnit.HOURS)
                .build()

        /** Schedule the next run (chained one-time work, like SyncWorker). */
        fun scheduleNext(context: Context, delayHours: Long = 24) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME, ExistingWorkPolicy.REPLACE, buildRequest(delayHours),
            )
        }

        /** Enable + kick off the first run shortly after the user opts in. */
        fun enable(context: Context, intervalHours: Long) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setInitialDelay(2, TimeUnit.MINUTES)
                    .build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
