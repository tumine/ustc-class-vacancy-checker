package com.ustc.vacancychecker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ustc.vacancychecker.ui.coursecheck.CourseCheckScreen
import com.ustc.vacancychecker.ui.login.LoginScreen
import com.ustc.vacancychecker.ui.settings.SettingsScreen

object Routes {
    const val LOGIN = "login"
    const val COURSE_CHECK = "course_check"
    const val SETTINGS = "settings"
}

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.COURSE_CHECK) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Routes.COURSE_CHECK) {
            CourseCheckScreen(
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }
        
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.COURSE_CHECK) { inclusive = true }
                    }
                }
            )
        }
    }
}
