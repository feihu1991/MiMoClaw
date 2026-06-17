package com.xiaomi.mimoclaw.agent.observation

import com.xiaomi.mimoclaw.agent.webcontroller.WebController
import com.xiaomi.mimoclaw.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observation System - 执行结果感知层
 * 每一步执行后收集页面状态，用于判断成功/失败和提供修复上下文
 */
@Singleton
class ObservationSystem @Inject constructor(
    private val webController: WebController
) {

    /**
     * 执行步骤后收集 Observation
     */
    suspend fun observe(
        stepId: String,
        actionResult: ActionResult,
        step: TaskStep
    ): Observation = withContext(Dispatchers.IO) {
        try {
            val url = webController.getCurrentUrl()
            val title = webController.getPageTitle()
            val screenshotResult = webController.screenshot()
            val pageSourceResult = webController.getPageSource()

            val pageState = analyzePageState(pageSourceResult.data, url, title)

            Observation(
                stepId = stepId,
                success = actionResult.success,
                pageState = pageState,
                error = if (!actionResult.success) actionResult.message else null,
                screenshot = screenshotResult.data,
                pageSource = pageSourceResult.data?.take(5000), // 限制大小
                currentUrl = url,
                pageTitle = title,
                domExists = checkDomExists(step.selector, pageSourceResult.data),
                extractedText = actionResult.data
            )
        } catch (e: Exception) {
            Observation(
                stepId = stepId,
                success = false,
                pageState = PageState("", ""),
                error = e.message
            )
        }
    }

    /**
     * 快速观察（不截图，只收集基本信息）
     */
    suspend fun quickObserve(stepId: String, success: Boolean, error: String? = null): Observation {
        return try {
            val url = webController.getCurrentUrl()
            val title = webController.getPageTitle()

            Observation(
                stepId = stepId,
                success = success,
                pageState = PageState(url, title),
                error = error,
                currentUrl = url,
                pageTitle = title
            )
        } catch (e: Exception) {
            Observation(
                stepId = stepId,
                success = success,
                pageState = PageState("", ""),
                error = error ?: e.message
            )
        }
    }

    /**
     * 检查 DOM 中是否存在目标元素
     */
    private suspend fun checkDomExists(selector: String?, pageSource: String?): Boolean {
        if (selector == null || pageSource == null) return true
        // 简单检查：selector 关键词是否在页面源码中出现
        val selectorParts = selector.split(".", "#", "[", " ", ">").filter { it.isNotBlank() }
        return selectorParts.any { part -> pageSource.contains(part, ignoreCase = true) }
    }

    /**
     * 分析页面状态
     */
    private fun analyzePageState(pageSource: String?, url: String, title: String): PageState {
        if (pageSource == null) return PageState(url, title)

        val hasLoginForm = pageSource.contains("password", ignoreCase = true) &&
                (pageSource.contains("login", ignoreCase = true) ||
                 pageSource.contains("登录", ignoreCase = true))

        val hasSearchForm = pageSource.contains("search", ignoreCase = true) &&
                (pageSource.contains("input", ignoreCase = true) ||
                 pageSource.contains("搜索", ignoreCase = true))

        val elementCount = pageSource.split("<").size - 1

        // 提取可见文本（简化版）
        val textRegex = Regex(">([^<]+)<")
        val visibleText = textRegex.findAll(pageSource)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() && it.length > 2 }
            .take(50)
            .joinToString(" ")

        return PageState(
            url = url,
            title = title,
            hasLoginForm = hasLoginForm,
            hasSearchForm = hasSearchForm,
            elementCount = elementCount,
            visibleText = visibleText.take(1000)
        )
    }
}
