package com.carecompanion.data.network.models

import com.google.gson.annotations.SerializedName

// ── Termii SMS gateway ──────────────────────────────────────────────────────
// POST {base}/api/sms/send

data class TermiiSmsRequest(
    @SerializedName("to")      val to: String,
    @SerializedName("from")    val from: String,
    @SerializedName("sms")     val sms: String,
    @SerializedName("type")    val type: String = "plain",
    @SerializedName("channel") val channel: String = "generic",
    @SerializedName("api_key") val apiKey: String,
)

data class TermiiSmsResponse(
    @SerializedName("message_id") val messageId: String? = null,
    @SerializedName("message")    val message: String? = null,
    @SerializedName("balance")    val balance: Double? = null,
    @SerializedName("code")       val code: String? = null,
)

// ── SendGrid email gateway ──────────────────────────────────────────────────
// POST {base}/v3/mail/send  (Authorization: Bearer <key>)

data class SendGridRequest(
    @SerializedName("personalizations") val personalizations: List<SendGridPersonalization>,
    @SerializedName("from")             val from: SendGridAddress,
    @SerializedName("subject")          val subject: String,
    @SerializedName("content")          val content: List<SendGridContent>,
)

data class SendGridPersonalization(
    @SerializedName("to") val to: List<SendGridAddress>,
)

data class SendGridAddress(
    @SerializedName("email") val email: String,
    @SerializedName("name")  val name: String? = null,
)

data class SendGridContent(
    @SerializedName("type")  val type: String = "text/plain",
    @SerializedName("value") val value: String,
)
