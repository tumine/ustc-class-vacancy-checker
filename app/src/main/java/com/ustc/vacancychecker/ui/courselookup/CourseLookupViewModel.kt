package com.ustc.vacancychecker.ui.courselookup

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ustc.vacancychecker.data.local.CourseRepository
import com.ustc.vacancychecker.data.model.TrackedCourse
import com.ustc.vacancychecker.data.remote.CatalogCourseResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

@HiltViewModel
class CourseLookupViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val catalogCourseResolver: CatalogCourseResolver
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

        viewModelScope.launch {
            try {
                uiState = uiState.copy(isResolvingTracking = true, errorMessage = null)
                val defaultAutoSelect = courseRepository.isAutoSelectEnabled()
                val selected = uiState.results.filter { selectedCodes.contains(it.classCode) }
                val metadata = catalogCourseResolver.resolve(selected.map { it.classCode })
                val unresolved = selected.filterNot { metadata.containsKey(it.classCode) }
                if (unresolved.isNotEmpty()) {
                    uiState = uiState.copy(
                        isResolvingTracking = false,
                        errorMessage = "无法从课程目录确认以下课堂的课程标识，未加入跟踪：${unresolved.joinToString { it.classCode }}"
                    )
                    return@launch
                }
                val coursesToTrack = selected
                    .map {
                        val authority = metadata.getValue(it.classCode)
                        TrackedCourse(
                            courseId = it.classCode,
                            courseName = it.courseName,
                            courseKey = authority.courseKey,
                            courseNumber = authority.courseNumber,
                            teacher = it.teacher,
                            isMonitoring = true,
                            autoSelectEnabled = defaultAutoSelect
                        )
                    }

                val existing = courseRepository.getTrackedCourses()
                val conflicts = coursesToTrack.groupBy { it.courseKey }.mapNotNull { (key, additions) ->
                    val tracked = existing.filter { it.courseKey == key && additions.none { added -> added.courseId == it.courseId } }
                    if (tracked.isEmpty()) null else additions to tracked
                }
                if (conflicts.isNotEmpty()) {
                    val summary = conflicts.joinToString("\n\n") { (additions, tracked) ->
                        "${additions.first().courseName}\n已有：${tracked.joinToString { it.courseId }}\n新增：${additions.joinToString { it.courseId }}"
                    }
                    uiState = uiState.copy(
                        isResolvingTracking = false,
                        pendingCoursesToTrack = coursesToTrack,
                        trackingConflictMessage = "以下课堂将加入已有课程组，并按组内优先级依次尝试：\n\n$summary"
                    )
                } else {
                    commitTracking(coursesToTrack)
                }
            } catch (e: Exception) {
                Log.e("CourseLookup", "Failed to add courses to tracking", e)
                uiState = uiState.copy(isResolvingTracking = false, errorMessage = "加入跟踪失败: ${e.message}")
            }
        }
    }

    fun confirmTrackingAdd() {
        val pending = uiState.pendingCoursesToTrack
        if (pending.isEmpty()) return
        viewModelScope.launch {
            try {
                commitTracking(pending)
            } catch (e: Exception) {
                uiState = uiState.copy(errorMessage = "加入跟踪失败: ${e.message}")
            }
        }
    }

    fun cancelTrackingAdd() {
        uiState = uiState.copy(pendingCoursesToTrack = emptyList(), trackingConflictMessage = null)
    }

    private suspend fun commitTracking(courses: List<TrackedCourse>) {
        courseRepository.addTrackedCourses(courses)
        uiState = uiState.copy(
            isResolvingTracking = false,
            selectedForTracking = emptySet(),
            pendingCoursesToTrack = emptyList(),
            trackingConflictMessage = null,
            showSuccessMessage = "成功添加 ${courses.size} 个课堂到后台跟踪列表"
        )
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
    val showSuccessMessage: String? = null,
    val isResolvingTracking: Boolean = false,
    val pendingCoursesToTrack: List<TrackedCourse> = emptyList(),
    val trackingConflictMessage: String? = null
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
