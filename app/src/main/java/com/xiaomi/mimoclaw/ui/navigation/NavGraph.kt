package com.xiaomi.mimoclaw.ui.navigation

import androidx.compose.runtime.Composable
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
    const val HOME = "home"
    const val LOGIN = "login"
    const val TOKEN_LOGIN = "token_login"
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
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {
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

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { cookies ->
                    viewModel.login(cookies, com.xiaomi.mimoclaw.data.model.User(
                        userId = "web_user",
                        nickname = "User",
                        avatar = null,
                        email = null,
                        phone = null
                    ))
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.TOKEN_LOGIN) {
            TokenLoginScreen(
                onTokenSubmit = { token ->
                    viewModel.login(token, com.xiaomi.mimoclaw.data.model.User(
                        userId = "token_user",
                        nickname = "API User",
                        avatar = null,
                        email = null,
                        phone = null
                    ))
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

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

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    viewModel.logout()
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }

        composable(Routes.SUBSCRIBE) {
            SubscribeScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.API_SERVICE) {
            ApiServiceScreen(onBack = { navController.popBackStack() })
        }
    }
}
