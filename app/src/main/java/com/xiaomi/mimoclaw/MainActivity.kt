package com.xiaomi.mimoclaw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.xiaomi.mimoclaw.auth.AuthViewModel
import com.xiaomi.mimoclaw.core.navigation.AppNavigation
import com.xiaomi.mimoclaw.core.theme.MiMoTheme
import com.xiaomi.mimoclaw.core.update.UpdateDialog
import com.xiaomi.mimoclaw.core.update.UpdateViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MiMoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val authViewModel: AuthViewModel = hiltViewModel()
                    val updateViewModel: UpdateViewModel = hiltViewModel()
                    val updateState by updateViewModel.updateState.collectAsState()
                    val updateInfo by updateViewModel.updateInfo.collectAsState()

                    UpdateDialog(
                        updateState = updateState,
                        updateInfo = updateInfo,
                        onUpdate = updateViewModel::startDownload,
                        onDismiss = updateViewModel::dismissUpdate,
                        onRetry = updateViewModel::checkUpdate
                    )

                    AppNavigation(
                        navController = navController,
                        authViewModel = authViewModel
                    )
                }
            }
        }
    }
}
