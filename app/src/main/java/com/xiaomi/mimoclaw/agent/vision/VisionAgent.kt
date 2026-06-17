package com.xiaomi.mimoclaw.agent.vision

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
 * Vision Agent - 当 DOM 不可用时使用视觉模型识别 UI 元素
 * 截图 → 视觉模型分析 → 返回点击坐标
 */
@Singleton
class VisionAgent @Inject constructor(
    private val apiService: MiMoApiService,
    private val gson: Gson
) {
    companion object {
        private const val VISION_SYSTEM_PROMPT = """你是一个UI视觉分析助手。用户会发送一张截图，你需要根据指令找到目标UI元素的位置。

输出格式（必须严格JSON）：
{
  "action": "click|input|scroll",
  "x": 0.5,
  "y": 0.3,
  "text": "",
  "confidence": 0.9,
  "description": "找到登录按钮，位于页面中央偏上"
}

x和y是相对坐标（0-1范围），表示元素在屏幕中的位置：
- x: 0=最左边, 1=最右边
- y: 0=最顶部, 1=最底部

如果找不到目标元素，返回confidence为0。"""
    }

    /**
     * 使用视觉模型分析截图并返回操作建议
     */
    fun analyze(request: VisionRequest): Flow<VisionResult> = flow {
        emit(VisionResult.Analyzing)

        try {
            val prompt = buildString {
                append("当前页面URL: ${request.currentUrl ?: "未知"}\n\n")
                append("用户指令: ${request.instruction}\n\n")
                append("请分析截图，找到目标UI元素的位置。")
            }

            val messages = listOf(
                ApiMessage(role = "system", content = VISION_SYSTEM_PROMPT),
                ApiMessage(role = "user", content = prompt)
            )

            val chatRequest = ChatRequest(
                messages = messages,
                stream = false,
                temperature = 0.2f,
                maxTokens = 500
            )

            val response = apiService.chatCompletions("Bearer dummy", chatRequest)

            if (response.isSuccessful) {
                val content = response.body()?.choices?.firstOrNull()?.message?.content
                if (content != null) {
                    val visionResponse = parseVisionResponse(content)
                    if (visionResponse != null && visionResponse.confidence > 0.3f) {
                        emit(VisionResult.Success(visionResponse))
                        return@flow
                    }
                }
            }

            // Fallback: 使用启发式方法
            val fallback = heuristicAnalysis(request.instruction)
            emit(VisionResult.Success(fallback))
        } catch (e: Exception) {
            // Fallback
            val fallback = heuristicAnalysis(request.instruction)
            emit(VisionResult.Success(fallback))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 启发式分析（视觉模型不可用时的兜底）
     */
    private fun heuristicAnalysis(instruction: String): VisionResponse {
        // 根据常见UI布局猜测位置
        val x: Float
        val y: Float
        val action: String

        when {
            instruction.contains("登录") || instruction.contains("login") -> {
                x = 0.5f; y = 0.4f; action = "click"
            }
            instruction.contains("搜索") || instruction.contains("search") -> {
                x = 0.5f; y = 0.1f; action = "input"
            }
            instruction.contains("提交") || instruction.contains("submit") -> {
                x = 0.5f; y = 0.8f; action = "click"
            }
            instruction.contains("菜单") || instruction.contains("menu") -> {
                x = 0.05f; y = 0.05f; action = "click"
            }
            instruction.contains("关闭") || instruction.contains("close") -> {
                x = 0.95f; y = 0.05f; action = "click"
            }
            instruction.contains("向下") || instruction.contains("scroll down") -> {
                x = 0.5f; y = 0.8f; action = "scroll"
            }
            else -> {
                x = 0.5f; y = 0.5f; action = "click"
            }
        }

        return VisionResponse(
            action = action,
            x = x,
            y = y,
            confidence = 0.4f,
            description = "启发式分析: $instruction"
        )
    }

    private fun parseVisionResponse(content: String): VisionResponse? {
        return try {
            gson.fromJson(content, VisionResponse::class.java)
        } catch (e: Exception) {
            try {
                val jsonPattern = Regex("\\{[\\s\\S]*\\}")
                val jsonMatch = jsonPattern.find(content)
                if (jsonMatch != null) {
                    gson.fromJson(jsonMatch.value, VisionResponse::class.java)
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}

sealed class VisionResult {
    data object Analyzing : VisionResult()
    data class Success(val response: VisionResponse) : VisionResult()
    data class Error(val message: String) : VisionResult()
}
