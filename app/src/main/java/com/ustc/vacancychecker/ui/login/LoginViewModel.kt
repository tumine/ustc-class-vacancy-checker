package com.ustc.vacancychecker.ui.login

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.ustc.vacancychecker.data.local.CredentialsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val credentialsManager: CredentialsManager
) : ViewModel() {
    
    var uiState by mutableStateOf(LoginUiState())
        private set
    
    init {
        checkHasCredentials()
    }
    
    /**
     * WebView 登录成功回调
     * @param username 用户名（学号）
     * @param password 密码
     */
    fun onWebViewLoginSuccess(username: String, password: String) {
        Log.d("LoginViewModel", "onWebViewLoginSuccess: username=$username, passwordLength=${password.length}")
        
        // 加密保存用户名和密码
        if (username.isNotBlank() && password.isNotBlank()) {
            saveCredentials(username, password)
        } else {
            Log.e("LoginViewModel", "Credentials empty, not saving!")
        }
        
        uiState = uiState.copy(isLoggedIn = true)
    }

    fun getCredentials(): Pair<String, String>? {
        val u = credentialsManager.getUsername()
        val p = credentialsManager.getPassword()
        return if (u != null && p != null) u to p else null
    }

    fun checkHasCredentials() {
        uiState = uiState.copy(hasCredentials = credentialsManager.hasCredentials())
    }

    fun saveCredentials(u: String, p: String) {
        Log.d("LoginViewModel", "Saving credentials explicitly")
        credentialsManager.saveCredentials(u, p)
        checkHasCredentials()
    }
    
    fun clearCredentials() {
        Log.d("LoginViewModel", "Clearing credentials")
        credentialsManager.clearCredentials()
        checkHasCredentials()
    }

    fun setErrorMessage(message: String?) {
        uiState = uiState.copy(errorMessage = message)
    }

    fun logout() {
        credentialsManager.clearCredentials()
        uiState = LoginUiState()
    }
}

data class LoginUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val hasCredentials: Boolean = false,
    val errorMessage: String? = null
)
