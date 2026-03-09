package com.ustc.vacancychecker.ui.coursecheck

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ustc.vacancychecker.data.local.CourseRepository
import com.ustc.vacancychecker.data.local.CredentialsManager
import com.ustc.vacancychecker.data.model.TrackedCourse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CourseCheckViewModel @Inject constructor(
    private val credentialsManager: CredentialsManager,
    private val courseRepository: CourseRepository
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
            courseName = null,
            teacher = null,
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
    
    fun onVacancyResult(stdCount: Int, limitCount: Int, courseName: String, teacher: String) {
        Log.d("CourseCheck", "Vacancy result: $stdCount/$limitCount, course: $courseName, teacher: $teacher")
        val hasVacancy = stdCount < limitCount
        uiState = uiState.copy(
            isChecking = false,
            showWebView = false,
            courseName = courseName.ifBlank { "未命名课程" },
            teacher = teacher.ifBlank { "未知" },
            result = VacancyResult(
                stdCount = stdCount,
                limitCount = limitCount,
                hasVacancy = hasVacancy
            )
        )
    }
    
    fun dismissResult() {
        uiState = uiState.copy(result = null, errorMessage = null, showSuccessMessage = null)
    }

    fun clearSuccessMessage() {
        uiState = uiState.copy(showSuccessMessage = null)
    }

    fun addToBackgroundTracking() {
        val code = uiState.classCode
        if (code.isBlank()) return
        
        viewModelScope.launch {
            try {
                val vacancyResult = uiState.result
                courseRepository.addTrackedCourses(listOf(
                    TrackedCourse(
                        courseId = code,
                        courseName = uiState.courseName ?: "未命名课程",
                        teacher = uiState.teacher ?: "未知",
                        isMonitoring = true,
                        lastCheckTime = System.currentTimeMillis(),
                        vacancy = vacancyResult?.let { it.limitCount - it.stdCount }?.takeIf { it >= 0 } ?: 0
                    )
                ))
                uiState = uiState.copy(showSuccessMessage = "成功加入后台跟踪队列")
            } catch (e: Exception) {
                uiState = uiState.copy(errorMessage = "加入跟踪失败: ${e.message}")
            }
        }
    }
    
    fun getCredentials(): Pair<String, String>? {
        val u = credentialsManager.getUsername()
        val p = credentialsManager.getPassword()
        return if (u != null && p != null) u to p else null
    }
}

data class CourseCheckUiState(
    val classCode: String = "",
    val courseName: String? = null,
    val teacher: String? = null,
    val trackedCourses: List<String> = emptyList(),
    val isChecking: Boolean = false,
    val showWebView: Boolean = false,
    val result: VacancyResult? = null,
    val errorMessage: String? = null,
    val showSuccessMessage: String? = null
)

data class VacancyResult(
    val stdCount: Int,
    val limitCount: Int,
    val hasVacancy: Boolean
)
