package com.xiaomi.mimoclaw.agent.healing

import com.google.gson.Gson
import com.xiaomi.mimoclaw.data.model.*
import com.xiaomi.mimoclaw.data.remote.MiMoApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Self-Healing System - 失败自修复系统
 * 当步骤失败时，收集Observation，调用LLM修复TaskStep，重新执行
 */
@Singleton
class SelfHealingSystem @Inject constructor(
    private val apiService: MiMoApiService,
    private val gson: Gson
) {
    companion object {
        private const val REPAIR_PROMPT = """你是一个任务修复专家。一个自动化任务的某个步骤执行失败了，请根据以下信息修复该步骤。

原始任务信息：
%s

失败步骤：
%s

执行结果观察（Observation）：
%s

错误分类：
%s

请分析失败原因并输出修复后的步骤（严格JSON格式）：
{
  "repairedStep": {
    "type": "CLICK|INPUT|OPEN|EXTRACT|WAIT|SCREENSHOT",
    "selector": "新的CSS选择器（如果需要）",
    "value": "新的值（如果需要）",
    "description": "修复后的描述"
  },
  "shouldSkip": false,
  "shouldRetry": true,
  "alternativeSelector": "备用选择器",
  "explanation": "修复原因说明"
}

常见修复策略：
1. DOM_NOT_FOUND → 尝试备用选择器或更通用的选择器
2. ELEMENT_NOT_CLICKABLE → 等待元素可交互或使用JavaScript点击
3. PAGE_CHANGE → 重新分析页面结构，更新选择器
4. TIMEOUT → 增加等待时间或重试
5. 选择器过于具体 → 使用更通用的选择器

如果无法修复，设置 shouldSkip=true 跳过该步骤。"""
    }

    /**
     * 尝试修复失败的步骤
     */
    fun repair(request: RepairRequest): Flow<HealingResult> = flow {
        emit(HealingResult.Analyzing)

        try {
            val prompt = buildRepairPrompt(request)
            val messages = listOf(
                ApiMessage(role = "system", content = "你是一个自动化任务修复专家。请根据失败信息修复任务步骤。"),
                ApiMessage(role = "user", content = prompt)
            )

            val chatRequest = ChatRequest(
                messages = messages,
                stream = false,
                temperature = 0.3f,
                maxTokens = 1000
            )

            val response = apiService.chatCompletions("Bearer dummy", chatRequest)

            if (response.isSuccessful) {
                val content = response.body()?.choices?.firstOrNull()?.message?.content
                if (content != null) {
                    val repairResponse = parseRepairResponse(content)
                    if (repairResponse != null) {
                        emit(HealingResult.Repaired(repairResponse))
                        return@flow
                    }
                }
            }

            // LLM 不可用时的启发式修复
            val heuristicRepair = heuristicRepair(request)
            emit(HealingResult.Repaired(heuristicRepair))

        } catch (e: Exception) {
            // 启发式修复兜底
            val heuristicRepair = heuristicRepair(request)
            emit(HealingResult.Repaired(heuristicRepair))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 启发式修复（LLM 不可用时的兜底策略）
     */
    private fun heuristicRepair(request: RepairRequest): RepairResponse {
        val step = request.failedStep
        val classification = request.failureClassification

        return when (classification.type) {
            FailureType.DOM_NOT_FOUND -> {
                // 尝试更通用的选择器
                val genericSelector = generalizeSelector(step.selector)
                RepairResponse(
                    repairedStep = step.copy(
                        selector = genericSelector,
                        description = "使用通用选择器重试: $genericSelector"
                    ),
                    shouldRetry = true,
                    alternativeSelector = genericSelector,
                    explanation = "DOM未找到，使用更通用的选择器"
                )
            }

            FailureType.ELEMENT_NOT_CLICKABLE -> {
                // 等待后重试
                RepairResponse(
                    repairedStep = step.copy(
                        type = StepType.WAIT_MS,
                        value = "2000",
                        description = "等待元素可交互"
                    ),
                    shouldRetry = true,
                    explanation = "元素不可点击，等待后重试"
                )
            }

            FailureType.TIMEOUT -> {
                RepairResponse(
                    repairedStep = step,
                    shouldRetry = true,
                    explanation = "超时，增加等待后重试"
                )
            }

            FailureType.PAGE_CHANGE -> {
                // 尝试截图+Vision
                RepairResponse(
                    repairedStep = step.copy(
                        type = StepType.SCREENSHOT,
                        description = "页面变化，截图分析"
                    ),
                    shouldRetry = false,
                    explanation = "页面结构变化，需要重新分析"
                )
            }

            FailureType.JS_ERROR -> {
                RepairResponse(
                    repairedStep = step,
                    shouldRetry = true,
                    explanation = "JS错误，重试"
                )
            }

            FailureType.NETWORK_ERROR -> {
                RepairResponse(
                    repairedStep = step,
                    shouldRetry = true,
                    explanation = "网络错误，等待后重试"
                )
            }

            FailureType.NAVIGATION_ERROR -> {
                RepairResponse(
                    repairedStep = step,
                    shouldRetry = true,
                    explanation = "导航错误，重试"
                )
            }

            FailureType.UNKNOWN -> {
                // 尝试截图
                RepairResponse(
                    repairedStep = step.copy(
                        type = StepType.SCREENSHOT,
                        description = "未知错误，截图分析"
                    ),
                    shouldSkip = step.retryCount >= 3,
                    explanation = "未知错误，截图分析或跳过"
                )
            }
        }
    }

    /**
     * 将具体选择器泛化
     * 例: #login-btn → button, .submit-form input → input
     */
    private fun generalizeSelector(selector: String?): String? {
        if (selector == null) return null

        // 提取标签名
        val tagPattern = Regex("^[a-z]+")
        val tag = tagPattern.find(selector)?.value

        // 提取类型
        val typePattern = Regex("\\[type=['\"]?(\\w+)")
        val type = typePattern.find(selector)?.groupValues?.get(1)

        return when {
            selector.contains("login") || selector.contains("登录") -> "button, [type='submit'], a[href*='login']"
            selector.contains("search") || selector.contains("搜索") -> "input[type='search'], input[name*='search'], input[placeholder*='搜索']"
            selector.contains("submit") || selector.contains("提交") -> "[type='submit'], button"
            selector.contains("input") -> "input, textarea"
            selector.contains("button") -> "button, [role='button']"
            selector.contains("link") || selector.contains("a") -> "a"
            tag != null -> tag
            else -> "*"
        }
    }

    private fun buildRepairPrompt(request: RepairRequest): String {
        return String.format(
            REPAIR_PROMPT,
            gson.toJson(mapOf(
                "name" to request.originalTask.name,
                "totalSteps" to request.originalTask.steps.size,
                "currentStep" to request.originalTask.currentStepIndex
            )),
            gson.toJson(mapOf(
                "type" to request.failedStep.type.name,
                "selector" to request.failedStep.selector,
                "value" to request.failedStep.value,
                "description" to request.failedStep.description,
                "retryCount" to request.failedStep.retryCount,
                "error" to request.failedStep.error
            )),
            gson.toJson(mapOf(
                "success" to request.observation.success,
                "url" to request.observation.currentUrl,
                "domExists" to request.observation.domExists,
                "pageTitle" to request.observation.pageTitle,
                "error" to request.observation.error
            )),
            request.failureClassification.type.name
        )
    }

    private fun parseRepairResponse(content: String): RepairResponse? {
        return try {
            val jsonPattern = Regex("\\{[\\s\\S]*\\}")
            val jsonMatch = jsonPattern.find(content)
            if (jsonMatch != null) {
                val parsed = gson.fromJson(jsonMatch.value, RepairResponseJson::class.java)
                RepairResponse(
                    repairedStep = parsed.repairedStep?.let {
                        TaskStep(
                            type = try { StepType.valueOf(it.type.uppercase()) } catch (_: Exception) { StepType.SCREENSHOT },
                            selector = it.selector,
                            value = it.value,
                            description = it.description ?: ""
                        )
                    },
                    shouldSkip = parsed.shouldSkip ?: false,
                    shouldRetry = parsed.shouldRetry ?: false,
                    alternativeSelector = parsed.alternativeSelector,
                    explanation = parsed.explanation ?: ""
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private data class RepairResponseJson(
        val repairedStep: PlanStepJson?,
        val shouldSkip: Boolean?,
        val shouldRetry: Boolean?,
        val alternativeSelector: String?,
        val explanation: String?
    )

    private data class PlanStepJson(
        val type: String?,
        val selector: String?,
        val value: String?,
        val description: String?
    )
}

sealed class HealingResult {
    data object Analyzing : HealingResult()
    data class Repaired(val response: RepairResponse) : HealingResult()
    data class Failed(val message: String) : HealingResult()
}
