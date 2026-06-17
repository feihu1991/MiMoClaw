package com.xiaomi.mimoclaw.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val _isLoggedIn = MutableStateFlow(authManager.isLoggedIn)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _splashReady = MutableStateFlow(false)
    val splashReady: StateFlow<Boolean> = _splashReady.asStateFlow()

    init {
        // Splash: 检查登录状态
        viewModelScope.launch {
            delay(800) // 最小显示时间
            if (authManager.isLoggedIn) {
                val valid = authManager.ensureValidToken()
                _isLoggedIn.value = valid
            }
            _splashReady.value = true
        }
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("请输入账号和密码")
            return
        }

        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            val result = authManager.login(username, password)
            result.fold(
                onSuccess = {
                    _loginState.value = LoginState.Success
                    _isLoggedIn.value = true
                },
                onFailure = { e ->
                    _loginState.value = LoginState.Error(e.message ?: "登录失败")
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
