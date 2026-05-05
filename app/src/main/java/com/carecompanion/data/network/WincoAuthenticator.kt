package com.carecompanion.data.network

import android.content.Context
import com.carecompanion.data.network.models.WincoTokenRequest
import com.carecompanion.utils.SharedPreferencesHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp [Authenticator] for the WINCO Bearer token.
 *
 * When WINCO returns HTTP 401 the token has expired (7-day lifetime).
 * This class transparently:
 *   1. Re-authenticates using the stored WINCO username + password.
 *   2. Persists the new token in [SharedPreferencesHelper].
 *   3. Retries the original request with the fresh token — caller sees no error.
 *
 * If credentials are missing, or if re-authentication itself fails, returns
 * null so OkHttp propagates the 401 to the caller rather than looping.
 *
 * Thread-safety: a [synchronized] block ensures only one token refresh happens
 * concurrently even when multiple requests receive 401 at the same time.
 */
@Singleton
class WincoAuthenticator @Inject constructor(
    @ApplicationContext private val context: Context,
) : Authenticator {

    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Guard against infinite retry: if this request was already built with a
        // freshly-refreshed token (marked by our custom header), give up.
        if (response.request.header("X-Token-Refreshed") != null) return null

        val username = SharedPreferencesHelper.getEmrUsername(context)?.takeIf { it.isNotBlank() }
            ?: return null
        val password = SharedPreferencesHelper.getEmrPassword(context)?.takeIf { it.isNotBlank() }
            ?: return null
        val baseUrl = SharedPreferencesHelper.getWincoBaseUrl(context)?.takeIf { it.isNotBlank() }
            ?: return null

        synchronized(refreshLock) {
            // Another thread may have already refreshed the token while we waited on the lock.
            // If the token in prefs differs from the one that triggered the 401, reuse it.
            val storedToken = SharedPreferencesHelper.getWincoApiKey(context)
            val failedToken = response.request.header("Authorization")
                ?.removePrefix("Bearer ")?.trim()
            if (storedToken != null && storedToken != failedToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $storedToken")
                    .header("X-Token-Refreshed", "1")
                    .build()
            }

            // Use a plain client — no interceptors, no auth header — to call the token
            // endpoint. Using the main WINCO client would be circular.
            val newToken = try {
                val plainClient = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
                val wincoService = Retrofit.Builder()
                    .baseUrl(baseUrl.trimEnd('/') + "/")
                    .client(plainClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(WincoApiService::class.java)

                // authenticate() is called on OkHttp's background thread — runBlocking is safe here.
                val resp = runBlocking { wincoService.getToken(WincoTokenRequest(username, password)) }
                resp.accessToken.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            } ?: return null  // re-auth failed — propagate the original 401

            SharedPreferencesHelper.setWincoApiKey(newToken)

            return response.request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .header("X-Client-Type", "mobile")
                .header("X-Token-Refreshed", "1")
                .build()
        }
    }
}
