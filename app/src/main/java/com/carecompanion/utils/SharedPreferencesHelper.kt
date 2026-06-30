package com.carecompanion.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.carecompanion.CareCompanionApplication

object SharedPreferencesHelper {
    private const val KEY_MATCH_THRESHOLD = "biometric_match_threshold"

    fun setMatchThreshold(context: Context, threshold: Double) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putFloat(KEY_MATCH_THRESHOLD, threshold.toFloat()).apply()
    }

    fun getMatchThreshold(context: Context): Double {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getFloat(KEY_MATCH_THRESHOLD, 0.35f).toDouble()  // Default 35% — calibrated for Bozorth3/SecuGen (see IDENTIFICATION_MIN_SCORE)
    }
    // ...existing code...
    private const val PREF_NAME = "care_companion_prefs"
    private const val TAG = "SharedPreferencesHelper"

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun prefs(): SharedPreferences {
        cachedPrefs?.let { return it }
        synchronized(this) {
            cachedPrefs?.let { return it }
            val context = CareCompanionApplication.getAppContext()
            val resolved = runCatching {
                createEncryptedPrefs(context)
            }.recoverCatching {
                // Recover from corrupted encrypted prefs / keystore mismatch.
                Log.e(TAG, "Encrypted prefs initialization failed, resetting local preferences", it)
                context.deleteSharedPreferences(PREF_NAME)
                createEncryptedPrefs(context)
            }.getOrElse {
                // Final fallback so app can still boot in production.
                Log.e(TAG, "Encrypted prefs recovery failed, using plain SharedPreferences fallback", it)
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            }

            cachedPrefs = resolved
            return resolved
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            PREF_NAME,
            key,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    fun isFirstLaunch(context: Context) = prefs().getBoolean("first_launch", true)
    fun setFirstLaunchComplete(context: Context) = prefs().edit().putBoolean("first_launch", false).apply()
    fun setEmrBaseUrl(url: String) = prefs().edit().putString("emr_url", url).apply()
    fun getEmrBaseUrl(context: Context): String? = prefs().getString("emr_url", null)
    fun setApiKey(apiKey: String) = prefs().edit().putString("api_key", apiKey).apply()
    fun getApiKey(context: Context): String? = prefs().getString("api_key", null)
    fun setFacilityId(facilityId: Long) = prefs().edit().putLong("facility_id", facilityId).apply()
    fun getFacilityId(context: Context): Long = prefs().getLong("facility_id", 0L)
    fun getSyncInterval(context: Context): Int = prefs().getInt("sync_interval", 5)
    fun setSyncInterval(context: Context, minutes: Int) = prefs().edit().putInt("sync_interval", minutes).apply()
    fun isAutoSyncEnabled(context: Context): Boolean = prefs().getBoolean("auto_sync_enabled", true)
    fun setAutoSyncEnabled(enabled: Boolean) = prefs().edit().putBoolean("auto_sync_enabled", enabled).apply()
    fun getLastSyncDate(context: Context): String? = prefs().getString("last_sync_date", null)
    fun setLastSyncDate(context: Context, date: String) = prefs().edit().putString("last_sync_date", date).apply()
    fun getScannerType(context: Context): String? = prefs().getString("scanner_type", null)
    fun setScannerType(context: Context, type: String) = prefs().edit().putString("scanner_type", type).apply()
    fun setEmrUsername(username: String) = prefs().edit().putString("emr_username", username).apply()
    fun getEmrUsername(context: Context): String? = prefs().getString("emr_username", null)
    fun setEmrPassword(password: String) = prefs().edit().putString("emr_password", password).apply()
    fun getEmrPassword(context: Context): String? = prefs().getString("emr_password", null)
    fun setActiveFacilityName(name: String) = prefs().edit().putString("active_facility_name", name).apply()
    fun getActiveFacilityName(context: Context): String? = prefs().getString("active_facility_name", null)
    /** Sets both the active facility ID (new key) and legacy facility_id for backward compat. */
    fun setActiveFacilityId(facilityId: Long) {
        prefs().edit().putLong("facility_id", facilityId).apply()
    }
    fun getActiveFacilityId(context: Context): Long = prefs().getLong("facility_id", 0L)

    // ── Facility geolocation (captured on-site, per facility) ──────────────
    // Stored locally so the AI risk engine can compute each client's distance to
    // the facility. Keyed by facility id to support multi-facility devices.
    fun setFacilityLocation(facilityId: Long, latitude: Double, longitude: Double, capturedAtMs: Long = System.currentTimeMillis()) {
        prefs().edit()
            .putString("facility_lat_$facilityId", latitude.toString())
            .putString("facility_lng_$facilityId", longitude.toString())
            .putLong("facility_loc_captured_$facilityId", capturedAtMs)
            .apply()
    }
    fun getFacilityLatitude(facilityId: Long): Double? = prefs().getString("facility_lat_$facilityId", null)?.toDoubleOrNull()
    fun getFacilityLongitude(facilityId: Long): Double? = prefs().getString("facility_lng_$facilityId", null)?.toDoubleOrNull()
    fun getFacilityLocationCapturedAt(facilityId: Long): Long = prefs().getLong("facility_loc_captured_$facilityId", 0L)

    // ── Reminder gateways (SMS via Termii, Email via SendGrid) ─────────────
    // API keys are stored in EncryptedSharedPreferences. NOTE: keys embedded in a
    // mobile app are still extractable; for production prefer proxying sends through
    // the WINCO backend. These setters allow on-device configuration meanwhile.
    fun isSmsReminderEnabled(): Boolean = prefs().getBoolean("sms_reminder_enabled", false)
    fun setSmsReminderEnabled(enabled: Boolean) = prefs().edit().putBoolean("sms_reminder_enabled", enabled).apply()
    fun getTermiiBaseUrl(): String = prefs().getString("termii_base_url", "https://api.ng.termii.com") ?: "https://api.ng.termii.com"
    fun setTermiiBaseUrl(url: String) = prefs().edit().putString("termii_base_url", url.trim()).apply()
    fun getTermiiApiKey(): String? = prefs().getString("termii_api_key", null)?.takeIf { it.isNotBlank() }
        ?: com.carecompanion.BuildConfig.TERMII_API_KEY.takeIf { it.isNotBlank() }
    fun setTermiiApiKey(key: String) = prefs().edit().putString("termii_api_key", key.trim()).apply()
    fun getTermiiSenderId(): String = prefs().getString("termii_sender_id", "CareComp") ?: "CareComp"
    fun setTermiiSenderId(id: String) = prefs().edit().putString("termii_sender_id", id.trim()).apply()

    fun isEmailReminderEnabled(): Boolean = prefs().getBoolean("email_reminder_enabled", false)
    fun setEmailReminderEnabled(enabled: Boolean) = prefs().edit().putBoolean("email_reminder_enabled", enabled).apply()
    fun getSendGridBaseUrl(): String = prefs().getString("sendgrid_base_url", "https://api.sendgrid.com") ?: "https://api.sendgrid.com"
    fun setSendGridBaseUrl(url: String) = prefs().edit().putString("sendgrid_base_url", url.trim()).apply()
    fun getSendGridApiKey(): String? = prefs().getString("sendgrid_api_key", null)
    fun setSendGridApiKey(key: String) = prefs().edit().putString("sendgrid_api_key", key.trim()).apply()
    fun getReminderEmailFrom(): String? = prefs().getString("reminder_email_from", null)
    fun setReminderEmailFrom(email: String) = prefs().edit().putString("reminder_email_from", email.trim()).apply()

    // ── Automatic reminders + per-type message templates ───────────────────
    fun isAutoReminderEnabled(): Boolean = prefs().getBoolean("auto_reminder_enabled", false)
    fun setAutoReminderEnabled(enabled: Boolean) = prefs().edit().putBoolean("auto_reminder_enabled", enabled).apply()
    fun getAutoReminderIntervalHours(): Int = prefs().getInt("auto_reminder_interval_hours", 24)
    fun setAutoReminderIntervalHours(hours: Int) = prefs().edit().putInt("auto_reminder_interval_hours", hours).apply()
    /** Who the AI auto-reminds: name of a ReminderAudience (GENERAL | AT_RISK). */
    fun getAutoReminderAudience(): String = prefs().getString("auto_reminder_audience", "GENERAL") ?: "GENERAL"
    fun setAutoReminderAudience(value: String) = prefs().edit().putString("auto_reminder_audience", value).apply()

    /** Saved custom template for a reminder type, or null to use the built-in default. */
    fun getReminderTemplate(typeKey: String): String? = prefs().getString("reminder_tpl_$typeKey", null)
    fun setReminderTemplate(typeKey: String, template: String) =
        prefs().edit().putString("reminder_tpl_$typeKey", template).apply()
    fun clearReminderTemplate(typeKey: String) = prefs().edit().remove("reminder_tpl_$typeKey").apply()

    // ── Learned risk model (Phase 1: in-app adopt + guardrail) ─────────────
    fun getRiskScoringMode(): String = prefs().getString("risk_scoring_mode", "HEURISTIC") ?: "HEURISTIC"
    fun setRiskScoringMode(mode: String) = prefs().edit().putString("risk_scoring_mode", mode).apply()
    fun getLearnedModelJson(): String? = prefs().getString("learned_model_json", null)
    fun setLearnedModelJson(json: String) = prefs().edit().putString("learned_model_json", json).apply()
    fun clearLearnedModel() = prefs().edit().remove("learned_model_json").remove("risk_scoring_mode").apply()

    // Second prediction head: EAC cascade-failure (outcome='eac_failure').
    fun getEacModelJson(): String? = prefs().getString("eac_model_json", null)
    fun setEacModelJson(json: String) = prefs().edit().putString("eac_model_json", json).apply()

    /** De-dupe: last epoch-day (WAT) a reminder was sent to a patient. 0 = never. */
    fun getReminderLastSentEpochDay(uuid: String): Int = prefs().getInt("reminder_sent_$uuid", 0)
    fun setReminderLastSentEpochDay(uuid: String, epochDay: Int) =
        prefs().edit().putInt("reminder_sent_$uuid", epochDay).apply()

    fun clearAll(context: Context) = prefs().edit().clear().apply()
    // ── TX_ML pull filters (IIT/TRANSFER_OUT/DEATH) ───────────────────────
    fun getSyncPage(context: Context): Int = prefs().getInt("sync_page_offset", 0)
    fun setSyncPage(context: Context, page: Int) = prefs().edit().putInt("sync_page_offset", page).apply()
    fun isTxMlIncludeEnabled(context: Context): Boolean = prefs().getBoolean("tx_ml_include_enabled", false)
    fun setTxMlIncludeEnabled(enabled: Boolean) = prefs().edit().putBoolean("tx_ml_include_enabled", enabled).apply()
    fun getTxMlStartDate(context: Context): String = prefs().getString("tx_ml_start_date", "") ?: ""
    fun setTxMlStartDate(value: String) = prefs().edit().putString("tx_ml_start_date", value).apply()
    fun getTxMlEndDate(context: Context): String = prefs().getString("tx_ml_end_date", "") ?: ""
    fun setTxMlEndDate(value: String) = prefs().edit().putString("tx_ml_end_date", value).apply()

    // ── WINCO server (intermediate aggregation layer) ─────────────────────────
    /** Base URL of the WINCO server, e.g. "http://192.168.1.10:5000". */
    fun normalizeBaseUrl(rawUrl: String?): String? {
        val raw = rawUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val withScheme = if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            raw
        } else {
            "http://$raw"
        }
        return withScheme.trimEnd('/') + "/"
    }

    fun setWincoBaseUrl(url: String) {
        val normalized = normalizeBaseUrl(url) ?: return
        prefs().edit().putString("winco_url", normalized).apply()
    }

    fun getWincoBaseUrl(context: Context): String? =
        normalizeBaseUrl(prefs().getString("winco_url", null) ?: prefs().getString("emr_url", null))

    /** X-API-KEY header value required by WINCO's mobile endpoints. */
    fun setWincoApiKey(apiKey: String) = prefs().edit().putString("winco_api_key", apiKey).apply()
    fun getWincoApiKey(context: Context): String? =
        prefs().getString("winco_api_key", null) ?: prefs().getString("api_key", null)

    fun isWincoConfigured(context: Context): Boolean =
        !getWincoBaseUrl(context).isNullOrBlank()

    // ── User session management ───────────────────────────────────────────────
    private const val SESSION_TIMEOUT_MS = 8 * 60 * 60 * 1000L  // 8 hours

    fun setLoggedInUserId(id: Long) = prefs().edit().putLong("session_user_id", id).apply()
    fun getLoggedInUserId(): Long = prefs().getLong("session_user_id", -1L)

    fun setSessionStartedAt(timestamp: Long) = prefs().edit().putLong("session_started_at", timestamp).apply()
    fun getSessionStartedAt(): Long = prefs().getLong("session_started_at", -1L)

    fun setLoggedInUsername(username: String) = prefs().edit().putString("session_username", username).apply()
    fun getLoggedInUsername(): String? = prefs().getString("session_username", null)

    fun setLoggedInUserFullName(name: String) = prefs().edit().putString("session_fullname", name).apply()
    fun getLoggedInUserFullName(): String? = prefs().getString("session_fullname", null)

    fun setLoggedInUserRole(role: String) = prefs().edit().putString("session_role", role).apply()
    fun getLoggedInUserRole(): String? = prefs().getString("session_role", null)

    fun isSessionValid(): Boolean {
        val userId = getLoggedInUserId()
        if (userId < 0) return false
        val startedAt = getSessionStartedAt()
        if (startedAt < 0) return false
        return (System.currentTimeMillis() - startedAt) < SESSION_TIMEOUT_MS
    }

    fun clearSession() {
        prefs().edit()
            .remove("session_user_id")
            .remove("session_started_at")
            .remove("session_username")
            .remove("session_fullname")
            .remove("session_role")
            .apply()
    }

    // ── Notification preferences ──────────────────────────────────────────────
    /** Master switch — when false, ReminderWorker posts nothing. */
    fun areNotificationsEnabled(context: Context): Boolean = prefs().getBoolean("notif_enabled", true)
    fun setNotificationsEnabled(enabled: Boolean) = prefs().edit().putBoolean("notif_enabled", enabled).apply()

    /** Daily digest notification (active-client count summary at 08:00 AM). */
    fun isDailyDigestEnabled(context: Context): Boolean = prefs().getBoolean("notif_daily_digest", true)
    fun setDailyDigestEnabled(enabled: Boolean) = prefs().edit().putBoolean("notif_daily_digest", enabled).apply()

    /** ART Refills service reminder. */
    fun isArtRefillsReminderEnabled(context: Context): Boolean = prefs().getBoolean("notif_art_refills", true)
    fun setArtRefillsReminderEnabled(enabled: Boolean) = prefs().edit().putBoolean("notif_art_refills", enabled).apply()

    /** Missed Appointments service reminder. */
    fun isMissedApptsReminderEnabled(context: Context): Boolean = prefs().getBoolean("notif_missed_appts", true)
    fun setMissedApptsReminderEnabled(enabled: Boolean) = prefs().edit().putBoolean("notif_missed_appts", enabled).apply()

    /** Viral Load service reminder. */
    fun isViralLoadReminderEnabled(context: Context): Boolean = prefs().getBoolean("notif_viral_load", true)
    fun setViralLoadReminderEnabled(enabled: Boolean) = prefs().edit().putBoolean("notif_viral_load", enabled).apply()

    /** TPT service reminder. */
    fun isTptReminderEnabled(context: Context): Boolean = prefs().getBoolean("notif_tpt", true)
    fun setTptReminderEnabled(enabled: Boolean) = prefs().edit().putBoolean("notif_tpt", enabled).apply()

    /** AHD alert. */
    fun isAhdAlertEnabled(context: Context): Boolean = prefs().getBoolean("notif_ahd", true)
    fun setAhdAlertEnabled(enabled: Boolean) = prefs().edit().putBoolean("notif_ahd", enabled).apply()
}