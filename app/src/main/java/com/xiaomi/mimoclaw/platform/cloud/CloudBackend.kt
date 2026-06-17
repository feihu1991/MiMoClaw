package com.xiaomi.mimoclaw.platform.cloud

import com.xiaomi.mimoclaw.data.model.*
import kotlinx.coroutines.flow.Flow
import retrofit2.Response
import retrofit2.http.*

// ══════════════════════════════════════════════
// Cloud Backend API - 云端服务接口
// ══════════════════════════════════════════════

interface CloudBackendApi {

    companion object {
        const val BASE_URL = "https://api.mimo-agent.com/v1/"
    }

    // ── Task API ──
    
    @POST("tasks")
    suspend fun createTask(
        @Header("Authorization") token: String,
        @Body request: CreateTaskRequest
    ): Response<TaskResponse>

    @GET("tasks/{taskId}")
    suspend fun getTask(
        @Header("Authorization") token: String,
        @Path("taskId") taskId: String
    ): Response<TaskResponse>

    @GET("tasks")
    suspend fun listTasks(
        @Header("Authorization") token: String,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<TaskListResponse>

    @POST("tasks/{taskId}/cancel")
    suspend fun cancelTask(
        @Header("Authorization") token: String,
        @Path("taskId") taskId: String
    ): Response<TaskResponse>

    @POST("tasks/{taskId}/retry")
    suspend fun retryTask(
        @Header("Authorization") token: String,
        @Path("taskId") taskId: String
    ): Response<TaskResponse>

    // ── Log Streaming (SSE) ──
    
    @Streaming
    @GET("tasks/{taskId}/logs/stream")
    suspend fun streamLogs(
        @Header("Authorization") token: String,
        @Path("taskId") taskId: String
    ): Response<okhttp3.ResponseBody>

    // ── Agent API ──
    
    @GET("agents")
    suspend fun listAgents(
        @Header("Authorization") token: String
    ): Response<AgentListResponse>

    @GET("agents/{agentId}/status")
    suspend fun getAgentStatus(
        @Header("Authorization") token: String,
        @Path("agentId") agentId: String
    ): Response<AgentStatusResponse>

    // ── Billing API ──
    
    @GET("billing/usage")
    suspend fun getUsage(
        @Header("Authorization") token: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): Response<UsageResponse>

    @GET("billing/plan")
    suspend fun getPlan(
        @Header("Authorization") token: String
    ): Response<PlanResponse>

    // ── User API ──
    
    @GET("user/profile")
    suspend fun getProfile(
        @Header("Authorization") token: String
    ): Response<UserProfileResponse>

    @GET("user/quota")
    suspend fun getQuota(
        @Header("Authorization") token: String
    ): Response<QuotaResponse>
}

// ── Request/Response Models ──

data class CreateTaskRequest(
    val instruction: String,
    val priority: Int = 0,
    val callbackUrl: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class TaskResponse(
    val id: String,
    val status: String,
    val name: String,
    val steps: List<StepResponse>?,
    val currentStep: Int,
    val error: String?,
    val createdAt: String,
    val updatedAt: String
)

data class StepResponse(
    val id: String,
    val type: String,
    val status: String,
    val description: String,
    val error: String?
)

data class TaskListResponse(
    val tasks: List<TaskResponse>,
    val total: Int,
    val page: Int,
    val limit: Int
)

data class AgentListResponse(
    val agents: List<AgentInfo>
)

data class AgentInfo(
    val id: String,
    val type: String,
    val status: String,
    val currentTask: String?
)

data class AgentStatusResponse(
    val id: String,
    val type: String,
    val status: String,
    val tasksCompleted: Int,
    val tasksFailed: Int,
    val uptime: Long
)

data class UsageResponse(
    val totalTokens: Long,
    val totalTasks: Int,
    val totalVisionCalls: Int,
    val period: String
)

data class PlanResponse(
    val plan: String,
    val taskQuota: Int,
    val tokenQuota: Long,
    val visionQuota: Int,
    val expiresAt: String
)

data class UserProfileResponse(
    val userId: String,
    val email: String,
    val name: String,
    val plan: String,
    val apiKey: String?
)

data class QuotaResponse(
    val tasksRemaining: Int,
    val tokensRemaining: Long,
    val visionCallsRemaining: Int,
    val resetsAt: String
)
