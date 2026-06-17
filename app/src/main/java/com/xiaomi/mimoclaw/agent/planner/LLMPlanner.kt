package com.xiaomi.mimoclaw.agent.planner

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
 * LLM Planner - 将自然语言转换为 Task JSON
 * 失败时自动修复 Task
 */
@Singleton
class LLMPlanner @Inject constructor(
    private val apiService: MiMoApiService,
    private val gson: Gson
) {
    companion object {
        private const val PLANNING_SYSTEM_PROMPT = """你是一个任务规划器。用户会给你一个自然语言指令，你需要将其转换为严格JSON格式的任务步骤。

输出格式（必须严格JSON，不要包含其他文字）：
{
  "name": "任务名称",
  "description": "任务描述",
  "steps": [
    {"type": "OPEN", "value": "https://example.com", "description": "打开网页"},
    {"type": "CLICK", "selector": "#login-btn", "description": "点击登录按钮"},
    {"type": "INPUT", "selector": "#username", "value": "test", "description": "输入用户名"},
    {"type": "EXTRACT", "selector": ".result", "description": "提取结果"},
    {"type": "WAIT", "selector": ".loading", "description": "等待加载完成"},
    {"type": "SCREENSHOT", "description": "截图保存"},
    {"type": "SCROLL", "value": "down", "description": "向下滚动"}
  ]
}

支持的步骤类型：
- OPEN: 打开URL (需要value)
- CLICK: 点击元素 (需要selector)
- INPUT: 输入文本 (需要selector和value)
- EXTRACT: 提取文本 (需要selector)
- WAIT: 等待元素出现 (需要selector)
- SCREENSHOT: 截图
- SCROLL: 滚动 (value: down/up/left/right)
- NAVIGATE_BACK: 返回上一页
- WAIT_MS: 等待毫秒 (value: 毫秒数)

selector可以是CSS选择器，也可以是元素描述（如"登录按钮"、"搜索输入框"）。"""

        private const val FIX_PROMPT = """之前的任务执行失败了，请根据错误信息修复任务步骤。

原始任务：
%s

失败步骤：
%s

错误信息：
%s

请输出修复后的完整任务JSON（与之前相同的格式）。"""
    }

    /**
     * 将自然语言指令转换为任务
     */
    fun plan(request: PlanRequest): Flow<PlanResult> = flow {
        emit(PlanResult.Planning)

        try {
            val messages = listOf(
                ApiMessage(role = "system", content = PLANNING_SYSTEM_PROMPT),
                ApiMessage(role = "user", content = buildPlanningPrompt(request))
            )

            val chatRequest = ChatRequest(
                messages = messages,
                stream = false,
                temperature = 0.3f,
                maxTokens = 2000
            )

            // 使用 token-based API
            val response = apiService.chatCompletions("Bearer dummy", chatRequest)

            if (response.isSuccessful) {
                val content = response.body()?.choices?.firstOrNull()?.message?.content
                if (content != null) {
                    val planResponse = parsePlanResponse(content)
                    if (planResponse != null) {
                        val task = planResponse.toTask()
                        emit(PlanResult.Success(task))
                    } else {
                        emit(PlanResult.Error("无法解析任务计划"))
                    }
                } else {
                    emit(PlanResult.Error("LLM 返回空内容"))
                }
            } else {
                // Fallback: 使用简单解析
                val fallbackTask = parseSimpleInstruction(request.instruction)
                emit(PlanResult.Success(fallbackTask))
            }
        } catch (e: Exception) {
            // Fallback: 使用简单解析
            val fallbackTask = parseSimpleInstruction(request.instruction)
            emit(PlanResult.Success(fallbackTask))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 修复失败的任务
     */
    fun fixTask(
        originalTask: AgentTask,
        failedStep: TaskStep,
        error: String
    ): Flow<PlanResult> = flow {
        emit(PlanResult.Planning)

        try {
            val fixPrompt = String.format(
                FIX_PROMPT,
                gson.toJson(originalTask.toPlanFormat()),
                gson.toJson(failedStep),
                error
            )

            val messages = listOf(
                ApiMessage(role = "system", content = PLANNING_SYSTEM_PROMPT),
                ApiMessage(role = "user", content = fixPrompt)
            )

            val chatRequest = ChatRequest(
                messages = messages,
                stream = false,
                temperature = 0.3f,
                maxTokens = 2000
            )

            val response = apiService.chatCompletions("Bearer dummy", chatRequest)

            if (response.isSuccessful) {
                val content = response.body()?.choices?.firstOrNull()?.message?.content
                if (content != null) {
                    val planResponse = parsePlanResponse(content)
                    if (planResponse != null) {
                        val task = planResponse.toTask()
                        emit(PlanResult.Success(task))
                        return@flow
                    }
                }
            }

            // Fallback: 简单重试
            emit(PlanResult.Success(originalTask))
        } catch (e: Exception) {
            emit(PlanResult.Success(originalTask))
        }
    }.flowOn(Dispatchers.IO)

    // ── 简单指令解析（LLM 不可用时的兜底） ──

    private fun parseSimpleInstruction(instruction: String): AgentTask {
        val steps = mutableListOf<TaskStep>()

        // URL 模式
        val urlPattern = Regex("https?://[^\\s]+")
        val urlMatch = urlPattern.find(instruction)
        if (urlMatch != null) {
            steps.add(TaskStep(
                type = StepType.OPEN,
                value = urlMatch.value,
                description = "打开 ${urlMatch.value}"
            ))
        }

        // 点击模式
        if (instruction.contains("点击") || instruction.contains("click")) {
            val selector = extractSelector(instruction) ?: "button"
            steps.add(TaskStep(
                type = StepType.CLICK,
                selector = selector,
                description = "点击元素"
            ))
        }

        // 输入模式
        if (instruction.contains("输入") || instruction.contains("input") || instruction.contains("填写")) {
            val selector = extractSelector(instruction) ?: "input"
            val value = extractValue(instruction) ?: ""
            steps.add(TaskStep(
                type = StepType.INPUT,
                selector = selector,
                value = value,
                description = "输入: $value"
            ))
        }

        // 截图
        if (instruction.contains("截图") || instruction.contains("screenshot")) {
            steps.add(TaskStep(
                type = StepType.SCREENSHOT,
                description = "截图"
            ))
        }

        // 提取
        if (instruction.contains("提取") || instruction.contains("extract") || instruction.contains("获取")) {
            val selector = extractSelector(instruction) ?: "body"
            steps.add(TaskStep(
                type = StepType.EXTRACT,
                selector = selector,
                description = "提取内容"
            ))
        }

        if (steps.isEmpty()) {
            steps.add(TaskStep(
                type = StepType.SCREENSHOT,
                description = "截图查看当前页面"
            ))
        }

        return AgentTask(
            name = instruction.take(30),
            description = instruction,
            steps = steps
        )
    }

    private fun extractSelector(instruction: String): String? {
        // 尝试提取 CSS 选择器
        val cssPattern = Regex("[#.]?[a-zA-Z][a-zA-Z0-9_-]*")
        return cssPattern.find(instruction)?.value
    }

    private fun extractValue(instruction: String): String? {
        // 尝试提取引号中的值
        val quotePattern = Regex("\"([^\"]+)\"|'([^']+)'")
        return quotePattern.find(instruction)?.groupValues?.getOrNull(1)
    }

    private fun parsePlanResponse(content: String): PlanResponse? {
        return try {
            // 尝试直接解析
            gson.fromJson(content, PlanResponse::class.java)
        } catch (e: Exception) {
            try {
                // 尝试提取 JSON 块
                val jsonPattern = Regex("\\{[\\s\\S]*\\}")
                val jsonMatch = jsonPattern.find(content)
                if (jsonMatch != null) {
                    gson.fromJson(jsonMatch.value, PlanResponse::class.java)
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun PlanResponse.toTask(): AgentTask {
        return AgentTask(
            name = this.name,
            description = this.description,
            steps = this.steps.map { ps ->
                TaskStep(
                    type = try { StepType.valueOf(ps.type.uppercase()) } catch (_: Exception) { StepType.SCREENSHOT },
                    selector = ps.selector,
                    value = ps.value,
                    description = ps.description ?: ""
                )
            }
        )
    }

    private fun AgentTask.toPlanFormat(): Map<String, Any?> {
        return mapOf(
            "name" to name,
            "description" to description,
            "steps" to steps.map { step ->
                mapOf(
                    "type" to step.type.name,
                    "selector" to step.selector,
                    "value" to step.value,
                    "description" to step.description
                )
            }
        )
    }
}

sealed class PlanResult {
    data object Planning : PlanResult()
    data class Success(val task: AgentTask) : PlanResult()
    data class Error(val message: String) : PlanResult()
}
