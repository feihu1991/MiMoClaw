package com.xiaomi.mimoclaw.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.xiaomi.mimoclaw.agent.webcontroller.WebController
import com.xiaomi.mimoclaw.ui.AgentViewModel
import com.xiaomi.mimoclaw.ui.screen.*

object Routes {
    const val HOME = "home"
    const val TASK_DETAIL = "task_detail"
    const val CONSOLE = "console"
    const val BROWSER = "browser"
    const val SETTINGS = "settings"
    const val DEBUG_PANEL = "debug_panel"
    const val QUEUE_DASHBOARD = "queue_dashboard"
    const val MULTI_AGENT = "multi_agent"
    const val BILLING = "billing"
}

@Composable
fun AgentNavGraph(
    navController: NavHostController,
    webController: WebController,
    viewModel: AgentViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val currentTask by viewModel.currentTask.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val logText by viewModel.logText.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val loopState by viewModel.loopState.collectAsState()
    val observations by viewModel.observations.collectAsState()
    val checkpoint by viewModel.checkpoint.collectAsState()
    val queueStats by viewModel.queueStats.collectAsState()
    val queuedTasks by viewModel.queuedTasks.collectAsState()
    val eventHistory by viewModel.eventHistory.collectAsState()
    val dailyUsage by viewModel.dailyUsage.collectAsState()
    val usageSummary by viewModel.usageSummary.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val quota by viewModel.quota.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                currentTask = currentTask,
                inputText = inputText,
                onInputChange = { viewModel.updateInput(it) },
                onExecute = { viewModel.execute() },
                onNavigateToDetail = { navController.navigate(Routes.TASK_DETAIL) },
                onNavigateToConsole = { navController.navigate(Routes.CONSOLE) },
                onNavigateToBrowser = { navController.navigate(Routes.BROWSER) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToDebug = { navController.navigate(Routes.DEBUG_PANEL) },
                onNavigateToQueue = { navController.navigate(Routes.QUEUE_DASHBOARD) },
                onNavigateToMultiAgent = { navController.navigate(Routes.MULTI_AGENT) },
                onNavigateToBilling = { navController.navigate(Routes.BILLING) }
            )
        }

        composable(Routes.TASK_DETAIL) {
            TaskDetailScreen(task = currentTask, onBack = { navController.popBackStack() })
        }

        composable(Routes.CONSOLE) {
            RunConsoleScreen(
                task = currentTask, logs = logs, logText = logText,
                onPause = { viewModel.pause() }, onResume = { viewModel.resume() },
                onCancel = { viewModel.cancel() }, onRetry = { viewModel.retry() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.BROWSER) {
            BrowserScreen(webController = webController, currentUrl = "", onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.DEBUG_PANEL) {
            DebugPanelScreen(
                task = currentTask, loopState = loopState, observations = observations,
                checkpoint = checkpoint, logs = logs, onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.QUEUE_DASHBOARD) {
            QueueDashboardScreen(
                tasks = queuedTasks, stats = queueStats,
                onPause = { viewModel.pauseTask(it) },
                onResume = { viewModel.resumeTask(it) },
                onCancel = { viewModel.cancelTask(it) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.MULTI_AGENT) {
            MultiAgentScreen(
                activeTasks = emptyMap(),
                eventHistory = eventHistory,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.BILLING) {
            BillingScreen(
                user = currentUser, quota = quota,
                dailyUsage = dailyUsage, usageSummary = usageSummary,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
