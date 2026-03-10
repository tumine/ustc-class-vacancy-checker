package com.ustc.vacancychecker.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ustc.vacancychecker.data.local.CourseRepository
import com.ustc.vacancychecker.data.remote.UpdateChecker
import com.ustc.vacancychecker.data.remote.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: CourseRepository,
    private val updateChecker: UpdateChecker
) : ViewModel() {

    val monitoringInterval: StateFlow<Int> = repository.monitoringIntervalFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 60
        )
    
    val autoSelectEnabled: StateFlow<Boolean> = repository.autoSelectEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    var uiState by mutableStateOf(SettingsUiState())
        private set

    fun updateInterval(interval: Int) {
        viewModelScope.launch {
            repository.updateMonitoringInterval(interval)
        }
    }
    
    fun updateAutoSelectEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAutoSelectEnabled(enabled)
        }
    }

    fun checkForUpdate() {
        uiState = uiState.copy(isCheckingUpdate = true, updateError = null, updateInfo = null)
        viewModelScope.launch {
            try {
                val currentVersion = com.ustc.vacancychecker.BuildConfig.VERSION_NAME
                val info = updateChecker.checkForUpdate(currentVersion)
                uiState = uiState.copy(
                    isCheckingUpdate = false,
                    updateInfo = info
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isCheckingUpdate = false,
                    updateError = e.message ?: "检查更新失败"
                )
            }
        }
    }

    fun dismissUpdateDialog() {
        uiState = uiState.copy(updateInfo = null, updateError = null)
    }
}

data class SettingsUiState(
    val isCheckingUpdate: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val updateError: String? = null
)
