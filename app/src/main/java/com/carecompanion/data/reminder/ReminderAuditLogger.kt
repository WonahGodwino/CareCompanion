package com.carecompanion.data.reminder

import com.carecompanion.data.database.dao.ReminderLogDao
import com.carecompanion.data.database.entities.ReminderLog
import com.carecompanion.data.messaging.ReminderResult
import com.carecompanion.data.risk.AssessedClient
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/** Writes one [ReminderLog] row per real send attempt (manual or automatic). */
@Singleton
class ReminderAuditLogger @Inject constructor(
    private val dao: ReminderLogDao,
) {
    suspend fun record(client: AssessedClient, result: ReminderResult, auto: Boolean) {
        // A missing-gateway result is a configuration state, not a send attempt — don't log it.
        if (result is ReminderResult.NotConfigured) return

        val (channels, success, detail) = when (result) {
            is ReminderResult.Sent -> Triple(result.channels.joinToString(" & "), true, null)
            is ReminderResult.Failed -> Triple("", false, result.reason)
            ReminderResult.NoContact -> Triple("", false, "No phone/email on file")
            ReminderResult.NotConfigured -> return
        }

        dao.insert(
            ReminderLog(
                personUuid = client.score.uuid,
                hospitalNumber = client.score.hospitalNumber,
                displayName = client.score.displayName,
                reminderType = client.type.name,
                channels = channels,
                success = success,
                detail = detail,
                riskScore = client.score.score,
                riskBand = client.score.band.name,
                appointmentDate = client.context.appointmentDate,
                sentAt = Date(),
                auto = auto,
            )
        )
    }
}
