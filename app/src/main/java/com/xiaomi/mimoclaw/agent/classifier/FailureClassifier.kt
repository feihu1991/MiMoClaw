package com.xiaomi.mimoclaw.agent.classifier

import com.xiaomi.mimoclaw.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 错误分类器 - 根据错误信息和Observation自动分类失败原因
 * 不同错误类型对应不同修复策略
 */
@Singleton
class FailureClassifier @Inject constructor() {

    /**
     * 分类错误并返回修复策略
     */
    fun classify(
        step: TaskStep,
        actionResult: ActionResult?,
        observation: Observation?,
        exception: Exception?
    ): FailureClassification {
        val errorMsg = actionResult?.message ?: exception?.message ?: observation?.error ?: ""

        // DOM 未找到
        if (isDomNotFound(errorMsg, actionResult)) {
            return FailureClassification(
                type = FailureType.DOM_NOT_FOUND,
                message = "DOM元素未找到: ${step.selector}",
                shouldRetry = false,
                shouldUseVision = true,
                shouldUseLLMRepair = false,
                waitBeforeRetryMs = 0
            )
        }

        // JS 执行错误
        if (isJsError(errorMsg, exception)) {
            return FailureClassification(
                type = FailureType.JS_ERROR,
                message = "JS执行错误: $errorMsg",
                shouldRetry = true,
                shouldUseVision = false,
                shouldUseLLMRepair = false,
                waitBeforeRetryMs = 1000
            )
        }

        // 超时
        if (isTimeout(errorMsg, exception)) {
            return FailureClassification(
                type = FailureType.TIMEOUT,
                message = "操作超时: $errorMsg",
                shouldRetry = true,
                shouldUseVision = false,
                shouldUseLLMRepair = false,
                waitBeforeRetryMs = 2000
            )
        }

        // 页面变化
        if (isPageChange(errorMsg, observation)) {
            return FailureClassification(
                type = FailureType.PAGE_CHANGE,
                message = "页面结构变化: $errorMsg",
                shouldRetry = false,
                shouldUseVision = false,
                shouldUseLLMRepair = true,
                waitBeforeRetryMs = 0
            )
        }

        // 导航错误
        if (isNavigationError(errorMsg)) {
            return FailureClassification(
                type = FailureType.NAVIGATION_ERROR,
                message = "导航失败: $errorMsg",
                shouldRetry = true,
                shouldUseVision = false,
                shouldUseLLMRepair = false,
                waitBeforeRetryMs = 2000
            )
        }

        // 网络错误
        if (isNetworkError(errorMsg, exception)) {
            return FailureClassification(
                type = FailureType.NETWORK_ERROR,
                message = "网络错误: $errorMsg",
                shouldRetry = true,
                shouldUseVision = false,
                shouldUseLLMRepair = false,
                waitBeforeRetryMs = 3000
            )
        }

        // 元素不可点击
        if (isNotClickable(errorMsg)) {
            return FailureClassification(
                type = FailureType.ELEMENT_NOT_CLICKABLE,
                message = "元素不可点击: $errorMsg",
                shouldRetry = true,
                shouldUseVision = false,
                shouldUseLLMRepair = false,
                waitBeforeRetryMs = 1500
            )
        }

        // 未知错误
        return FailureClassification(
            type = FailureType.UNKNOWN,
            message = "未知错误: $errorMsg",
            shouldRetry = step.retryCount < 2,
            shouldUseVision = step.retryCount >= 2,
            shouldUseLLMRepair = step.retryCount >= 3,
            waitBeforeRetryMs = 1000
        )
    }

    // ── 识别模式 ──

    private fun isDomNotFound(msg: String, result: ActionResult?): Boolean {
        val keywords = listOf(
            "not_found", "not found", "元素未找到", "element not found",
            "selector", "querySelector", "null", "undefined"
        )
        return keywords.any { msg.contains(it, ignoreCase = true) } ||
                (result?.data == "__NOT_FOUND__")
    }

    private fun isJsError(msg: String, exception: Exception?): Boolean {
        val keywords = listOf(
            "javascript", "js error", "eval", "syntax", "reference error",
            "type error", "JS", "script"
        )
        return keywords.any { msg.contains(it, ignoreCase = true) } ||
                exception is java.lang.RuntimeException
    }

    private fun isTimeout(msg: String, exception: Exception?): Boolean {
        val keywords = listOf(
            "timeout", "超时", "timed out", "wait", "deadline"
        )
        return keywords.any { msg.contains(it, ignoreCase = true) } ||
                exception is kotlinx.coroutines.TimeoutCancellationException
    }

    private fun isPageChange(msg: String, observation: Observation?): Boolean {
        val keywords = listOf(
            "page change", "页面变化", "navigation", "redirect",
            "页面结构", "dom changed"
        )
        return keywords.any { msg.contains(it, ignoreCase = true) } ||
                (observation?.domExists == false)
    }

    private fun isNavigationError(msg: String): Boolean {
        val keywords = listOf(
            "navigation", "navigate", "load", "ERR_", "net::",
            "connection", "refused"
        )
        return keywords.any { msg.contains(it, ignoreCase = true) }
    }

    private fun isNetworkError(msg: String, exception: Exception?): Boolean {
        val keywords = listOf(
            "network", "网络", "connection", "socket", "http",
            "ssl", "tls", "dns"
        )
        return keywords.any { msg.contains(it, ignoreCase = true) } ||
                exception is java.net.UnknownHostException ||
                exception is java.net.SocketTimeoutException
    }

    private fun isNotClickable(msg: String): Boolean {
        val keywords = listOf(
            "not clickable", "不可点击", "not interactable",
            "disabled", "hidden", "obscured"
        )
        return keywords.any { msg.contains(it, ignoreCase = true) }
    }
}
