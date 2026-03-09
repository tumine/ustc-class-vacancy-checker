package com.ustc.vacancychecker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ustc.vacancychecker.ui.coursecheck.CourseCheckScreen
import com.ustc.vacancychecker.ui.courselookup.CourseLookupScreen
import com.ustc.vacancychecker.ui.login.LoginScreen
import com.ustc.vacancychecker.ui.settings.SettingsScreen
import com.ustc.vacancychecker.ui.tracker.TrackerScreen

object Routes {
    const val LOGIN = "login"
    const val COURSE_CHECK = "course_check"
    const val SETTINGS = "settings"
    const val COURSE_LOOKUP = "course_lookup"
    const val TRACKER = "tracker"
}

/** savedStateHandle key for passing selected class code back from CourseLookup */
const val KEY_SELECTED_CLASS_CODE = "selected_class_code"

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
        
        composable(Routes.COURSE_CHECK) { backStackEntry ->
            // 监听从 CourseLookup 页面返回的课堂号
            val selectedClassCode by backStackEntry
                .savedStateHandle
                .getStateFlow<String?>(KEY_SELECTED_CLASS_CODE, null)
                .collectAsState()
            
            CourseCheckScreen(
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToCourseLookup = {
                    navController.navigate(Routes.COURSE_LOOKUP)
                },
                onNavigateToTracker = {
                    navController.navigate(Routes.TRACKER)
                },
                selectedClassCode = selectedClassCode,
                onSelectedClassCodeConsumed = {
                    backStackEntry.savedStateHandle.remove<String>(KEY_SELECTED_CLASS_CODE)
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
        
        composable(Routes.COURSE_LOOKUP) {
            CourseLookupScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onCourseSelected = { classCode ->
                    // 将选中的课堂号传递回 CourseCheck 页面
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_SELECTED_CLASS_CODE, classCode)
                    navController.popBackStack()
                }
            )
        }
        
        composable(Routes.TRACKER) {
            TrackerScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
