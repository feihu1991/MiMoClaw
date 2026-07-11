package com.xiaomi.mimoclaw.core.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaomi.mimoclaw.auth.AuthViewModel
import com.xiaomi.mimoclaw.auth.LoginScreen
import com.xiaomi.mimoclaw.auth.LoginState
import com.xiaomi.mimoclaw.auth.SplashScreen
import com.xiaomi.mimoclaw.feature.chat.ChatScreen
import com.xiaomi.mimoclaw.feature.chat.NativeChatScreen
import com.xiaomi.mimoclaw.feature.settings.SettingsScreen
import com.xiaomi.mimoclaw.feature.task.TaskScreen
import com.xiaomi.mimoclaw.feature.browser.BrowserScreen
import com.xiaomi.mimoclaw.feature.files.FileWorkspaceScreen

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    /** Default product entry: native MiMo Claw workspace. */
    const val HOME = "claw"
    /** Third entry: account, usage and developer console. */
    const val TASKS = "console"
    /** Second entry: official MiMo conversation workspace. */
    const val BROWSER = "mimo_chat"
    /** Native cloud file manager for the Claw workspace. */
    const val FILES = "files"
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
    val navigateTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            launchSingleTop = true
            popUpTo(Routes.HOME) { saveState = true }
            restoreState = true
        }
    }

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
                isReady = splashReady,
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

        // ── Login (小米官方 WebView) ──
        composable(Routes.LOGIN) {
            LoginScreen(
                loginState = loginState,
                onResetState = { authViewModel.resetState() },
                onSsoSuccess = {
                    authViewModel.onSsoLoginSuccess()
                }
            )
        }

        // ── Claw workspace (first entry; requires login) ──
        composable(Routes.HOME) {
            AuthGuard(isLoggedIn = isLoggedIn, navController = navController) {
                ChatScreen(
                    viewModel = hiltViewModel(),
                    onNavigateToBrowser = { navigateTopLevel(Routes.BROWSER) },
                    onNavigateToFiles = { navController.navigate(Routes.FILES) },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }
        }

        // ── Console (third entry) ──
        composable(Routes.TASKS) {
            AuthGuard(isLoggedIn = isLoggedIn, navController = navController) {
                TaskScreen(
                    onHome = { navigateTopLevel(Routes.HOME) },
                    onBrowser = { navigateTopLevel(Routes.BROWSER) },
                    onSettings = { navigateTopLevel(Routes.SETTINGS) },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // ── MiMo Chat (second entry) ──
        composable(Routes.BROWSER) {
            AuthGuard(isLoggedIn = isLoggedIn, navController = navController) {
                NativeChatScreen(
                    viewModel = hiltViewModel(),
                    onClaw = { navigateTopLevel(Routes.HOME) },
                    onFiles = { navController.navigate(Routes.FILES) },
                    onProfile = { navController.navigate(Routes.SETTINGS) }
                )
            }
        }

        composable(Routes.FILES) {
            AuthGuard(isLoggedIn = isLoggedIn, navController = navController) {
                FileWorkspaceScreen(onBack = { navController.popBackStack() })
            }
        }

        // ── Settings (需要登录) ──
        composable(Routes.SETTINGS) {
            AuthGuard(isLoggedIn = isLoggedIn, navController = navController) {
                SettingsScreen(
                    onHome = { navigateTopLevel(Routes.HOME) },
                    onBrowser = { navigateTopLevel(Routes.BROWSER) },
                    onFiles = { navController.navigate(Routes.FILES) },
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
