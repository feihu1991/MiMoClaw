package com.xiaomi.mimoclaw.platform.queue

import com.xiaomi.mimoclaw.data.model.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.PriorityBlockingQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Task Queue - 任务队列系统
 * 支持: 优先级调度 / 速率限制 / 重试策略 / 超时处理
 */
@Singleton
class TaskQueue @Inject constructor() {

    // 优先级队列
    private val queue = PriorityBlockingQueue<QueuedTask>(100, compareByDescending { it.priority })
    
    // 状态跟踪
    private val _tasks = MutableStateFlow<List<QueuedTask>>(emptyList())
    val tasks: StateFlow<List<QueuedTask>> = _tasks.asStateFlow()
    
    private val _stats = MutableStateFlow(QueueStats())
    val stats: StateFlow<QueueStats> = _stats.asStateFlow()
    
    // 速率限制
    private val rateLimiter = RateLimiter(maxPerMinute = 30)
    
    /**
     * 入队
     */
    fun enqueue(
        task: AgentTask,
        userId: String,
        priority: Int = 0,
        maxRetries: Int = 3,
        timeoutMs: Long = 300_000
    ): QueuedTask {
        val queuedTask = QueuedTask(
            id = UUID.randomUUID().toString(),
            task = task,
            userId = userId,
            priority = priority,
            maxRetries = maxRetries,
            timeoutMs = timeoutMs,
            status = QueueStatus.PENDING,
            enqueuedAt = System.currentTimeMillis()
        )
        queue.offer(queuedTask)
        updateState()
        return queuedTask
    }
    
    /**
     * 出队（获取下一个待执行任务）
     */
    fun dequeue(): QueuedTask? {
        if (!rateLimiter.tryAcquire()) return null
        
        val task = queue.poll() ?: return null
        val running = task.copy(
            status = QueueStatus.RUNNING,
            startedAt = System.currentTimeMillis()
        )
        updateTask(running)
        updateState()
        return running
    }
    
    /**
     * 标记完成
     */
    fun complete(taskId: String) {
        findTask(taskId)?.let { task ->
            updateTask(task.copy(
                status = QueueStatus.SUCCESS,
                completedAt = System.currentTimeMillis()
            ))
            updateState()
        }
    }
    
    /**
     * 标记失败
     */
    fun fail(taskId: String, error: String) {
        findTask(taskId)?.let { task ->
            if (task.retryCount < task.maxRetries) {
                // 重新入队重试
                updateTask(task.copy(
                    status = QueueStatus.RETRY,
                    retryCount = task.retryCount + 1,
                    error = error
                ))
                queue.offer(task.copy(
                    status = QueueStatus.PENDING,
                    retryCount = task.retryCount + 1,
                    priority = task.priority - 1 // 降低优先级
                ))
            } else {
                updateTask(task.copy(
                    status = QueueStatus.FAILED,
                    error = error,
                    completedAt = System.currentTimeMillis()
                ))
            }
            updateState()
        }
    }
    
    /**
     * 暂停任务
     */
    fun pause(taskId: String) {
        findTask(taskId)?.let { task ->
            updateTask(task.copy(status = QueueStatus.PAUSED))
            updateState()
        }
    }
    
    /**
     * 恢复任务
     */
    fun resume(taskId: String) {
        findTask(taskId)?.let { task ->
            updateTask(task.copy(status = QueueStatus.PENDING))
            queue.offer(task.copy(status = QueueStatus.PENDING))
            updateState()
        }
    }
    
    /**
     * 取消任务
     */
    fun cancel(taskId: String) {
        findTask(taskId)?.let { task ->
            updateTask(task.copy(
                status = QueueStatus.CANCELLED,
                completedAt = System.currentTimeMillis()
            ))
            updateState()
        }
    }
    
    /**
     * 获取指定用户的任务
     */
    fun userTasks(userId: String): List<QueuedTask> {
        return _tasks.value.filter { it.userId == userId }
    }
    
    /**
     * 获取指定状态的任务
     */
    fun tasksByStatus(status: QueueStatus): List<QueuedTask> {
        return _tasks.value.filter { it.status == status }
    }
    
    private fun findTask(taskId: String): QueuedTask? {
        return _tasks.value.find { it.id == taskId }
    }
    
    private fun updateTask(task: QueuedTask) {
        _tasks.value = _tasks.value.map { if (it.id == task.id) task else it }
    }
    
    private fun updateState() {
        val allTasks = _tasks.value
        _stats.value = QueueStats(
            total = allTasks.size,
            pending = allTasks.count { it.status == QueueStatus.PENDING },
            running = allTasks.count { it.status == QueueStatus.RUNNING },
            completed = allTasks.count { it.status == QueueStatus.SUCCESS },
            failed = allTasks.count { it.status == QueueStatus.FAILED },
            retrying = allTasks.count { it.status == QueueStatus.RETRY }
        )
    }
}

data class QueuedTask(
    val id: String,
    val task: AgentTask,
    val userId: String,
    val priority: Int = 0,
    val maxRetries: Int = 3,
    val retryCount: Int = 0,
    val timeoutMs: Long = 300_000,
    val status: QueueStatus,
    val error: String? = null,
    val enqueuedAt: Long = 0,
    val startedAt: Long? = null,
    val completedAt: Long? = null
) {
    val durationMs: Long?
        get() = if (startedAt != null && completedAt != null) completedAt - startedAt else null
}

enum class QueueStatus {
    PENDING,    // 等待执行
    RUNNING,    // 执行中
    PAUSED,     // 已暂停
    RETRY,      // 重试中
    FAILED,     // 失败
    SUCCESS,    // 成功
    CANCELLED   // 已取消
}

data class QueueStats(
    val total: Int = 0,
    val pending: Int = 0,
    val running: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val retrying: Int = 0
)

/**
 * 速率限制器
 */
class RateLimiter(private val maxPerMinute: Int) {
    private val timestamps = ConcurrentLinkedQueue<Long>()
    
    fun tryAcquire(): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = now - 60_000
        
        // 清理过期时间戳
        while (timestamps.peek() != null && timestamps.peek() < windowStart) {
            timestamps.poll()
        }
        
        if (timestamps.size >= maxPerMinute) return false
        
        timestamps.offer(now)
        return true
    }
}
