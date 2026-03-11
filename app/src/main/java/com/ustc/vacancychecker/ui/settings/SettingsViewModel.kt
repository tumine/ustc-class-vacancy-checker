package com.ustc.vacancychecker.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ustc.vacancychecker.data.local.CourseRepository
import com.ustc.vacancychecker.data.remote.ApkDownloader
import com.ustc.vacancychecker.data.remote.DownloadState
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
    private val application: Application,
    private val repository: CourseRepository,
    private val updateChecker: UpdateChecker,
    private val apkDownloader: ApkDownloader
) : ViewModel() {

    val monitoringInterval: StateFlow<Int> = repository.monitoringIntervalFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 60
        )

    var uiState by mutableStateOf(SettingsUiState())
        private set

    val downloadState: StateFlow<DownloadState> = apkDownloader.downloadState

    fun updateInterval(interval: Int) {
        viewModelScope.launch {
            repository.updateMonitoringInterval(interval)
        }
    }

    fun checkForUpdate() {
        uiState = uiState.copy(isCheckingUpdate = true, updateError = null, updateInfo = null)
        viewModelScope.launch {
            try {
                val currentVersion = com.ustc.vacancychecker.BuildConfig.VERSION_NAME
                val isDebug = com.ustc.vacancychecker.BuildConfig.DEBUG
                val info = updateChecker.checkForUpdate(currentVersion, isDebug)
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

    fun startDownload() {
        val info = uiState.updateInfo ?: return
        val downloadUrl = info.apkDownloadUrl ?: return
        val fileName = info.apkFileName ?: "update.apk"
        
        apkDownloader.startDownload(application, downloadUrl, fileName)
        // 关闭所有对话框，让用户可以继续使用应用
        uiState = uiState.copy(showDownloadConfirmDialog = false, updateInfo = null)
    }
    
    fun needsNotificationPermission(): Boolean {
        return !apkDownloader.hasNotificationPermission(application)
    }

    fun installApk() {
        val state = downloadState.value
        if (state is DownloadState.Completed) {
            apkDownloader.installApk(application, state.fileUri)
        }
    }

    fun showDownloadConfirmDialog() {
        uiState = uiState.copy(showDownloadConfirmDialog = true)
    }

    fun dismissUpdateDialog() {
        uiState = uiState.copy(updateInfo = null, updateError = null, showDownloadConfirmDialog = false)
    }

    fun resetDownload() {
        apkDownloader.reset()
    }
    
    override fun onCleared() {
        super.onCleared()
        apkDownloader.cleanup(application)
    }
}

data class SettingsUiState(
    val isCheckingUpdate: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val updateError: String? = null,
    val showDownloadConfirmDialog: Boolean = false
)
