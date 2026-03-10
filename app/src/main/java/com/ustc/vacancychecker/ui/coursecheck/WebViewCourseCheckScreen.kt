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
import com.ustc.vacancychecker.data.remote.LoginScriptUtils

/**
 * WebView 选课页面操作
 * 在选课页面中执行：自动登录 → 消除公告弹窗 → 点击进入选课 → 搜索课堂号 → 读取余量
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewCourseCheckScreen(
    classCode: String,
    credentials: Pair<String, String>? = null,
    autoSelectEnabled: Boolean = false,
    onNotInSelectTime: () -> Unit,
    onCourseNotFound: () -> Unit,
    onVacancyResult: (stdCount: Int, limitCount: Int, courseName: String, teacher: String, hasSelectButton: Boolean, isAlreadySelected: Boolean) -> Unit,
    onSelectButtonClickResult: (success: Boolean, message: String) -> Unit = { _, _ -> },
    onSelectResult: (success: Boolean, message: String) -> Unit = { _, _ -> }
) {
    val courseSelectUrl = "https://jw.ustc.edu.cn/for-std/course-select"
    
    var isLoading by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    
    // 状态跟踪
    var hasLoggedIn by remember { mutableStateOf(false) }
    var hasHandledAnnouncement by remember { mutableStateOf(false) }
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
                        fun captureCredentials(username: String, password: String) {
                            Log.d("CourseCheck", "Credentials captured for user: $username")
                        }

                        @JavascriptInterface
                        fun onLoginErrorDetected() {
                            Log.e("CourseCheck", "Login error detected during course check")
                        }

                        @JavascriptInterface
                        fun logDomInfo(info: String) {
                            Log.d("CourseCheck-DOM", info)
                        }
                        
                        @JavascriptInterface
                        fun onAnnouncementDismissed(found: Boolean) {
                            Log.d("CourseCheck", "Announcement dismissed: found=$found")
                            hasHandledAnnouncement = true
                            // 弹窗已处理（或不存在），继续注入搜索脚本
                            post {
                                val js = CourseCheckScriptUtils.getSearchCourseScript(classCode)
                                evaluateJavascript(js, null)
                            }
                        }
                        
                        @JavascriptInterface
                        fun onEnterCourseSelectResult(found: Boolean) {
                            Log.d("CourseCheck", "Enter course select result: found=$found")
                            if (found) {
                                hasEnteredCourseSelect = true
                                // 点击"进入选课"后会导航到新页面，等待 onPageFinished 触发后续流程
                                Log.d("CourseCheck", "Entered course select, waiting for new page to load...")
                            } else {
                                post { onNotInSelectTime() }
                            }
                        }
                        
                        @JavascriptInterface
                        fun onSearchComplete(code: String) {
                            Log.d("CourseCheck", "Search complete, reading vacancy...")
                            hasSearched = true
                            // 搜索完成后，读取余量数据
                            post {
                                val js = CourseCheckScriptUtils.getReadVacancyScript(classCode)
                                evaluateJavascript(js, null)
                            }
                        }
                        
                        @JavascriptInterface
                        fun onVacancyResult(code: String, stdCount: Int, limitCount: Int, courseName: String, teacher: String, hasSelectButton: Boolean, isAlreadySelected: Boolean) {
                            Log.d("CourseCheck", "Vacancy result: $stdCount/$limitCount, name: $courseName, teacher: $teacher, hasSelectButton: $hasSelectButton, isAlreadySelected: $isAlreadySelected")
                            post { 
                                onVacancyResult(stdCount, limitCount, courseName, teacher, hasSelectButton, isAlreadySelected)
                                // 如果启用自动选课且有空位且有选课按钮，触发选课
                                if (autoSelectEnabled && stdCount < limitCount && hasSelectButton && !isAlreadySelected) {
                                    Log.d("CourseCheck", "Auto-select enabled, triggering select course...")
                                    postDelayed({
                                        val clickJs = CourseCheckScriptUtils.getClickSelectButtonScript(classCode)
                                        evaluateJavascript(clickJs, null)
                                    }, 1000)
                                }
                            }
                        }
                        
                        @JavascriptInterface
                        fun onCourseNotFound(code: String) {
                            Log.d("CourseCheck", "Course not found")
                            post { onCourseNotFound() }
                        }
                        
                        @JavascriptInterface
                        fun onSelectButtonClickResult(success: Boolean, message: String) {
                            Log.d("CourseCheck", "Select button click result: success=$success, message=$message")
                            post { 
                                onSelectButtonClickResult(success, message)
                                // 如果成功点击了选课按钮，等待结果
                                if (success) {
                                    postDelayed({
                                        val resultJs = CourseCheckScriptUtils.getCheckSelectResultScript()
                                        evaluateJavascript(resultJs, null)
                                    }, 1500)
                                }
                            }
                        }
                        
                        @JavascriptInterface
                        fun onSelectResult(success: Boolean, message: String) {
                            Log.d("CourseCheck", "Select result: success=$success, message=$message")
                            post { onSelectResult(success, message) }
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
                                // Case 1: CAS 统一身份认证登录页面 - 注入凭证捕获和自动填充
                                if (it.contains("id.ustc.edu.cn") || it.contains("passport.ustc.edu.cn")) {
                                    Log.d("CourseCheck", "Detected CAS login page, injecting login scripts")
                                    val captureJs = LoginScriptUtils.getCredentialCaptureScript()
                                    view?.evaluateJavascript(captureJs, null)
                                    // 自动填充凭证
                                    credentials?.let { (u, p) ->
                                        val fillJs = LoginScriptUtils.getAutoFillScript(u, p)
                                        view?.evaluateJavascript(fillJs, null)
                                    }
                                }
                                // Case 2: 教务系统登录页面 - 自动点击"统一身份认证登录"
                                else if (it.contains("jw.ustc.edu.cn") && it.contains("login")) {
                                    Log.d("CourseCheck", "Detected jw login page, auto-clicking CAS login button")
                                    val loginJs = LoginScriptUtils.getAutoLoginClickScript()
                                    view?.evaluateJavascript(loginJs, null)
                                }
                                // Case 3: 登录成功后落在 jw 主页（非选课页面）- 跳转到选课页
                                else if (it.contains("jw.ustc.edu.cn") && !it.contains("course-select") && !hasLoggedIn) {
                                    Log.d("CourseCheck", "Login successful, redirecting to course-select page")
                                    hasLoggedIn = true
                                    view?.loadUrl(courseSelectUrl)
                                }
                                // Case 4: 选课 turns 页面（包含"进入选课"按钮） - 直接点击进入
                                else if (it.contains("course-select") && !hasEnteredCourseSelect) {
                                    Log.d("CourseCheck", "On course-select turns page, clicking enter button")
                                    val js = CourseCheckScriptUtils.getCheckEnterButtonScript()
                                    view?.evaluateJavascript(js, null)
                                }
                                // Case 5: 已点击"进入选课"后的实际选课页面 - 先消除公告弹窗，再搜索
                                else if (it.contains("course-select") && hasEnteredCourseSelect && !hasHandledAnnouncement) {
                                    Log.d("CourseCheck", "On actual selection page, dismissing announcement popup")
                                    val js = CourseCheckScriptUtils.getDismissAnnouncementScript()
                                    view?.evaluateJavascript(js, null)
                                }
                                Unit
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
                        
                        // 处理 JavaScript alert() 弹窗（选课公告可能是 alert 形式）
                        override fun onJsAlert(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            result: JsResult?
                        ): Boolean {
                            Log.d("CourseCheck", "JS Alert: $message")
                            // 自动确认所有 alert 弹窗
                            result?.confirm()
                            if (message?.contains("选课公告") == true || message?.contains("公告") == true) {
                                Log.d("CourseCheck", "Dismissed announcement alert")
                                hasHandledAnnouncement = true
                                view?.post {
                                    val js = CourseCheckScriptUtils.getCheckEnterButtonScript()
                                    view.evaluateJavascript(js, null)
                                }
                            }
                            return true
                        }
                        
                        // 处理 JavaScript confirm() 弹窗
                        override fun onJsConfirm(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            result: JsResult?
                        ): Boolean {
                            Log.d("CourseCheck", "JS Confirm: $message")
                            // 自动确认所有 confirm 弹窗
                            result?.confirm()
                            if (message?.contains("选课公告") == true || message?.contains("公告") == true) {
                                Log.d("CourseCheck", "Dismissed announcement confirm")
                                hasHandledAnnouncement = true
                                view?.post {
                                    val js = CourseCheckScriptUtils.getCheckEnterButtonScript()
                                    view.evaluateJavascript(js, null)
                                }
                            }
                            return true
                        }
                    }
                    
                    loadUrl(courseSelectUrl)
                }
            },
            update = { }
        )
    }
}
