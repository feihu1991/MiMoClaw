package com.xiaomi.mimoclaw.platform.tenant

import com.xiaomi.mimoclaw.data.local.PreferencesManager
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tenant Manager - 多租户管理
 * 负责: 用户隔离 / API Key认证 / 配额管理
 */
@Singleton
class TenantManager @Inject constructor(
    private val preferencesManager: PreferencesManager
) {
    private val _currentUser = MutableStateFlow<TenantUser?>(null)
    val currentUser: StateFlow<TenantUser?> = _currentUser.asStateFlow()
    
    private val _quota = MutableStateFlow<UserQuota?>(null)
    val quota: StateFlow<UserQuota?> = _quota.asStateFlow()
    
    val isLoggedIn: Boolean
        get() = _currentUser.value != null
    
    /**
     * 登录
     */
    suspend fun login(user: TenantUser) {
        _currentUser.value = user
        preferencesManager.saveToken(user.apiKey ?: "")
        _quota.value = UserQuota.fromPlan(user.plan)
    }
    
    /**
     * 登出
     */
    suspend fun logout() {
        _currentUser.value = null
        _quota.value = null
        preferencesManager.clearAll()
    }
    
    /**
     * 检查配额
     */
    fun checkQuota(requestedTokens: Long = 0): QuotaCheck {
        val q = _quota.value ?: return QuotaCheck(false, "未登录")
        
        return when {
            q.tasksRemaining <= 0 -> QuotaCheck(false, "任务配额已用完")
            q.tokensRemaining < requestedTokens -> QuotaCheck(false, "Token配额不足")
            q.visionCallsRemaining <= 0 && requestedTokens > 0 -> QuotaCheck(false, "Vision配额已用完")
            else -> QuotaCheck(true)
        }
    }
    
    /**
     * 消耗配额
     */
    fun consumeQuota(tokens: Long = 0, visionCalls: Int = 0) {
        val q = _quota.value ?: return
        _quota.value = q.copy(
            tasksRemaining = q.tasksRemaining - 1,
            tokensRemaining = q.tokensRemaining - tokens,
            visionCallsRemaining = q.visionCallsRemaining - visionCalls
        )
    }
    
    /**
     * 获取当前Plan
     */
    fun currentPlan(): PlanType {
        return _currentUser.value?.plan ?: PlanType.FREE
    }
}

data class TenantUser(
    val userId: String,
    val email: String,
    val name: String,
    val plan: PlanType,
    val apiKey: String? = null,
    val avatar: String? = null
)

enum class PlanType(val displayName: String, val taskQuota: Int, val tokenQuota: Long, val visionQuota: Int, val price: String) {
    FREE("Free", 10, 100_000, 5, "¥0"),
    PRO("Pro", 100, 5_000_000, 100, "¥99/月"),
    TEAM("Team", 500, 50_000_000, 500, "¥499/月"),
    ENTERPRISE("Enterprise", -1, -1, -1, "联系销售")
}

data class UserQuota(
    val tasksRemaining: Int,
    val tokensRemaining: Long,
    val visionCallsRemaining: Int,
    val resetsAt: Long
) {
    companion object {
        fun fromPlan(plan: PlanType): UserQuota {
            return UserQuota(
                tasksRemaining = plan.taskQuota,
                tokensRemaining = plan.tokenQuota,
                visionCallsRemaining = plan.visionQuota,
                resetsAt = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000 // 30天后
            )
        }
    }
}

data class QuotaCheck(
    val allowed: Boolean,
    val reason: String? = null
)
