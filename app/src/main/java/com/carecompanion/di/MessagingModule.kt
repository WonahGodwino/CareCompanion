package com.carecompanion.di

import com.carecompanion.data.messaging.ReminderGateway
import com.carecompanion.data.messaging.ReminderGatewayImpl
import com.carecompanion.data.network.MessagingApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifier for the external messaging-gateway HTTP graph (Termii, SendGrid). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MessagingService

/**
 * Network graph for external messaging gateways. Deliberately separate from the
 * WINCO/EMR graph: it has NO host-rewrite or Authorization interceptors, because
 * every gateway call supplies its own absolute URL and per-request auth.
 */
@Module
@InstallIn(SingletonComponent::class)
object MessagingModule {

    @Provides
    @Singleton
    @MessagingService
    fun provideMessagingOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    @Provides
    @Singleton
    @MessagingService
    fun provideMessagingRetrofit(@MessagingService client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            // Placeholder host — every endpoint uses an absolute @Url.
            .baseUrl("https://localhost/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideMessagingApiService(@MessagingService retrofit: Retrofit): MessagingApiService =
        retrofit.create(MessagingApiService::class.java)

    @Provides
    @Singleton
    fun provideReminderGateway(api: MessagingApiService): ReminderGateway =
        ReminderGatewayImpl(api)
}
