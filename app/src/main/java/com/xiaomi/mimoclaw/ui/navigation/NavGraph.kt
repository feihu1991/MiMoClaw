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
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.TASK_DETAIL) {
            TaskDetailScreen(
                task = currentTask,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CONSOLE) {
            RunConsoleScreen(
                task = currentTask,
                logs = logs,
                logText = logText,
                onPause = { viewModel.pause() },
                onResume = { viewModel.resume() },
                onCancel = { viewModel.cancel() },
                onRetry = { viewModel.retry() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.BROWSER) {
            BrowserScreen(
                webController = webController,
                currentUrl = "",
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
