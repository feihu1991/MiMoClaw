package com.xiaomi.mimoclaw.platform.agent

import com.xiaomi.mimoclaw.agent.planner.LLMPlanner
import com.xiaomi.mimoclaw.agent.planner.PlanResult
import com.xiaomi.mimoclaw.data.model.*
import com.xiaomi.mimoclaw.platform.event.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PlannerAgent - 任务规划Agent
 * 职责: 将自然语言转换为Task DAG
 * 接收: 用户输入 / 修复请求
 * 输出: Task计划 → 发送给Orchestrator
 */
@Singleton
class PlannerAgent @Inject constructor(
    private val planner: LLMPlanner,
    private val eventBus: AgentEventBus
) {
    val agentId = "planner-001"
    
    /**
     * 规划任务并发送到EventBus
     */
    fun plan(instruction: String, taskId: String): Flow<PlanResult> = flow {
        // 发布规划开始事件
        eventBus.publish(
            agentId = agentId,
            agentType = AgentType.PLANNER,
            type = MessageType.PLAN_CREATED,
            taskId = taskId,
            payload = MessagePayload(instruction = instruction)
        )
        
        planner.plan(PlanRequest(instruction = instruction)).collect { result ->
            when (result) {
                is PlanResult.Planning -> {
                    eventBus.publish(
                        agentId = agentId,
                        agentType = AgentType.PLANNER,
                        type = MessageType.LOG,
                        taskId = taskId,
                        payload = MessagePayload(instruction = "规划中...")
                    )
                }
                is PlanResult.Success -> {
                    eventBus.publish(
                        agentId = agentId,
                        agentType = AgentType.PLANNER,
                        type = MessageType.PLAN_CREATED,
                        taskId = taskId,
                        payload = MessagePayload(
                            instruction = instruction,
                            metadata = mapOf(
                                "taskName" to result.task.name,
                                "stepCount" to result.task.steps.size.toString()
                            )
                        ),
                        result = MessageResult(success = true, data = result.task.name)
                    )
                }
                is PlanResult.Error -> {
                    eventBus.publish(
                        agentId = agentId,
                        agentType = AgentType.PLANNER,
                        type = MessageType.ERROR,
                        taskId = taskId,
                        payload = MessagePayload(error = result.message)
                    )
                }
            }
            emit(result)
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    
    /**
     * 修复任务
     */
    fun repair(
        originalTask: AgentTask,
        failedStep: TaskStep,
        error: String,
        taskId: String
    ): Flow<PlanResult> = flow {
        eventBus.publish(
            agentId = agentId,
            agentType = AgentType.PLANNER,
            type = MessageType.REPAIR_SUGGESTED,
            taskId = taskId,
            payload = MessagePayload(
                stepIndex = originalTask.steps.indexOf(failedStep),
                error = error
            )
        )
        
        planner.fixTask(originalTask, failedStep, error).collect { result ->
            emit(result)
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)
}
