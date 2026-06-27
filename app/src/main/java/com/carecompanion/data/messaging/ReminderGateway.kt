package com.carecompanion.data.messaging

import com.carecompanion.data.network.MessagingApiService
import com.carecompanion.data.network.models.SendGridAddress
import com.carecompanion.data.network.models.SendGridContent
import com.carecompanion.data.network.models.SendGridPersonalization
import com.carecompanion.data.network.models.SendGridRequest
import com.carecompanion.data.network.models.TermiiSmsRequest
import com.carecompanion.utils.SharedPreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Where a reminder should be delivered. */
data class ReminderTarget(
    val displayName: String,
    val phoneNumber: String?,
    val email: String?,
)

sealed interface ReminderResult {
    data class Sent(val channels: List<String>) : ReminderResult           // e.g. ["SMS", "Email"]
    data class Failed(val reason: String) : ReminderResult
    object NotConfigured : ReminderResult                                   // no enabled channel
    object NoContact : ReminderResult                                       // no phone/email on file
}

interface ReminderGateway {
    /** Send a neutral, confidentiality-safe appointment reminder via configured channels. */
    suspend fun sendAppointmentReminder(target: ReminderTarget, messageBody: String): ReminderResult
}

@Singleton
class ReminderGatewayImpl @Inject constructor(
    private val api: MessagingApiService,
) : ReminderGateway {

    override suspend fun sendAppointmentReminder(
        target: ReminderTarget,
        messageBody: String,
    ): ReminderResult = withContext(Dispatchers.IO) {
        val smsOn = SharedPreferencesHelper.isSmsReminderEnabled() && !SharedPreferencesHelper.getTermiiApiKey().isNullOrBlank()
        val emailOn = SharedPreferencesHelper.isEmailReminderEnabled() &&
            !SharedPreferencesHelper.getSendGridApiKey().isNullOrBlank() &&
            !SharedPreferencesHelper.getReminderEmailFrom().isNullOrBlank()

        if (!smsOn && !emailOn) return@withContext ReminderResult.NotConfigured

        val hasPhone = !target.phoneNumber.isNullOrBlank()
        val hasEmail = !target.email.isNullOrBlank()
        if ((smsOn && !hasPhone) && (emailOn && !hasEmail)) return@withContext ReminderResult.NoContact
        if (!smsOn && emailOn && !hasEmail) return@withContext ReminderResult.NoContact
        if (smsOn && !emailOn && !hasPhone) return@withContext ReminderResult.NoContact

        val sent = mutableListOf<String>()
        val errors = mutableListOf<String>()

        if (smsOn && hasPhone) {
            runCatching { sendSms(target.phoneNumber!!, messageBody) }
                .onSuccess { ok -> if (ok) sent += "SMS" else errors += "SMS rejected by gateway" }
                .onFailure { errors += "SMS: ${it.message ?: "network error"}" }
        }
        if (emailOn && hasEmail) {
            runCatching { sendEmail(target.email!!, target.displayName, messageBody) }
                .onSuccess { ok -> if (ok) sent += "Email" else errors += "Email rejected by gateway" }
                .onFailure { errors += "Email: ${it.message ?: "network error"}" }
        }

        when {
            sent.isNotEmpty() -> ReminderResult.Sent(sent)
            errors.isNotEmpty() -> ReminderResult.Failed(errors.joinToString("; "))
            else -> ReminderResult.NoContact
        }
    }

    private suspend fun sendSms(phone: String, body: String): Boolean {
        val base = SharedPreferencesHelper.getTermiiBaseUrl().trimEnd('/')
        val resp = api.sendTermiiSms(
            url = "$base/api/sms/send",
            body = TermiiSmsRequest(
                to = normalizeMsisdn(phone),
                from = SharedPreferencesHelper.getTermiiSenderId(),
                sms = body,
                apiKey = SharedPreferencesHelper.getTermiiApiKey().orEmpty(),
            ),
        )
        // Termii returns 200 with a message_id on success.
        return resp.isSuccessful && resp.body()?.messageId != null
    }

    private suspend fun sendEmail(email: String, name: String, body: String): Boolean {
        val base = SharedPreferencesHelper.getSendGridBaseUrl().trimEnd('/')
        val from = SharedPreferencesHelper.getReminderEmailFrom().orEmpty()
        val resp = api.sendSendGridEmail(
            url = "$base/v3/mail/send",
            authorization = "Bearer ${SharedPreferencesHelper.getSendGridApiKey().orEmpty()}",
            body = SendGridRequest(
                personalizations = listOf(SendGridPersonalization(to = listOf(SendGridAddress(email, name)))),
                from = SendGridAddress(from, "CareCompanion Clinic"),
                subject = "Clinic appointment reminder",
                content = listOf(SendGridContent(value = body)),
            ),
        )
        return resp.isSuccessful // SendGrid returns 202 Accepted
    }

    /** Normalise Nigerian numbers to MSISDN (234XXXXXXXXXX) expected by the gateway. */
    private fun normalizeMsisdn(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.startsWith("234") -> digits
            digits.startsWith("0")   -> "234" + digits.drop(1)
            digits.length == 10      -> "234$digits"
            else                      -> digits
        }
    }
}
