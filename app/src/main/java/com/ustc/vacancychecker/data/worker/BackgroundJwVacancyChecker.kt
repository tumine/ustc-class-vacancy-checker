package com.ustc.vacancychecker.data.worker

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import com.ustc.vacancychecker.data.model.SelectResult
import com.ustc.vacancychecker.data.remote.CourseCheckScriptUtils
import com.ustc.vacancychecker.data.remote.LoginScriptUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class BackgroundJwVacancyChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BgJwChecker"
        private const val COURSE_SELECT_URL = "https://jw.ustc.edu.cn/for-std/course-select" 
        private const val TIMEOUT_MS = 120000L // 120秒超时，查多门课需要更长的时间
        private const val MSG_ALREADY_SELECTED = "已选课程"
        private const val MAX_RETRY_COUNT = 3 // 最大重试次数
        private const val MAX_ERROR_COUNT = 3 // 最大网络错误次数
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun performCheck(
        classCodes: List<String>,
        username: String,
        password: String,
        autoSelectMap: Map<String, Boolean> = emptyMap()
    ): Result<Map<String, Pair<Int, SelectResult?>>> {
        if (username.isBlank() || password.isBlank()) {
            return Result.failure(Exception("用户名或密码为空"))
        }
        if (classCodes.isEmpty()) {
            return Result.success(emptyMap())
        }

        return try {
            withTimeout(TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val mainHandler = Handler(Looper.getMainLooper())
                    var webView: WebView? = null
                    
                    mainHandler.post {
                        var isResumed = false
                        val resultMap = mutableMapOf<String, Pair<Int, SelectResult?>>()
                        var currentCourseIndex = 0
                        var isSelectingCourse = false
                        var currentVacancy = 0
                        
                        var hasLoggedIn = false
                        var hasHandledAnnouncement = false
                        var hasEnteredCourseSelect = false
                        var hasClickedAllCoursesTab = false // 是否已点击过"全部课程"选项卡
                        
                        // 错误处理相关
                        var networkErrorCount = 0
                        var searchRetryCount = mutableMapOf<String, Int>() // 每个课程的搜索重试次数
                        var isReloading = false // 是否正在重新加载页面
                        
                        fun resumeEx(result: Result<Map<String, Pair<Int, SelectResult?>>>) {
                            if (!isResumed && continuation.isActive) {
                                isResumed = true
                                try {
                                    webView?.stopLoading()
                                    webView?.destroy()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error destroying WebView", e)
                                }
                                webView = null
                                continuation.resume(result)
                            }
                        }
                        
                        fun reloadPage() {
                            if (isReloading) return
                            isReloading = true
                            networkErrorCount++
                            Log.w(TAG, "Reloading page due to error. Error count: $networkErrorCount")
                            
                            if (networkErrorCount > MAX_ERROR_COUNT) {
                                Log.e(TAG, "Max error count reached, aborting")
                                resumeEx(Result.failure(Exception("网络错误次数过多")))
                                return
                            }
                            
                            // 重置状态
                            hasClickedAllCoursesTab = false
                            hasHandledAnnouncement = false
                            hasEnteredCourseSelect = false
                            
                            mainHandler.postDelayed({
                                isReloading = false
                                webView?.loadUrl(COURSE_SELECT_URL)
                            }, 2000)
                        }

                        fun checkNextCourse() {
                            if (currentCourseIndex < classCodes.size) {
                                val code = classCodes[currentCourseIndex]
                                Log.d(TAG, "Checking course: $code")
                                isSelectingCourse = false
                                
                                // 使用新的搜索策略：第一次点击选项卡，后续快速搜索
                                val js = if (!hasClickedAllCoursesTab) {
                                    Log.d(TAG, "First search, clicking '全部课程' tab first")
                                    // 先点击选项卡，延迟后再搜索
                                    webView?.evaluateJavascript(CourseCheckScriptUtils.getClickAllCoursesTabScript(), null)
                                    hasClickedAllCoursesTab = true
                                    // 延迟后执行快速搜索
                                    mainHandler.postDelayed({
                                        webView?.evaluateJavascript(CourseCheckScriptUtils.getQuickSearchScript(code), null)
                                    }, 1500)
                                    null
                                } else {
                                    Log.d(TAG, "Subsequent search, using quick search")
                                    CourseCheckScriptUtils.getQuickSearchScript(code)
                                }
                                
                                js?.let { webView?.evaluateJavascript(it, null) }
                            } else {
                                Log.d(TAG, "All courses checked. Results: $resultMap")
                                resumeEx(Result.success(resultMap))
                            }
                        }

                        try {
                            Log.d(TAG, "Starting background Jw Vacancy Checker...")
                            
                            try {
                                CookieManager.getInstance().removeAllCookies(null)
                                CookieManager.getInstance().flush()
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to clear cookies", e)
                            }

                            webView = WebView(context).apply {
                                layoutParams = ViewGroup.LayoutParams(1, 1) // 最小尺寸
                                
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    setSupportMultipleWindows(false)
                                    javaScriptCanOpenWindowsAutomatically = true
                                }

                                addJavascriptInterface(object {
                                    @JavascriptInterface
                                    fun logDomInfo(info: String) {
                                        Log.d("$TAG-DOM", info)
                                    }
                                    
                                    @JavascriptInterface
                                    fun captureCredentials(username: String, password: String) {}

                                    @JavascriptInterface
                                    fun onLoginErrorDetected() {
                                        mainHandler.post {
                                            resumeEx(Result.failure(Exception("登录失败(密码错误等)")))
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onAnnouncementDismissed(found: Boolean) {
                                        Log.d(TAG, "Announcement dismissed: found=$found")
                                        hasHandledAnnouncement = true
                                        mainHandler.post { checkNextCourse() }
                                    }
                                    
                                    @JavascriptInterface
                                    fun onEnterCourseSelectResult(found: Boolean) {
                                        Log.d(TAG, "Enter course select result: found=$found")
                                        if (found) {
                                            hasEnteredCourseSelect = true
                                            // 会触发导航，然后在 onPageFinished 中处理公告
                                        } else {
                                            mainHandler.post {
                                                resumeEx(Result.failure(Exception("不在选课时间内或未找到进入选课按钮")))
                                            }
                                        }
                                    }
                                    
                                    @JavascriptInterface
                                    fun onTabClicked(success: Boolean) {
                                        Log.d(TAG, "Tab clicked result: success=$success")
                                        if (!success) {
                                            mainHandler.post {
                                                Log.w(TAG, "Failed to click '全部课程' tab, retrying...")
                                                hasClickedAllCoursesTab = false
                                                checkNextCourse()
                                            }
                                        }
                                    }
                                    
                                    @JavascriptInterface
                                    fun onSearchComplete(code: String) {
                                        Log.d(TAG, "Search complete, reading vacancy...")
                                        mainHandler.post {
                                            if (currentCourseIndex < classCodes.size && classCodes[currentCourseIndex].equals(code, ignoreCase = true)) {
                                                val js = CourseCheckScriptUtils.getReadVacancyScript(code)
                                                webView?.evaluateJavascript(js, null)
                                            }
                                        }
                                    }
                                    
                                    @JavascriptInterface
                                    fun onSearchError(code: String, message: String) {
                                        Log.w(TAG, "Search error for $code: $message")
                                        mainHandler.post {
                                            // 搜索失败，尝试重试
                                            val retryCount = searchRetryCount.getOrDefault(code, 0)
                                            if (retryCount < MAX_RETRY_COUNT) {
                                                searchRetryCount[code] = retryCount + 1
                                                Log.d(TAG, "Retrying search for $code (attempt ${retryCount + 1})")
                                                mainHandler.postDelayed({
                                                    webView?.evaluateJavascript(CourseCheckScriptUtils.getQuickSearchScript(code), null)
                                                }, 1000)
                                            } else {
                                                Log.w(TAG, "Max retry count reached for $code, skipping")
                                                currentCourseIndex++
                                                checkNextCourse()
                                            }
                                        }
                                    }
                                    
                                    @JavascriptInterface
                                    fun onVacancyResult(code: String, stdCount: Int, limitCount: Int, courseName: String, teacher: String, hasSelectButton: Boolean, isAlreadySelected: Boolean) {
                                        Log.d(TAG, "Vacancy result: $stdCount/$limitCount (name=$courseName, teacher=$teacher, hasSelectButton=$hasSelectButton, isAlreadySelected=$isAlreadySelected)")
                                        mainHandler.post {
                                            if (currentCourseIndex < classCodes.size && classCodes[currentCourseIndex].equals(code, ignoreCase = true)) {
                                                currentVacancy = maxOf(0, limitCount - stdCount)
                                                
                                                // 获取该课程的自动选课开关状态
                                                val courseAutoSelectEnabled = autoSelectMap[code] ?: false
                                                
                                                // 如果启用自动选课，有空位，有选课按钮，且未选中，则触发选课
                                                if (courseAutoSelectEnabled && currentVacancy > 0 && hasSelectButton && !isAlreadySelected) {
                                                    Log.d(TAG, "Auto-select enabled for $code, triggering select...")
                                                    isSelectingCourse = true
                                                    mainHandler.postDelayed({
                                                        val clickJs = CourseCheckScriptUtils.getClickSelectButtonScript(code)
                                                        webView?.evaluateJavascript(clickJs, null)
                                                    }, 1000)
                                                } else {
                                                    // 否则直接记录结果并继续下一门课
                                                    resultMap[code] = Pair(currentVacancy, null)
                                                    currentCourseIndex++
                                                    checkNextCourse()
                                                }
                                            }
                                        }
                                    }
                                    
                                    @JavascriptInterface
                                    fun onSelectButtonClickResult(success: Boolean, message: String) {
                                        Log.d(TAG, "Select button click result: success=$success, message=$message")
                                        mainHandler.post {
                                            if (success && isSelectingCourse) {
                                                // 等待选课结果
                                                mainHandler.postDelayed({
                                                    val resultJs = CourseCheckScriptUtils.getCheckSelectResultScript()
                                                    webView?.evaluateJavascript(resultJs, null)
                                                }, 1500)
                                            } else if (!success) {
                                                // 选课按钮点击失败，记录失败并继续下一门课
                                                val code = if (currentCourseIndex < classCodes.size) classCodes[currentCourseIndex] else ""
                                                if (code.isNotEmpty()) {
                                                    val isAlreadySelected = message.contains(MSG_ALREADY_SELECTED)
                                                    resultMap[code] = Pair(currentVacancy, SelectResult(
                                                        success = isAlreadySelected,
                                                        message = message,
                                                        isAlreadySelected = isAlreadySelected
                                                    ))
                                                    currentCourseIndex++
                                                    checkNextCourse()
                                                }
                                            }
                                        }
                                    }
                                    
                                    @JavascriptInterface
                                    fun onSelectResult(success: Boolean, message: String) {
                                        Log.d(TAG, "Select result: success=$success, message=$message")
                                        mainHandler.post {
                                            val code = if (currentCourseIndex < classCodes.size) classCodes[currentCourseIndex] else ""
                                            if (code.isNotEmpty()) {
                                                resultMap[code] = Pair(currentVacancy, SelectResult(success, message))
                                                currentCourseIndex++
                                                checkNextCourse()
                                            }
                                        }
                                    }
                                    
                                    @JavascriptInterface
                                    fun onCourseNotFound(code: String) {
                                        Log.w(TAG, "Course not found")
                                        mainHandler.post {
                                            if (currentCourseIndex < classCodes.size && classCodes[currentCourseIndex].equals(code, ignoreCase = true)) {
                                                // 没找到则直接继续下一门课，不报错
                                                currentCourseIndex++
                                                checkNextCourse()
                                            }
                                        }
                                    }
                                }, "AndroidBridge")

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        Log.d(TAG, "Page finished: $url")
                                        
                                        if (url != null) {
                                            if (url.contains("id.ustc.edu.cn") || url.contains("passport.ustc.edu.cn")) {
                                                view?.evaluateJavascript(LoginScriptUtils.getCredentialCaptureScript(), null)
                                                view?.evaluateJavascript(LoginScriptUtils.getAutoFillScript(username, password), null)
                                            }
                                            else if (url.contains("jw.ustc.edu.cn") && url.contains("login")) {
                                                view?.evaluateJavascript(LoginScriptUtils.getAutoLoginClickScript(), null)
                                            }
                                            else if (url.contains("jw.ustc.edu.cn") && !url.contains("course-select") && !hasLoggedIn) {
                                                hasLoggedIn = true
                                                view?.loadUrl(COURSE_SELECT_URL)
                                            }
                                            else if (url.contains("course-select") && !hasEnteredCourseSelect) {
                                                view?.evaluateJavascript(CourseCheckScriptUtils.getCheckEnterButtonScript(), null)
                                            }
                                            else if (url.contains("course-select") && hasEnteredCourseSelect && !hasHandledAnnouncement) {
                                                view?.evaluateJavascript(CourseCheckScriptUtils.getDismissAnnouncementScript(), null)
                                            }
                                        }
                                    }
                                    
                                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                        super.onReceivedError(view, request, error)
                                        Log.w(TAG, "WebView error: ${error?.description}, url: ${request?.url}")
                                        
                                        // 只处理主框架的错误
                                        if (request?.isForMainFrame == true && !isResumed) {
                                            mainHandler.post {
                                                reloadPage()
                                            }
                                        }
                                    }
                                    
                                    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                                        super.onReceivedHttpError(view, request, errorResponse)
                                        Log.w(TAG, "HTTP error: ${errorResponse?.statusCode}, url: ${request?.url}")
                                        
                                        // 只处理主框架的错误
                                        if (request?.isForMainFrame == true && !isResumed) {
                                            mainHandler.post {
                                                reloadPage()
                                            }
                                        }
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                        result?.confirm()
                                        if (message?.contains("公告") == true || message?.contains("选课") == true) {
                                            if (!hasHandledAnnouncement && hasEnteredCourseSelect) {
                                                hasHandledAnnouncement = true
                                                view?.post {
                                                    // 弹窗消掉后认为可以通过，直接触发搜素
                                                    checkNextCourse()
                                                }
                                            } else if (!hasEnteredCourseSelect) {
                                                view?.post {
                                                    view.evaluateJavascript(CourseCheckScriptUtils.getCheckEnterButtonScript(), null)
                                                }
                                            }
                                        }
                                        return true
                                    }
                                    
                                    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                        result?.confirm()
                                        if (message?.contains("公告") == true || message?.contains("选课") == true) {
                                            if (!hasHandledAnnouncement && hasEnteredCourseSelect) {
                                                hasHandledAnnouncement = true
                                                view?.post { checkNextCourse() }
                                            } else if (!hasEnteredCourseSelect) {
                                                view?.post {
                                                    view.evaluateJavascript(CourseCheckScriptUtils.getCheckEnterButtonScript(), null)
                                                }
                                            }
                                        }
                                        return true
                                    }
                                }
                                
                                loadUrl(COURSE_SELECT_URL)
                            }
                            
                            mainHandler.postDelayed({
                                if (!isResumed) {
                                    Log.w(TAG, "Timeout reaching $TIMEOUT_MS ms")
                                    resumeEx(Result.failure(Exception("后台查询超时")))
                                }
                            }, TIMEOUT_MS)

                        } catch (e: Exception) {
                            Log.e(TAG, "Error initializing background WebView", e)
                            resumeEx(Result.failure(e))
                        }
                    }
                    
                    continuation.invokeOnCancellation {
                        mainHandler.post {
                            try {
                                webView?.stopLoading()
                                webView?.destroy()
                            } catch (e: Exception) {}
                            webView = null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
