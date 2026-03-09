package com.ustc.vacancychecker

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.ustc.vacancychecker.data.local.CredentialsManager
import com.ustc.vacancychecker.ui.navigation.NavGraph
import com.ustc.vacancychecker.ui.navigation.Routes
import com.ustc.vacancychecker.ui.theme.UstcVacancyCheckerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkManager
import com.ustc.vacancychecker.data.worker.ClassVacancyWorker
import java.util.concurrent.TimeUnit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.ustc.vacancychecker.data.local.CourseRepository

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var credentialsManager: CredentialsManager
    
    @Inject
    lateinit var courseRepository: CourseRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Stop any old periodic work
        WorkManager.getInstance(applicationContext).cancelUniqueWork("VacancyCheckWork")

        lifecycleScope.launch {
            courseRepository.monitoringIntervalFlow.collectLatest { interval ->
                if (interval > 0) {
                    val workRequest = ClassVacancyWorker.buildOneTimeRequest(interval.toLong(), recursive = true)
                    WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                        ClassVacancyWorker.WORK_NAME,
                        ExistingWorkPolicy.REPLACE,
                        workRequest
                    )
                } else {
                    WorkManager.getInstance(applicationContext).cancelUniqueWork(ClassVacancyWorker.WORK_NAME)
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
            }
        }
    }
}
