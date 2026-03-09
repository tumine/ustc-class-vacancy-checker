package com.ustc.vacancychecker.ui.courselookup

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ustc.vacancychecker.data.local.CourseRepository
import com.ustc.vacancychecker.data.model.TrackedCourse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

@HiltViewModel
class CourseLookupViewModel @Inject constructor(
    private val courseRepository: CourseRepository
) : ViewModel() {

    var uiState by mutableStateOf(CourseLookupUiState())
        private set

    fun updateKeyword(keyword: String) {
        uiState = uiState.copy(keyword = keyword)
    }

    fun updateSearchType(type: SearchType) {
        if (uiState.searchType != type) {
            uiState = uiState.copy(
                searchType = type,
                keyword = "",
                results = emptyList(),
                errorMessage = null,
                warningMessage = null
            )
        }
    }

    fun startSearch() {
        if (uiState.keyword.isBlank()) {
            uiState = uiState.copy(errorMessage = "请输入搜索关键字")
            return
        }
        uiState = uiState.copy(
            isSearching = true,
            showWebView = true,
            results = emptyList(),
            errorMessage = null,
            warningMessage = null
        )
    }

    fun onSearchResults(json: String) {
        Log.d("CourseLookup", "Search results received: $json")
        try {
            var coursesArray: JSONArray
            var maxPageReached = false
            
            if (json.trim().startsWith("{")) {
                val jsonObject = org.json.JSONObject(json)
                coursesArray = jsonObject.optJSONArray("data") ?: JSONArray()
                maxPageReached = jsonObject.optBoolean("maxPageReached", false)
            } else {
                coursesArray = JSONArray(json)
            }

            val courses = mutableListOf<CourseInfo>()
            for (i in 0 until coursesArray.length()) {
                val obj = coursesArray.getJSONObject(i)
                courses.add(
                    CourseInfo(
                        classCode = obj.optString("classCode", ""),
                        courseName = obj.optString("courseName", ""),
                        teacher = obj.optString("teacher", "")
                    )
                )
            }

            val warning = if (maxPageReached) "部分课程由于达到显示上限（1 页 25 条）无法显示，建议进一步明确搜索关键字，以获取更精准结果" else null

            uiState = if (courses.isEmpty()) {
                uiState.copy(
                    isSearching = false,
                    showWebView = false,
                    errorMessage = "未找到匹配「${uiState.keyword}」的课程",
                    warningMessage = null
                )
            } else {
                uiState.copy(
                    isSearching = false,
                    showWebView = false,
                    results = courses,
                    errorMessage = null,
                    warningMessage = warning
                )
            }
        } catch (e: Exception) {
            Log.e("CourseLookup", "Failed to parse search results", e)
            uiState = uiState.copy(
                isSearching = false,
                showWebView = false,
                errorMessage = "解析搜索结果失败: ${e.message}",
                warningMessage = null
            )
        }
    }

    fun onSearchError(message: String) {
        Log.e("CourseLookup", "Search error: $message")
        uiState = uiState.copy(
            isSearching = false,
            showWebView = false,
            errorMessage = message,
            warningMessage = null
        )
    }

    fun toggleSelection(classCode: String) {
        val currentSelected = uiState.selectedForTracking.toMutableSet()
        if (currentSelected.contains(classCode)) {
            currentSelected.remove(classCode)
        } else {
            currentSelected.add(classCode)
        }
        uiState = uiState.copy(selectedForTracking = currentSelected)
    }

    fun clearSuccessMessage() {
        uiState = uiState.copy(showSuccessMessage = null)
    }

    fun addToTracking() {
        val selectedCodes = uiState.selectedForTracking
        if (selectedCodes.isEmpty()) return

        val coursesToTrack = uiState.results
            .filter { selectedCodes.contains(it.classCode) }
            .map {
                TrackedCourse(
                    courseId = it.classCode,
                    courseName = it.courseName,
                    teacher = it.teacher,
                    isMonitoring = true
                )
            }

        viewModelScope.launch {
            try {
                courseRepository.addTrackedCourses(coursesToTrack)
                uiState = uiState.copy(
                    selectedForTracking = emptySet(),
                    showSuccessMessage = "成功添加 ${coursesToTrack.size} 门课程到后台跟踪列表"
                )
                
                // 立即执行一次余量查询
                performImmediateCheck(coursesToTrack)
            } catch (e: Exception) {
                Log.e("CourseLookup", "Failed to add courses to tracking", e)
                uiState = uiState.copy(errorMessage = "加入跟踪失败: ${e.message}")
            }
        }
    }

    private suspend fun performImmediateCheck(courses: List<TrackedCourse>) {
        try {
            val semesterId = "421"
            val url = "https://catalog.ustc.edu.cn/api/teach/lesson/list-for-teach/$semesterId"

            val responseBody = withContext(Dispatchers.IO) {
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                }
            }

            if (responseBody.isNullOrBlank()) {
                // API 请求失败，仍然更新检测时间
                courseRepository.updateCheckTimeForCourses(courses.map { it.courseId })
                return
            }

            val jsonArray = JSONArray(responseBody)
            val vacancyMap = mutableMapOf<String, Int>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val code = item.optString("code", "")
                val stdCount = item.optInt("stdCount", 0)
                val limitCount = item.optInt("limitCount", 0)
                if (code.isNotBlank()) {
                    val vacancy = maxOf(0, limitCount - stdCount)
                    vacancyMap[code] = vacancy
                    
                    val courseObj = item.optJSONObject("course")
                    val courseCode = courseObj?.optString("code", "") ?: ""
                    if (courseCode.isNotBlank()) {
                        val existing = vacancyMap[courseCode] ?: 0
                        vacancyMap[courseCode] = maxOf(existing, vacancy)
                    }
                }
            }

            for (course in courses) {
                val vacancy = vacancyMap[course.courseId]
                if (vacancy != null) {
                    courseRepository.updateCourseStatus(course.courseId, vacancy)
                } else {
                    // 未在接口中找到该课程，重置余量并更新检测时间
                    courseRepository.updateCourseStatus(course.courseId, 0)
                }
            }
        } catch (e: Exception) {
            Log.e("CourseLookup", "Immediate vacancy check failed", e)
            courseRepository.updateCheckTimeForCourses(courses.map { it.courseId })
        }
    }
}

data class CourseLookupUiState(
    val keyword: String = "",
    val searchType: SearchType = SearchType.COURSE,
    val isSearching: Boolean = false,
    val showWebView: Boolean = false,
    val results: List<CourseInfo> = emptyList(),
    val errorMessage: String? = null,
    val warningMessage: String? = null,
    val selectedForTracking: Set<String> = emptySet(),
    val showSuccessMessage: String? = null
)

enum class SearchType {
    COURSE, // 课程名/编号
    TEACHER // 授课教师
}

data class CourseInfo(
    val classCode: String,
    val courseName: String,
    val teacher: String
)
