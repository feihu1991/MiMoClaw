package com.xiaomi.mimoclaw.auth

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaomi.mimoclaw.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _splashReady = MutableStateFlow(false)
    val splashReady: StateFlow<Boolean> = _splashReady.asStateFlow()

    init {
        viewModelScope.launch {
            delay(500)

            // CookieManager 操作必须在主线程
            val cookies = withContext(Dispatchers.Main) {
                CookieManager.getInstance().getCookie(BuildConfig.API_BASE_URL)
            }
            if (cookies?.contains("serviceToken") == true && cookies.contains("xiaomichatbot_ph")) {
                val valid = authManager.validateLogin()
                if (valid) {
                    _isLoggedIn.value = true
                    _splashReady.value = true
                    return@launch
                }
            }

            // 需要登录 (WebView 输入账号密码)
            _isLoggedIn.value = false
            _splashReady.value = true
        }
    }

    /**
     * WebView 登录成功回调
     */
    fun onSsoLoginSuccess() {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            val result = authManager.fetchUserInfo()
            result.fold(
                onSuccess = {
                    _loginState.value = LoginState.Success
                    _isLoggedIn.value = true
                },
                onFailure = {
                    val cookies = withContext(Dispatchers.Main) {
                        CookieManager.getInstance().getCookie(BuildConfig.API_BASE_URL)
                    }
                    _loginState.value = LoginState.Error(it.message ?: "登录验证失败")
                    _isLoggedIn.value = false
                }
            )
        }
    }

    fun logout() {
        authManager.logout()
        _isLoggedIn.value = false
        _loginState.value = LoginState.Idle
    }

    fun resetState() {
        _loginState.value = LoginState.Idle
    }
}

sealed class LoginState {
    data object Idle : LoginState()
    data object Loading : LoginState()
    data object Success : LoginState()
    data class Error(val message: String) : LoginState()
}
