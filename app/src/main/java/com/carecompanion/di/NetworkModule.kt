package com.carecompanion.di

import android.content.Context
import com.carecompanion.data.network.EMRApiService
import com.carecompanion.data.network.WincoApiService
import com.carecompanion.utils.SharedPreferencesHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** Qualifier to distinguish the WINCO Retrofit graph from the EMR one. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WincoService

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            // Dynamically rewrite host/scheme/port from the saved server URL on every request
            .addInterceptor { chain ->
                val savedUrl = SharedPreferencesHelper.getEmrBaseUrl(context)
                val request = if (savedUrl != null) {
                    val parsed = savedUrl.trimEnd('/').toHttpUrlOrNull()
                    if (parsed != null) {
                        val newUrl = chain.request().url.newBuilder()
                            .scheme(parsed.scheme)
                            .host(parsed.host)
                            .port(parsed.port)
                            .build()
                        chain.request().newBuilder().url(newUrl).build()
                    } else chain.request()
                } else chain.request()
                chain.proceed(request)
            }
            .addInterceptor { chain ->
                val apiKey = SharedPreferencesHelper.getApiKey(context) ?: ""
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("X-Client-Type", "mobile")
                        .addHeader("Authorization", "Bearer $apiKey")
                        .build()
                )
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        // Base URL is a placeholder — actual host is always overridden by the dynamic interceptor above
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideEMRApiService(retrofit: Retrofit): EMRApiService =
        retrofit.create(EMRApiService::class.java)

    // -------------------------------------------------------------------------
    // WINCO network graph (separate OkHttpClient + Retrofit + WincoApiService)
    // -------------------------------------------------------------------------

    @Provides
    @Singleton
    @WincoService
    fun provideWincoOkHttpClient(@ApplicationContext context: Context): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            // Dynamically rewrite host/scheme/port from the saved WINCO URL on every request
            .addInterceptor { chain ->
                val savedUrl = SharedPreferencesHelper.getWincoBaseUrl(context)
                val request = if (savedUrl != null) {
                    val parsed = savedUrl.trimEnd('/').toHttpUrlOrNull()
                    if (parsed != null) {
                        val newUrl = chain.request().url.newBuilder()
                            .scheme(parsed.scheme)
                            .host(parsed.host)
                            .port(parsed.port)
                            .build()
                        chain.request().newBuilder().url(newUrl).build()
                    } else chain.request()
                } else chain.request()
                chain.proceed(request)
            }
            // Attach Bearer token required by WINCO's _api_guard decorator.
            // The token is stored in SharedPreferences (winco_api_key) after the
            // user authenticates via POST /api/art/token in SettingsViewModel.
            .addInterceptor { chain ->
                val token = SharedPreferencesHelper.getWincoApiKey(context) ?: ""
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("X-Client-Type", "mobile")
                        .build()
                )
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    @Provides
    @Singleton
    @WincoService
    fun provideWincoRetrofit(@WincoService client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("http://localhost/")   // overridden at runtime by the interceptor above
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideWincoApiService(@WincoService retrofit: Retrofit): WincoApiService =
        retrofit.create(WincoApiService::class.java)
}