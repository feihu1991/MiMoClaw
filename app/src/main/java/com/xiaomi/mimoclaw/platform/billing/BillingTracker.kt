package com.xiaomi.mimoclaw.platform.billing

import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Billing Tracker - 计费追踪系统
 * 追踪: Token用量 / Vision调用 / 任务执行
 */
@Singleton
class BillingTracker @Inject constructor() {

    private val _records = MutableStateFlow<List<BillingRecord>>(emptyList())
    val records: StateFlow<List<BillingRecord>> = _records.asStateFlow()
    
    private val _dailyUsage = MutableStateFlow(DailyUsage())
    val dailyUsage: StateFlow<DailyUsage> = _dailyUsage.asStateFlow()
    
    /**
     * 记录Token使用
     */
    fun recordTokenUsage(userId: String, taskId: String, tokens: Long, model: String) {
        val record = BillingRecord(
            id = UUID.randomUUID().toString(),
            userId = userId,
            taskId = taskId,
            type = BillingType.TOKEN,
            quantity = tokens,
            unit = "tokens",
            model = model,
            cost = calculateTokenCost(tokens, model)
        )
        addRecord(record)
    }
    
    /**
     * 记录Vision调用
     */
    fun recordVisionUsage(userId: String, taskId: String) {
        val record = BillingRecord(
            id = UUID.randomUUID().toString(),
            userId = userId,
            taskId = taskId,
            type = BillingType.VISION,
            quantity = 1,
            unit = "calls",
            cost = 0.05 // ¥0.05/次
        )
        addRecord(record)
    }
    
    /**
     * 记录任务执行
     */
    fun recordTaskExecution(userId: String, taskId: String, steps: Int, durationMs: Long) {
        val record = BillingRecord(
            id = UUID.randomUUID().toString(),
            userId = userId,
            taskId = taskId,
            type = BillingType.TASK,
            quantity = 1,
            unit = "task",
            metadata = mapOf(
                "steps" to steps.toString(),
                "durationMs" to durationMs.toString()
            ),
            cost = 0.0 // 免费任务执行
        )
        addRecord(record)
    }
    
    /**
     * 获取用户总用量
     */
    fun userUsage(userId: String): UsageSummary {
        val userRecords = _records.value.filter { it.userId == userId }
        return UsageSummary(
            totalTokens = userRecords.filter { it.type == BillingType.TOKEN }.sumOf { it.quantity },
            totalVisionCalls = userRecords.filter { it.type == BillingType.VISION }.sumOf { it.quantity }.toInt(),
            totalTasks = userRecords.filter { it.type == BillingType.TASK }.sumOf { it.quantity }.toInt(),
            totalCost = userRecords.sumOf { it.cost }
        )
    }
    
    /**
     * 获取今日用量
     */
    fun todayUsage(userId: String): DailyUsage {
        val today = System.currentTimeMillis() - (System.currentTimeMillis() % (24 * 60 * 60 * 1000))
        val todayRecords = _records.value.filter { 
            it.userId == userId && it.timestamp >= today 
        }
        return DailyUsage(
            tokens = todayRecords.filter { it.type == BillingType.TOKEN }.sumOf { it.quantity },
            visionCalls = todayRecords.filter { it.type == BillingType.VISION }.sumOf { it.quantity }.toInt(),
            tasks = todayRecords.filter { it.type == BillingType.TASK }.sumOf { it.quantity }.toInt(),
            cost = todayRecords.sumOf { it.cost }
        )
    }
    
    private fun addRecord(record: BillingRecord) {
        _records.value = _records.value + record
        _dailyUsage.value = _dailyUsage.value.copy(
            tokens = _dailyUsage.value.tokens + if (record.type == BillingType.TOKEN) record.quantity else 0,
            visionCalls = _dailyUsage.value.visionCalls + if (record.type == BillingType.VISION) record.quantity.toInt() else 0,
            tasks = _dailyUsage.value.tasks + if (record.type == BillingType.TASK) 1 else 0,
            cost = _dailyUsage.value.cost + record.cost
        )
    }
    
    private fun calculateTokenCost(tokens: Long, model: String): Double {
        return when (model) {
            "MiMo-V2.5-Pro" -> tokens * 0.00002 // ¥0.02/1K tokens
            "MiMo-V2.5" -> tokens * 0.00001
            else -> tokens * 0.000005
        }
    }
}

data class BillingRecord(
    val id: String,
    val userId: String,
    val taskId: String,
    val type: BillingType,
    val quantity: Long,
    val unit: String,
    val model: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val cost: Double,
    val timestamp: Long = System.currentTimeMillis()
)

enum class BillingType {
    TOKEN,      // Token用量
    VISION,     // Vision调用
    TASK        // 任务执行
}

data class UsageSummary(
    val totalTokens: Long,
    val totalVisionCalls: Int,
    val totalTasks: Int,
    val totalCost: Double
)

data class DailyUsage(
    val tokens: Long = 0,
    val visionCalls: Int = 0,
    val tasks: Int = 0,
    val cost: Double = 0.0
)
