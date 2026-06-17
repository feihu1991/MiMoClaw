package com.xiaomi.mimoclaw.core.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.xiaomi.mimoclaw.auth.AuthViewModel
import com.xiaomi.mimoclaw.auth.LoginScreen
import com.xiaomi.mimoclaw.auth.LoginState
import com.xiaomi.mimoclaw.auth.SplashScreen
import com.xiaomi.mimoclaw.feature.browser.BrowserScreen
import com.xiaomi.mimoclaw.feature.home.HomeScreen
import com.xiaomi.mimoclaw.feature.settings.SettingsScreen
import com.xiaomi.mimoclaw.feature.task.TaskScreen

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val HOME = "home"
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

        // ── Login ──
        composable(Routes.LOGIN) {
            LoginScreen(
                loginState = loginState,
                onLogin = { username, password ->
                    authViewModel.login(username, password)
                },
                onResetState = { authViewModel.resetState() }
            )
        }

        // ── Home (需要登录) ──
        composable(Routes.HOME) {
            AuthGuard(isLoggedIn = isLoggedIn, navController = navController) {
                HomeScreen(
                    onNavigateToTasks = { navController.navigate(Routes.TASKS) },
                    onNavigateToBrowser = { navController.navigate(Routes.BROWSER) },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
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
