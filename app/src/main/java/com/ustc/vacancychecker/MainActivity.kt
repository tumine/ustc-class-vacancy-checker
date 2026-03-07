package com.ustc.vacancychecker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.ustc.vacancychecker.data.local.CredentialsManager
import com.ustc.vacancychecker.ui.navigation.NavGraph
import com.ustc.vacancychecker.ui.navigation.Routes
import com.ustc.vacancychecker.ui.theme.UstcVacancyCheckerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var credentialsManager: CredentialsManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            UstcVacancyCheckerTheme {
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
