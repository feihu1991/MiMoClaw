package com.xiaomi.mimoclaw.core.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.xiaomi.mimoclaw.auth.AuthViewModel
import com.xiaomi.mimoclaw.auth.LoginScreen
import com.xiaomi.mimoclaw.auth.LoginState
import com.xiaomi.mimoclaw.auth.SplashScreen
import com.xiaomi.mimoclaw.feature.browser.BrowserScreen
import com.xiaomi.mimoclaw.feature.home.HomeScreen
import com.xiaomi.mimoclaw.feature.settings.SettingsScreen
import com.xiaomi.mimoclaw.feature.task.TaskScreen
import com.xiaomi.mimoclaw.ui.chat.ChatScreen
import com.xiaomi.mimoclaw.ui.chat.ChatViewModel

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val HOME = "home"
    const val CHAT = "chat"
    const val TASKS = "tasks"
    const val BROWSER = "browser"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val loginState by authViewModel.loginState.collectAsState()
    val splashReady by authViewModel.splashReady.collectAsState()

    // 登录成功后自动跳转首页
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && navController.currentDestination?.route == Routes.LOGIN) {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.LOGIN) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = modifier,
        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(300)) }
    ) {
        // ── Splash ──
        composable(Routes.SPLASH) {
            SplashScreen(
                onSplashFinished = {
                    if (isLoggedIn) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                }
            )
        }

        // ── Login (SSO) ──
        composable(Routes.LOGIN) {
            LoginScreen(
                loginState = loginState,
                onLoginSuccess = { authViewModel.onSsoLoginSuccess() },
                onResetState = { authViewModel.resetState() }
            )
        }

        // ── Home (需要登录) ──
        composable(Routes.HOME) {
            AuthGuard(isLoggedIn = isLoggedIn, navController = navController) {
                HomeScreen(
                    onNavigateToChat = { initialMessage ->
                        val route = if (!initialMessage.isNullOrBlank()) {
                            "${Routes.CHAT}?message=${java.net.URLEncoder.encode(initialMessage, "UTF-8")}"
                        } else {
                            Routes.CHAT
                        }
                        navController.navigate(route)
                    },
                    onNavigateToTasks = { navController.navigate(Routes.TASKS) },
                    onNavigateToBrowser = { navController.navigate(Routes.BROWSER) },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }
        }

        // ── Chat (需要登录) ──
        composable(
            route = "${Routes.CHAT}?message={message}",
            arguments = listOf(
                navArgument("message") {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = true
                }
            )
        ) { backStackEntry ->
            AuthGuard(isLoggedIn = isLoggedIn, navController = navController) {
                val chatViewModel: ChatViewModel = hiltViewModel()
                val chatUiState by chatViewModel.uiState.collectAsState()
                val conversations by chatViewModel.conversations.collectAsState()
                val initialMessage = backStackEntry.arguments?.getString("message")

                // 自动发送初始消息
                LaunchedEffect(initialMessage) {
                    if (!initialMessage.isNullOrBlank()) {
                        val decoded = java.net.URLDecoder.decode(initialMessage, "UTF-8")
                        chatViewModel.sendMessage(decoded)
                    }
                }

                ChatScreen(
                    uiState = chatUiState,
                    conversations = conversations,
                    onSendMessage = chatViewModel::sendMessage,
                    onStopStreaming = chatViewModel::stopStreaming,
                    onNewChat = chatViewModel::newChat,
                    onLoadConversation = chatViewModel::loadConversation,
                    onDeleteConversation = chatViewModel::deleteConversation,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // ── Tasks (需要登录) ──
        composable(Routes.TASKS) {
            AuthGuard(isLoggedIn = isLoggedIn, navController = navController) {
                TaskScreen(onBack = { navController.popBackStack() })
            }
        }

        // ── Browser (需要登录) ──
        composable(Routes.BROWSER) {
            AuthGuard(isLoggedIn = isLoggedIn, navController = navController) {
                BrowserScreen(onBack = { navController.popBackStack() })
            }
        }

        // ── Settings (需要登录) ──
        composable(Routes.SETTINGS) {
            AuthGuard(isLoggedIn = isLoggedIn, navController = navController) {
                SettingsScreen(
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

/**
 * AuthGuard - 登录校验包装器
 * 未登录时自动跳转登录页
 */
@Composable
fun AuthGuard(
    isLoggedIn: Boolean,
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    if (isLoggedIn) {
        content()
    }
}
