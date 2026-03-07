package com.ustc.vacancychecker.ui.coursecheck

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ustc.vacancychecker.data.remote.CourseCheckScriptUtils

/**
 * WebView 选课页面操作
 * 在选课页面中执行：检测进入选课按钮 → 点击全部课程 → 搜索课堂号 → 读取余量
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewCourseCheckScreen(
    classCode: String,
    onNotInSelectTime: () -> Unit,
    onCourseNotFound: () -> Unit,
    onVacancyResult: (stdCount: Int, limitCount: Int) -> Unit
) {
    val courseSelectUrl = "https://jw.ustc.edu.cn/for-std/course-select"
    
    var isLoading by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    
    // 状态跟踪
    var hasEnteredCourseSelect by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // 进度条
        if (isLoading) {
            LinearProgressIndicator(
                progress = { loadingProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        
        // WebView（隐藏式操作，但保留可见以便调试）
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        setSupportMultipleWindows(false)
                        javaScriptCanOpenWindowsAutomatically = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    
                    // 添加 JavaScript 接口
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onEnterCourseSelectResult(found: Boolean) {
                            Log.d("CourseCheck", "Enter course select result: found=$found")
                            if (found) {
                                hasEnteredCourseSelect = true
                                // 进入选课后，注入搜索脚本
                                post {
                                    val js = CourseCheckScriptUtils.getSearchCourseScript(classCode)
                                    evaluateJavascript(js, null)
                                }
                            } else {
                                post { onNotInSelectTime() }
                            }
                        }
                        
                        @JavascriptInterface
                        fun onSearchComplete() {
                            Log.d("CourseCheck", "Search complete, reading vacancy...")
                            hasSearched = true
                            // 搜索完成后，读取余量数据
                            post {
                                val js = CourseCheckScriptUtils.getReadVacancyScript(classCode)
                                evaluateJavascript(js, null)
                            }
                        }
                        
                        @JavascriptInterface
                        fun onVacancyResult(stdCount: Int, limitCount: Int) {
                            Log.d("CourseCheck", "Vacancy result: $stdCount/$limitCount")
                            post { onVacancyResult(stdCount, limitCount) }
                        }
                        
                        @JavascriptInterface
                        fun onCourseNotFound() {
                            Log.d("CourseCheck", "Course not found")
                            post { onCourseNotFound() }
                        }
                    }, "AndroidBridge")
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                            Log.d("CourseCheck", "Page started: $url")
                        }
                        
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            Log.d("CourseCheck", "Page finished: $url")
                            
                            url?.let {
                                // 已经在选课页面，检测"进入选课"按钮
                                if (it.contains("course-select") && !hasEnteredCourseSelect) {
                                    val js = CourseCheckScriptUtils.getCheckEnterButtonScript()
                                    view?.evaluateJavascript(js, null)
                                }
                            }
                        }
                        
                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            Log.e("CourseCheck", "Error: ${error?.description}, URL: ${request?.url}")
                        }
                    }
                    
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            loadingProgress = newProgress
                        }
                    }
                    
                    loadUrl(courseSelectUrl)
                }
            }
        )
    }
}
