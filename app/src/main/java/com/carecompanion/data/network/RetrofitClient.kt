package com.carecompanion.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private var currentBaseUrl = ""
    private var retrofitInstance: Retrofit? = null
    private var apiServiceInstance: EMRApiService? = null

    fun getInstance(baseUrl: String): EMRApiService {
        if (retrofitInstance == null || currentBaseUrl != baseUrl) {
            currentBaseUrl = baseUrl
            val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().addHeader("X-Client-Type","mobile").build())
                }
                .connectTimeout(30,TimeUnit.SECONDS)
                .readTimeout(60,TimeUnit.SECONDS)
                .writeTimeout(60,TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
            retrofitInstance = Retrofit.Builder().baseUrl(baseUrl).client(client)
                .addConverterFactory(GsonConverterFactory.create()).build()
            apiServiceInstance = retrofitInstance!!.create(EMRApiService::class.java)
        }
        return apiServiceInstance!!
    }
}