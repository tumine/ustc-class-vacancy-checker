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
        uiState = uiState.copy(searchType = type)
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
            errorMessage = null
        )
    }

    fun onSearchResults(json: String) {
        Log.d("CourseLookup", "Search results received: $json")
        try {
            val jsonArray = JSONArray(json)
            val courses = mutableListOf<CourseInfo>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                courses.add(
                    CourseInfo(
                        classCode = obj.optString("classCode", ""),
                        courseName = obj.optString("courseName", ""),
                        teacher = obj.optString("teacher", "")
                    )
                )
            }

            uiState = if (courses.isEmpty()) {
                uiState.copy(
                    isSearching = false,
                    showWebView = false,
                    errorMessage = "未找到匹配「${uiState.keyword}」的课程"
                )
            } else {
                uiState.copy(
                    isSearching = false,
                    showWebView = false,
                    results = courses
                )
            }
        } catch (e: Exception) {
            Log.e("CourseLookup", "Failed to parse search results", e)
            uiState = uiState.copy(
                isSearching = false,
                showWebView = false,
                errorMessage = "解析搜索结果失败: ${e.message}"
            )
        }
    }

    fun onSearchError(message: String) {
        Log.e("CourseLookup", "Search error: $message")
        uiState = uiState.copy(
            isSearching = false,
            showWebView = false,
            errorMessage = message
        )
    }
}

data class CourseLookupUiState(
    val keyword: String = "",
    val searchType: SearchType = SearchType.COURSE,
    val isSearching: Boolean = false,
    val showWebView: Boolean = false,
    val results: List<CourseInfo> = emptyList(),
    val errorMessage: String? = null
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
