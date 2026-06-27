package com.carecompanion.data.network

import com.carecompanion.data.network.models.SendGridRequest
import com.carecompanion.data.network.models.TermiiSmsRequest
import com.carecompanion.data.network.models.TermiiSmsResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Calls external messaging gateways (Termii for SMS, SendGrid for email).
 *
 * Every endpoint takes an absolute [Url] so this service is independent of the
 * WINCO/EMR base URL and host-rewrite interceptors — it must use a dedicated,
 * plain OkHttp client (see MessagingModule).
 */
interface MessagingApiService {

    @POST
    suspend fun sendTermiiSms(
        @Url url: String,
        @Body body: TermiiSmsRequest,
    ): Response<TermiiSmsResponse>

    @POST
    suspend fun sendSendGridEmail(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body body: SendGridRequest,
    ): Response<ResponseBody>
}
