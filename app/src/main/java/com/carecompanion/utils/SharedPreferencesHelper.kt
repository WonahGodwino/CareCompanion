package com.carecompanion.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.carecompanion.CareCompanionApplication

object SharedPreferencesHelper {
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
    fun clearAll(context: Context) = prefs().edit().clear().apply()
    fun getSyncPage(context: Context): Int = prefs().getInt("sync_page", 0)
    fun setSyncPage(context: Context, page: Int) = prefs().edit().putInt("sync_page", page).apply()

    // ── WINCO server (intermediate aggregation layer) ─────────────────────────
    /** Base URL of the WINCO server, e.g. "http://192.168.1.10:5000". */
    fun setWincoBaseUrl(url: String) = prefs().edit().putString("winco_url", url).apply()
    fun getWincoBaseUrl(context: Context): String? =
        prefs().getString("winco_url", null) ?: prefs().getString("emr_url", null)

    /** X-API-KEY header value required by WINCO's mobile endpoints. */
    fun setWincoApiKey(apiKey: String) = prefs().edit().putString("winco_api_key", apiKey).apply()
    fun getWincoApiKey(context: Context): String? =
        prefs().getString("winco_api_key", null) ?: prefs().getString("api_key", null)

    fun isWincoConfigured(context: Context): Boolean =
        !getWincoBaseUrl(context).isNullOrBlank()

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