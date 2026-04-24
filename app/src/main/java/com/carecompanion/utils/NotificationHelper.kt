package com.carecompanion.utils

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.carecompanion.MainActivity

/**
 * Central helper for building and posting all CareCompanion notifications.
 *
 * Channel IDs are referenced by [CareCompanionApplication] when creating channels.
 * All post* methods are safe to call from WorkManager background threads.
 */
object NotificationHelper {

    // ── Channel IDs ───────────────────────────────────────────────────────────
    /** Low-importance channel: daily digest and routine service reminders. */
    const val CHANNEL_REMINDERS = "clinical_reminders"

    /** High-importance channel: urgent clinical alerts (missed appointments, AHD). */
    const val CHANNEL_ALERTS = "clinical_alerts"

    // ── Stable notification IDs ───────────────────────────────────────────────
    const val NOTIF_DAILY_DIGEST  = 2001
    const val NOTIF_ART_REFILLS   = 2002
    const val NOTIF_MISSED_APPTS  = 2003
    const val NOTIF_VIRAL_LOAD    = 2004
    const val NOTIF_TPT           = 2005
    const val NOTIF_AHD           = 2006

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun nm(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** PendingIntent that brings the user back to MainActivity. */
    private fun launchIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ── Public post helpers ───────────────────────────────────────────────────

    /**
     * Daily digest — fires every morning with the total active-client count.
     * This is immediately usable since patient count is available from local DB.
     */
    fun postDailyDigest(context: Context, patientCount: Int) {
        nm(context).notify(
            NOTIF_DAILY_DIGEST,
            NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("CareCompanion — Daily Summary")
                .setContentText("$patientCount active clients on ART. Tap to review today's tasks.")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "$patientCount active clients on ART. " +
                        "Review ART refill due dates, missed appointments and viral load " +
                        "status for your facility."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(launchIntent(context))
                .build()
        )
    }

    /**
     * ART refill reminder — will be driven by real due-date data once the
     * ART Refills module is integrated.
     */
    fun postArtRefillReminder(context: Context, dueCount: Int) {
        nm(context).notify(
            NOTIF_ART_REFILLS,
            NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("ART Refills Due")
                .setContentText("$dueCount ${if (dueCount == 1) "client has" else "clients have"} ART refills due this week.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(launchIntent(context))
                .build()
        )
    }

    /**
     * Missed-appointment alert — high importance; will be driven by appointment
     * data once the Missed Appointments module is integrated.
     */
    fun postMissedAppointmentAlert(context: Context, missedCount: Int) {
        nm(context).notify(
            NOTIF_MISSED_APPTS,
            NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Missed Appointments — Action Required")
                .setContentText(
                    "$missedCount ${if (missedCount == 1) "client" else "clients"} " +
                    "missed their scheduled appointment."
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(launchIntent(context))
                .build()
        )
    }

    /**
     * Viral load reminder — will be driven by VL due-date data once the
     * Viral Load module is integrated.
     */
    fun postViralLoadReminder(context: Context, dueCount: Int) {
        nm(context).notify(
            NOTIF_VIRAL_LOAD,
            NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Viral Load Samples Due")
                .setContentText(
                    "$dueCount ${if (dueCount == 1) "client is" else "clients are"} " +
                    "due for viral load sample collection."
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(launchIntent(context))
                .build()
        )
    }

    /**
     * TPT reminder — will be driven by TPT tracking data once the TPT module
     * is integrated.
     */
    fun postTptReminder(context: Context, eligibleCount: Int) {
        nm(context).notify(
            NOTIF_TPT,
            NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("TPT Follow-up Required")
                .setContentText(
                    "$eligibleCount ${if (eligibleCount == 1) "client requires" else "clients require"} " +
                    "TPT status review."
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(launchIntent(context))
                .build()
        )
    }

    /**
     * AHD alert — high importance; will be driven by AHD flagging logic once
     * the AHD module is integrated.
     */
    fun postAhdAlert(context: Context, ahdCount: Int) {
        nm(context).notify(
            NOTIF_AHD,
            NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("AHD — Priority Clients")
                .setContentText(
                    "$ahdCount ${if (ahdCount == 1) "client" else "clients"} " +
                    "flagged for Advanced HIV Disease review."
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(launchIntent(context))
                .build()
        )
    }

    /** Cancel all CareCompanion-posted notifications. */
    fun cancelAll(context: Context) {
        nm(context).cancelAll()
    }
}
