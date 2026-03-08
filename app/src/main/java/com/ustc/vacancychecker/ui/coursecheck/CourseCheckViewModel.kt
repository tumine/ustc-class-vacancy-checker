package com.ustc.vacancychecker.ui.coursecheck

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.ustc.vacancychecker.data.local.CredentialsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CourseCheckViewModel @Inject constructor(
    private val credentialsManager: CredentialsManager
) : ViewModel() {
    
    var uiState by mutableStateOf(CourseCheckUiState())
        private set
    
    fun updateClassCode(code: String) {
        uiState = uiState.copy(classCode = code)
    }

    fun addCourseToTrack(code: String) {
        if (code.isNotBlank() && !uiState.trackedCourses.contains(code)) {
            uiState = uiState.copy(trackedCourses = uiState.trackedCourses + code)
        }
    }
    
    fun removeTrackedCourse(code: String) {
        uiState = uiState.copy(trackedCourses = uiState.trackedCourses - code)
    }
    
    fun startCheck(code: String = uiState.classCode) {
        if (code.isBlank()) {
            uiState = uiState.copy(errorMessage = "请输入课堂号")
            return
        }
        uiState = uiState.copy(
            classCode = code, // 确保如果是从列表点击，输入框也更新
            isChecking = true,
            showWebView = true,
            result = null,
            errorMessage = null
        )
    }
    
    fun onNotInSelectTime() {
        Log.d("CourseCheck", "Not in course select time")
        uiState = uiState.copy(
            isChecking = false,
            showWebView = false,
            errorMessage = "当前不在选课时间内"
        )
    }
    
    fun onCourseNotFound() {
        Log.d("CourseCheck", "Course not found: ${uiState.classCode}")
        uiState = uiState.copy(
            isChecking = false,
            showWebView = false,
            errorMessage = "未找到课堂号为 ${uiState.classCode} 的课程"
        )
    }
    
    fun onVacancyResult(stdCount: Int, limitCount: Int) {
        Log.d("CourseCheck", "Vacancy result: $stdCount/$limitCount")
        val hasVacancy = stdCount < limitCount
        uiState = uiState.copy(
            isChecking = false,
            showWebView = false,
            result = VacancyResult(
                stdCount = stdCount,
                limitCount = limitCount,
                hasVacancy = hasVacancy
            )
        )
    }
    
    fun dismissResult() {
        uiState = uiState.copy(result = null, errorMessage = null)
    }
    
    fun getCredentials(): Pair<String, String>? {
        val u = credentialsManager.getUsername()
        val p = credentialsManager.getPassword()
        return if (u != null && p != null) u to p else null
    }
}

data class CourseCheckUiState(
    val classCode: String = "",
    val trackedCourses: List<String> = emptyList(),
    val isChecking: Boolean = false,
    val showWebView: Boolean = false,
    val result: VacancyResult? = null,
    val errorMessage: String? = null
)

data class VacancyResult(
    val stdCount: Int,
    val limitCount: Int,
    val hasVacancy: Boolean
)
