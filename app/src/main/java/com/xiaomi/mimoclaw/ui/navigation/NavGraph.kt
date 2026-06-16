package com.xiaomi.mimoclaw.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.xiaomi.mimoclaw.ui.MainViewModel
import com.xiaomi.mimoclaw.ui.screen.*

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val CHAT = "chat/{mode}/{conversationId}"
    const val SETTINGS = "settings"
    const val SUBSCRIBE = "subscribe"
    const val API_SERVICE = "api_service"

    fun chat(mode: String, conversationId: String = "new") = "chat/$mode/$conversationId"
}

@Composable
fun MiMoNavGraph(
    navController: NavHostController,
    viewModel: MainViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) Routes.HOME else Routes.LOGIN,
        modifier = modifier
    ) {
        // ── 登录页 ──
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { cookies ->
                    viewModel.login(cookies, com.xiaomi.mimoclaw.data.model.User(
                        userId = "web_user",
                        nickname = "用户",
                        avatar = null,
                        email = null,
                        phone = null
                    ))
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onBack = { /* 首页不允许返回 */ }
            )
        }

        // ── 首页 ──
        composable(Routes.HOME) {
            MainScreen(
                onNavigateToLogin = { navController.navigate(Routes.LOGIN) },
                onNavigateToChat = { mode, convId ->
                    navController.navigate(Routes.chat(mode.name, convId))
                },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToSubscribe = { navController.navigate(Routes.SUBSCRIBE) },
                onNavigateToApiService = { navController.navigate(Routes.API_SERVICE) }
            )
        }

        // ── 聊天页 ──
        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("conversationId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "MIMO_CLAW"
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: "new"
            ChatScreenWrapper(
                modeName = mode,
                conversationId = conversationId,
                onBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToSubscribe = { navController.navigate(Routes.SUBSCRIBE) },
                onNavigateToApiService = { navController.navigate(Routes.API_SERVICE) },
                onNavigateToLogin = { navController.navigate(Routes.LOGIN) }
            )
        }

        // ── 设置页 ──
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    viewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        // ── 订阅页 ──
        composable(Routes.SUBSCRIBE) {
            SubscribeScreen(onBack = { navController.popBackStack() })
        }

        // ── API 服务页 ──
        composable(Routes.API_SERVICE) {
            ApiServiceScreen(onBack = { navController.popBackStack() })
        }
    }
}
