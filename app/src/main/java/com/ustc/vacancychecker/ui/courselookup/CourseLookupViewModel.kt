package com.ustc.vacancychecker.ui.courselookup

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.json.JSONArray
import javax.inject.Inject

@HiltViewModel
class CourseLookupViewModel @Inject constructor() : ViewModel() {

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
}

data class CourseLookupUiState(
    val keyword: String = "",
    val searchType: SearchType = SearchType.COURSE,
    val isSearching: Boolean = false,
    val showWebView: Boolean = false,
    val results: List<CourseInfo> = emptyList(),
    val errorMessage: String? = null,
    val warningMessage: String? = null
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
