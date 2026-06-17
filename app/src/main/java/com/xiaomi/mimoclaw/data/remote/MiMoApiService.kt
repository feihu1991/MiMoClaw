package com.xiaomi.mimoclaw.data.remote

import com.xiaomi.mimoclaw.data.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface MiMoApiService {

    companion object {
        const val API_BASE = "https://token-plan-cn.xiaomimimo.com/v1/"
    }

    @POST("chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") token: String,
        @Body request: ChatRequest
    ): Response<ChatResponse>

    @Streaming
    @POST("chat/completions")
    suspend fun chatCompletionsStream(
        @Header("Authorization") token: String,
        @Body request: ChatRequest
    ): Response<ResponseBody>

    @GET("models")
    suspend fun getModels(
        @Header("Authorization") token: String
    ): Response<ResponseBody>
}
