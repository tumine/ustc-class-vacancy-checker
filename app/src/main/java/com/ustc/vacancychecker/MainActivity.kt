package com.ustc.vacancychecker

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.ustc.vacancychecker.data.local.CredentialsManager
import com.ustc.vacancychecker.ui.navigation.NavGraph
import com.ustc.vacancychecker.ui.navigation.Routes
import com.ustc.vacancychecker.ui.theme.UstcVacancyCheckerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.ustc.vacancychecker.data.service.CourseMonitoringService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.ustc.vacancychecker.data.local.CourseRepository

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var credentialsManager: CredentialsManager

    @Inject
    lateinit var courseRepository: CourseRepository

    private val showBatteryOptimizationDialog = mutableStateOf(false)
    private var monitoringActive = false
    private var strongBackgroundTrackingActive = false
    private var batteryOptimizationDialogDismissed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 只在存在启用监控的课程时保持前台监控服务运行。服务自身负责读取和响应间隔变化。
        lifecycleScope.launch {
            combine(
                courseRepository.monitoringIntervalFlow,
                courseRepository.trackedCoursesFlow,
                courseRepository.strongBackgroundTrackingFlow
            ) { interval, courses, strongBackgroundTracking ->
                MonitoringState(
                    shouldMonitor = interval > 0 && courses.any { it.isEffectivelyMonitoring },
                    strongBackgroundTracking = strongBackgroundTracking
                )
            }
                .distinctUntilChanged()
                .collectLatest { state ->
                    val strongTrackingWasJustEnabled = state.shouldMonitor &&
                        state.strongBackgroundTracking &&
                        (!monitoringActive || !strongBackgroundTrackingActive)
                    monitoringActive = state.shouldMonitor
                    strongBackgroundTrackingActive = state.strongBackgroundTracking
                    if (state.shouldMonitor) {
                        CourseMonitoringService.start(
                            applicationContext,
                            state.strongBackgroundTracking
                        )
                        if (state.strongBackgroundTracking &&
                            !isIgnoringBatteryOptimizations() &&
                            (strongTrackingWasJustEnabled || !batteryOptimizationDialogDismissed)
                        ) {
                            showBatteryOptimizationDialog.value = true
                        } else if (!state.strongBackgroundTracking) {
                            batteryOptimizationDialogDismissed = false
                            showBatteryOptimizationDialog.value = false
                        }
                    } else {
                        CourseMonitoringService.stop(applicationContext)
                        batteryOptimizationDialogDismissed = false
                        showBatteryOptimizationDialog.value = false
                    }
                }
        }
        
        setContent {
            UstcVacancyCheckerTheme {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val launcher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { _ -> }
                    LaunchedEffect(Unit) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    // 根据是否有保存的凭证决定起始页面
                    val startDestination = if (credentialsManager.hasCredentials()) {
                        Routes.COURSE_CHECK
                    } else {
                        Routes.LOGIN
                    }
                    
                    NavGraph(
                        navController = navController,
                        startDestination = startDestination
                    )
                }

                if (showBatteryOptimizationDialog.value) {
                    AlertDialog(
                        onDismissRequest = ::dismissBatteryOptimizationDialog,
                        title = { Text("允许息屏持续监控") },
                        text = {
                            Text(
                                "Android 的电池优化会在息屏后暂停网络和定时任务。" +
                                    "请在接下来的系统窗口中允许本应用不受电池优化限制，" +
                                    "否则课程空位监控只能在系统唤醒时继续。"
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = ::requestBatteryOptimizationExemption) {
                                Text("前往允许")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = ::dismissBatteryOptimizationDialog) {
                                Text("稍后")
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (monitoringActive && strongBackgroundTrackingActive &&
            isIgnoringBatteryOptimizations()
        ) {
            showBatteryOptimizationDialog.value = false
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean =
        getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(packageName)

    private fun dismissBatteryOptimizationDialog() {
        batteryOptimizationDialogDismissed = true
        showBatteryOptimizationDialog.value = false
    }

    // Course vacancy monitoring is a user-configured task-automation core function and cannot use
    // FCM because the upstream JW service does not publish vacancy changes.
    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationExemption() {
        dismissBatteryOptimizationDialog()
        val packageUri = Uri.parse("package:$packageName")
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
            )
        } catch (_: ActivityNotFoundException) {
            // Some vendor ROMs omit the direct request screen; open the exemption list instead.
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private data class MonitoringState(
        val shouldMonitor: Boolean,
        val strongBackgroundTracking: Boolean
    )
}
