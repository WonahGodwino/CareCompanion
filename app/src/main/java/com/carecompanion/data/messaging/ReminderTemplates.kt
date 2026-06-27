package com.carecompanion.data.messaging

import com.carecompanion.utils.DateUtils
import com.carecompanion.utils.SharedPreferencesHelper
import java.util.Date

/**
 * The reminder categories the AI monitoring engine produces. Each maps to one of the
 * predictive tiers and carries a default, confidentiality-safe message that a clinic
 * can override with its own saved template.
 */
enum class ReminderType(
    val key: String,
    val label: String,
    val defaultTemplate: String,
) {
    FORECAST(
        "forecast",
        "Upcoming appointment",
        "Hello {name}, this is a friendly reminder of your clinic appointment on {date}. " +
            "Please remember to attend. Thank you, {facility}.",
    ),
    APPROACHING_IIT(
        "approaching",
        "Approaching missed (1–27 days)",
        "Hello {name}, we noticed you have not yet come for your appointment due on {date}. " +
            "Please visit the clinic soon to continue your care. Thank you, {facility}.",
    ),
    ESTABLISHED_IIT(
        "established",
        "Missed appointment (28+ days)",
        "Hello {name}, our records show you missed a recent clinic appointment. " +
            "Please visit {facility} to continue your care. Thank you.",
    );

    companion object {
        /** Supported placeholders, shown to the user in the template editor. */
        val PLACEHOLDERS = listOf("{name}", "{date}", "{facility}", "{days}")
    }
}

/** Context substituted into a template for a specific client. */
data class ReminderContext(
    val displayName: String,
    val facilityName: String,
    val appointmentDate: Date?,
    val daysOverdue: Int,
    val daysUntilAppointment: Int,
)

object ReminderTemplates {

    /** The effective template for a type: the saved custom one, else the built-in default. */
    fun templateFor(type: ReminderType): String =
        SharedPreferencesHelper.getReminderTemplate(type.key)?.takeIf { it.isNotBlank() } ?: type.defaultTemplate

    /** Render a type's template against a client's context. */
    fun render(type: ReminderType, ctx: ReminderContext): String =
        substitute(templateFor(type), ctx)

    /** Render an arbitrary template string (used for live preview in the editor). */
    fun renderRaw(template: String, ctx: ReminderContext): String = substitute(template, ctx)

    private fun substitute(template: String, ctx: ReminderContext): String {
        val first = ctx.displayName.substringBefore(",").trim().ifBlank { "there" }
        val date = ctx.appointmentDate?.let { DateUtils.formatDate(it) } ?: "soon"
        val days = if (ctx.daysOverdue > 0) ctx.daysOverdue.toString() else ctx.daysUntilAppointment.toString()
        return template
            .replace("{name}", first)
            .replace("{date}", date)
            .replace("{facility}", ctx.facilityName.ifBlank { "your clinic" })
            .replace("{days}", days)
    }
}
