package com.ustc.vacancychecker.data.worker

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import com.google.gson.Gson
import com.ustc.vacancychecker.data.local.CourseRepository
import com.ustc.vacancychecker.data.model.AdjustmentCourseEvaluator
import com.ustc.vacancychecker.data.model.AdjustmentCourseTablePayload
import com.ustc.vacancychecker.data.model.SelectResult
import com.ustc.vacancychecker.data.model.CourseCheckRequest
import com.ustc.vacancychecker.data.model.CourseCheckResult
import com.ustc.vacancychecker.data.model.CourseTrackingPlanner
import com.ustc.vacancychecker.data.model.CourseSwitchResult
import com.ustc.vacancychecker.data.model.CourseSwitchState
import com.ustc.vacancychecker.data.model.SelectedCourseBehavior
import com.ustc.vacancychecker.data.remote.CourseCheckScriptUtils
import com.ustc.vacancychecker.data.remote.LoginScriptUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class BackgroundJwVacancyChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val courseRepository: CourseRepository
) {
    companion object {
        private const val TAG = "BgJwChecker"
        private const val COURSE_SELECT_URL = "https://jw.ustc.edu.cn/for-std/course-select" 
        private const val COURSE_ADJUSTMENT_URL_PREFIX = "https://jw.ustc.edu.cn/for-std/course-adjustment-apply"
        private const val TIMEOUT_MS = 120000L // 120秒超时，查多门课需要更长的时间
        private const val MSG_ALREADY_SELECTED = "已选课程"
        private const val MAX_RETRY_COUNT = 3 // 最大重试次数
        private const val MAX_ERROR_COUNT = 3 // 最大网络错误次数
    }

    private data class CourseObservation(
        val request: CourseCheckRequest,
        val vacancy: Int,
        val isAlreadySelected: Boolean,
        val hasDropButton: Boolean,
        val hasSwitchButton: Boolean
    )

    private enum class SwitchPath { DROP_THEN_SELECT, ADJUSTMENT_APPLY }

    private enum class SwitchStage {
        CLICKING_SOURCE_ACTION,
        CONFIRMING_DROP,
        SEARCHING_TARGET_AFTER_DROP,
        SELECTING_TARGET_AFTER_DROP,
        WAITING_TARGET_SELECT_RESULT,
        WAITING_ADJUSTMENT_PAGE,
        READING_ADJUSTMENT_TABLE,
        CLICKING_ADJUSTMENT_TARGET,
        WAITING_APPLICATION_FORM,
        FILLING_APPLICATION_FORM,
        WAITING_SUBMIT_OUTCOME
    }

    private data class ActiveSwitchOperation(
        val sourceRequest: CourseCheckRequest,
        var targetObservation: CourseObservation,
        val path: SwitchPath,
        var stage: SwitchStage,
        val adjustmentCandidates: List<CourseObservation> = listOf(targetObservation),
        val actionLog: MutableList<String> = mutableListOf()
    )

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun performCheck(
        requests: List<CourseCheckRequest>,
        username: String,
        password: String
    ): Result<Map<String, CourseCheckResult>> {
        if (username.isBlank() || password.isBlank()) {
            return Result.failure(Exception("用户名或密码为空"))
        }
        if (requests.isEmpty()) {
            return Result.success(emptyMap())
        }

        val classCodes = requests.map { it.courseId }

        return try {
            val verificationCodeMethod = courseRepository.verificationCodeMethodFlow.first()
            withTimeout(TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val mainHandler = Handler(Looper.getMainLooper())
                    var webView: WebView? = null
                    
                    mainHandler.post {
                        var isResumed = false
                        val gson = Gson()
                        val resultMap = mutableMapOf<String, CourseCheckResult>()
                        val skippedCourseIds = mutableSetOf<String>()
                        val actionButtons = mutableMapOf<String, Pair<Boolean, Boolean>>()
                        val observations = mutableMapOf<String, CourseObservation>()
                        var activeSwitch: ActiveSwitchOperation? = null
                        var adjustmentFormScriptInjected = false
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
                        
                        fun resumeEx(result: Result<Map<String, CourseCheckResult>>) {
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

                        fun skipRemainingAfterSelection(selectedRequest: CourseCheckRequest) {
                            requests.drop(currentCourseIndex + 1)
                                .filter { CourseTrackingPlanner.shouldSkipAfterSelection(selectedRequest, it) }
                                .forEach { skippedCourseIds.add(it.courseId) }
                        }

                        fun checkNextCourse() {
                            while (currentCourseIndex < classCodes.size && classCodes[currentCourseIndex] in skippedCourseIds) {
                                Log.d(TAG, "Skipping ${classCodes[currentCourseIndex]} after same-group selection")
                                currentCourseIndex++
                            }
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

                        fun returnToCourseSelectAfterAdjustment() {
                            hasClickedAllCoursesTab = false
                            hasHandledAnnouncement = false
                            hasEnteredCourseSelect = false
                            webView?.loadUrl(COURSE_SELECT_URL)
                        }

                        fun completeSwitch(state: CourseSwitchState, message: String) {
                            val operation = activeSwitch ?: return
                            operation.actionLog.add(message)
                            val sourceCode = operation.sourceRequest.courseId
                            val targetCode = operation.targetObservation.request.courseId
                            val sourceWasDropped = operation.path == SwitchPath.DROP_THEN_SELECT &&
                                operation.stage in setOf(
                                    SwitchStage.SEARCHING_TARGET_AFTER_DROP,
                                    SwitchStage.SELECTING_TARGET_AFTER_DROP,
                                    SwitchStage.WAITING_TARGET_SELECT_RESULT
                                )
                            val sourceResult = resultMap[sourceCode] ?: CourseCheckResult(
                                vacancy = 0,
                                isAlreadySelected = true
                            )
                            resultMap[sourceCode] = sourceResult.copy(
                                isAlreadySelected = if (sourceWasDropped) false else sourceResult.isAlreadySelected,
                                switchResult = CourseSwitchResult(
                                    state = state,
                                    sourceCourseId = sourceCode,
                                    targetCourseId = targetCode,
                                    message = message,
                                    actionLog = operation.actionLog.toList()
                                )
                            )
                            if (state == CourseSwitchState.VERIFIED && operation.path == SwitchPath.DROP_THEN_SELECT) {
                                val targetResult = resultMap[targetCode] ?: CourseCheckResult(
                                    vacancy = operation.targetObservation.vacancy,
                                    isAlreadySelected = true
                                )
                                resultMap[targetCode] = targetResult.copy(isAlreadySelected = true)
                            }
                            activeSwitch = null
                            adjustmentFormScriptInjected = false
                            if (state == CourseSwitchState.FAILED) {
                                resumeEx(Result.success(resultMap))
                                return
                            }
                            skipRemainingAfterSelection(operation.sourceRequest)
                            currentCourseIndex++
                            if (operation.path == SwitchPath.ADJUSTMENT_APPLY) {
                                returnToCourseSelectAfterAdjustment()
                            } else {
                                checkNextCourse()
                            }
                        }

                        fun failSwitch(message: String) {
                            val operation = activeSwitch
                            val sourceDropped = operation?.path == SwitchPath.DROP_THEN_SELECT &&
                                operation.stage in setOf(
                                    SwitchStage.SEARCHING_TARGET_AFTER_DROP,
                                    SwitchStage.SELECTING_TARGET_AFTER_DROP,
                                    SwitchStage.WAITING_TARGET_SELECT_RESULT
                                )
                            val suffix = if (sourceDropped) "；当前课堂已完成退课，请立即人工检查" else ""
                            completeSwitch(CourseSwitchState.FAILED, "换班失败：$message$suffix")
                        }

                        fun finishAdjustmentWithoutSwitch(message: String) {
                            val operation = activeSwitch ?: return
                            operation.actionLog.add(message)
                            Log.i(TAG, message)
                            activeSwitch = null
                            adjustmentFormScriptInjected = false
                            skipRemainingAfterSelection(operation.sourceRequest)
                            currentCourseIndex++
                            returnToCourseSelectAfterAdjustment()
                        }

                        fun failSwitchOrReload() {
                            if (activeSwitch != null) {
                                failSwitch("换班过程中页面加载失败")
                            } else {
                                reloadPage()
                            }
                        }

                        fun injectAdjustmentFormScript() {
                            val operation = activeSwitch ?: return
                            if (operation.path != SwitchPath.ADJUSTMENT_APPLY || adjustmentFormScriptInjected) return
                            adjustmentFormScriptInjected = true
                            operation.stage = SwitchStage.FILLING_APPLICATION_FORM
                            operation.actionLog.add("已进入换班申请表单，准备填写申请原因")
                            webView?.evaluateJavascript(
                                CourseCheckScriptUtils.getFillAndSubmitAdjustmentScript("同课程换班"),
                                null
                            )
                        }

                        fun openAdjustmentCourseTable() {
                            val operation = activeSwitch ?: return
                            val url = webView?.url.orEmpty()
                            if (!url.startsWith(COURSE_ADJUSTMENT_URL_PREFIX)) {
                                failSwitch("单课换班未跳转至规定页面，当前地址：$url")
                                return
                            }
                            operation.stage = SwitchStage.READING_ADJUSTMENT_TABLE
                            operation.actionLog.add("已确认进入课程调整申请页面：$url")
                            webView?.evaluateJavascript(
                                CourseCheckScriptUtils.getReadAdjustmentCourseTableScript(),
                                null
                            )
                        }

                        fun maybeStartSwitch(selectedObservation: CourseObservation): Boolean {
                            val selectedRequest = selectedObservation.request
                            if (selectedRequest.selectedCourseBehavior != SelectedCourseBehavior.PRIORITY_UPGRADE) return false
                            if (selectedRequest.pendingSwitchTargetId != null) return false
                            val candidates = requests
                                .asSequence()
                                .filter {
                                    it.groupId == selectedRequest.groupId &&
                                        it.priority < selectedRequest.priority
                                }
                                .sortedBy { it.priority }
                                .map { request ->
                                    observations[request.courseId] ?: CourseObservation(
                                        request = request,
                                        vacancy = 0,
                                        isAlreadySelected = false,
                                        hasDropButton = false,
                                        hasSwitchButton = false
                                    )
                                }
                                .filterNot { it.isAlreadySelected }
                                .toList()
                            if (candidates.isEmpty()) return false

                            val path = when {
                                selectedObservation.hasSwitchButton && selectedObservation.hasDropButton -> SwitchPath.ADJUSTMENT_APPLY
                                selectedObservation.hasDropButton && !selectedObservation.hasSwitchButton -> SwitchPath.DROP_THEN_SELECT
                                else -> {
                                    val target = candidates.firstOrNull { it.vacancy > 0 } ?: return false
                                    activeSwitch = ActiveSwitchOperation(
                                        sourceRequest = selectedRequest,
                                        targetObservation = target,
                                        path = SwitchPath.DROP_THEN_SELECT,
                                        stage = SwitchStage.CLICKING_SOURCE_ACTION,
                                        actionLog = mutableListOf("发现更高优先级目标课堂 ${target.request.courseId}，但当前课堂按钮组合不受支持")
                                    )
                                    failSwitch("当前已选课堂未提供可安全识别的退课/换班按钮组合")
                                    return true
                                }
                            }
                            val target = when (path) {
                                SwitchPath.ADJUSTMENT_APPLY -> candidates.first()
                                SwitchPath.DROP_THEN_SELECT -> candidates.firstOrNull { it.vacancy > 0 } ?: return false
                            }
                            activeSwitch = ActiveSwitchOperation(
                                sourceRequest = selectedRequest,
                                targetObservation = target,
                                path = path,
                                stage = SwitchStage.CLICKING_SOURCE_ACTION,
                                adjustmentCandidates = if (path == SwitchPath.ADJUSTMENT_APPLY) candidates else listOf(target),
                                actionLog = mutableListOf(
                                    "确认当前已选课堂：${selectedRequest.courseId}",
                                    if (path == SwitchPath.ADJUSTMENT_APPLY) {
                                        "准备在单课换班页面统一评估 ${candidates.size} 个更高优先级意向课堂"
                                    } else {
                                        "发现更高优先级目标课堂：${target.request.courseId}，余量：${target.vacancy}"
                                    },
                                    "按钮组合：退课=${selectedObservation.hasDropButton}，换班=${selectedObservation.hasSwitchButton}"
                                )
                            )
                            when (path) {
                                SwitchPath.DROP_THEN_SELECT -> {
                                    activeSwitch?.actionLog?.add("仅存在退课按钮，准备执行退课后选课")
                                    webView?.evaluateJavascript(
                                        CourseCheckScriptUtils.getClickDropButtonScript(selectedRequest.courseId),
                                        null
                                    )
                                }
                                SwitchPath.ADJUSTMENT_APPLY -> {
                                    activeSwitch?.actionLog?.add("同时存在退课和换班按钮；禁止退课，准备点击换班")
                                    webView?.evaluateJavascript(
                                        CourseCheckScriptUtils.getClickSingleCourseSwitchScript(selectedRequest.courseId),
                                        null
                                    )
                                }
                            }
                            return true
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
                                        mainHandler.post searchComplete@{
                                            val operation = activeSwitch
                                            if (operation != null &&
                                                operation.stage == SwitchStage.SEARCHING_TARGET_AFTER_DROP &&
                                                operation.targetObservation.request.courseId.equals(code, ignoreCase = true)
                                            ) {
                                                operation.stage = SwitchStage.SELECTING_TARGET_AFTER_DROP
                                                operation.actionLog.add("退课完成后已重新定位目标课堂：$code")
                                                mainHandler.postDelayed({
                                                    webView?.evaluateJavascript(
                                                        CourseCheckScriptUtils.getClickSelectButtonScript(code),
                                                        null
                                                    )
                                                }, 700)
                                                return@searchComplete
                                            }
                                            if (currentCourseIndex < classCodes.size && classCodes[currentCourseIndex].equals(code, ignoreCase = true)) {
                                                val js = CourseCheckScriptUtils.getReadVacancyScript(code)
                                                webView?.evaluateJavascript(js, null)
                                            }
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onCourseActionButtons(code: String, hasDropButton: Boolean, hasSwitchButton: Boolean) {
                                        Log.d(TAG, "Action buttons for $code: drop=$hasDropButton, switch=$hasSwitchButton")
                                        actionButtons[code] = hasDropButton to hasSwitchButton
                                    }
                                    
                                    @JavascriptInterface
                                    fun onSearchError(code: String, message: String) {
                                        Log.w(TAG, "Search error for $code: $message")
                                        mainHandler.post searchError@{
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
                                                val operation = activeSwitch
                                                if (operation != null &&
                                                    operation.stage == SwitchStage.SEARCHING_TARGET_AFTER_DROP &&
                                                    operation.targetObservation.request.courseId.equals(code, ignoreCase = true)
                                                ) {
                                                    failSwitch("退课后无法重新定位目标课堂：$message")
                                                    return@searchError
                                                }
                                                currentCourseIndex++
                                                checkNextCourse()
                                            }
                                        }
                                    }
                                    
                                    @JavascriptInterface
                                    fun onVacancyResult(code: String, stdCount: Int, limitCount: Int, courseName: String, teacher: String, hasSelectButton: Boolean, isAlreadySelected: Boolean) {
                                        Log.d(TAG, "Vacancy result: $stdCount/$limitCount (name=$courseName, teacher=$teacher, hasSelectButton=$hasSelectButton, isAlreadySelected=$isAlreadySelected)")
                                        mainHandler.post vacancyResult@{
                                            if (currentCourseIndex < classCodes.size && classCodes[currentCourseIndex].equals(code, ignoreCase = true)) {
                                                currentVacancy = maxOf(0, limitCount - stdCount)
                                                
                                                // 获取该课程的自动选课开关状态
                                                val currentRequest = requests[currentCourseIndex]
                                                val courseAutoSelectEnabled = currentRequest.autoSelectEnabled
                                                val buttons = actionButtons[code] ?: (false to false)
                                                val observation = CourseObservation(
                                                    request = currentRequest,
                                                    vacancy = currentVacancy,
                                                    isAlreadySelected = isAlreadySelected,
                                                    hasDropButton = buttons.first,
                                                    hasSwitchButton = buttons.second
                                                )
                                                observations[code] = observation

                                                if (isAlreadySelected && currentRequest.pendingSwitchTargetId == code) {
                                                    resultMap[code] = CourseCheckResult(
                                                        vacancy = currentVacancy,
                                                        isAlreadySelected = true,
                                                        switchResult = CourseSwitchResult(
                                                            state = CourseSwitchState.VERIFIED,
                                                            sourceCourseId = currentRequest.pendingSwitchSourceId.orEmpty(),
                                                            targetCourseId = code,
                                                            message = "换班成功：目标课堂 $code 已显示为已选中",
                                                            actionLog = listOf("下一轮核验目标课堂 $code：已选中")
                                                        )
                                                    )
                                                    skipRemainingAfterSelection(currentRequest)
                                                    currentCourseIndex++
                                                    checkNextCourse()
                                                    return@vacancyResult
                                                }

                                                if (isAlreadySelected && maybeStartSwitch(observation)) {
                                                    resultMap[code] = CourseCheckResult(
                                                        vacancy = currentVacancy,
                                                        isAlreadySelected = true
                                                    )
                                                    return@vacancyResult
                                                }
                                                
                                                // 如果启用自动选课，有空位，有选课按钮，且未选中，则触发选课
                                                if (
                                                    courseAutoSelectEnabled &&
                                                    currentRequest.selectedCourseBehavior != SelectedCourseBehavior.PRIORITY_UPGRADE &&
                                                    currentVacancy > 0 &&
                                                    hasSelectButton &&
                                                    !isAlreadySelected
                                                ) {
                                                    Log.d(TAG, "Auto-select enabled for $code, triggering select...")
                                                    isSelectingCourse = true
                                                    mainHandler.postDelayed({
                                                        val clickJs = CourseCheckScriptUtils.getClickSelectButtonScript(code)
                                                        webView?.evaluateJavascript(clickJs, null)
                                                    }, 1000)
                                                } else {
                                                    // 否则直接记录结果并继续下一门课
                                                    resultMap[code] = CourseCheckResult(
                                                        vacancy = currentVacancy,
                                                        isAlreadySelected = isAlreadySelected
                                                    )
                                                    if (isAlreadySelected) {
                                                        skipRemainingAfterSelection(currentRequest)
                                                    }
                                                    currentCourseIndex++
                                                    checkNextCourse()
                                                }
                                            }
                                        }
                                    }
                                    
                                    @JavascriptInterface
                                    fun onSelectButtonClickResult(success: Boolean, message: String) {
                                        Log.d(TAG, "Select button click result: success=$success, message=$message")
                                        mainHandler.post selectButtonResult@{
                                            val operation = activeSwitch
                                            if (operation != null &&
                                                operation.path == SwitchPath.DROP_THEN_SELECT &&
                                                operation.stage == SwitchStage.SELECTING_TARGET_AFTER_DROP
                                            ) {
                                                if (!success) {
                                                    if (message.contains(MSG_ALREADY_SELECTED)) {
                                                        completeSwitch(
                                                            CourseSwitchState.VERIFIED,
                                                            "换班成功：目标课堂 ${operation.targetObservation.request.courseId} 已显示为已选中"
                                                        )
                                                    } else {
                                                        failSwitch("退课后选中目标课堂失败：$message")
                                                    }
                                                } else {
                                                    operation.stage = SwitchStage.WAITING_TARGET_SELECT_RESULT
                                                    operation.actionLog.add("已点击目标课堂 ${operation.targetObservation.request.courseId} 的选课按钮")
                                                    mainHandler.postDelayed({
                                                        webView?.evaluateJavascript(
                                                            CourseCheckScriptUtils.getCheckSelectResultScript(),
                                                            null
                                                        )
                                                    }, 1500)
                                                }
                                                return@selectButtonResult
                                            }
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
                                                    val selectResult = SelectResult(
                                                        success = isAlreadySelected,
                                                        message = message,
                                                        isAlreadySelected = isAlreadySelected
                                                    )
                                                    resultMap[code] = CourseCheckResult(
                                                        vacancy = currentVacancy,
                                                        isAlreadySelected = isAlreadySelected,
                                                        selectResult = selectResult
                                                    )
                                                    if (isAlreadySelected) {
                                                        skipRemainingAfterSelection(requests[currentCourseIndex])
                                                    }
                                                    currentCourseIndex++
                                                    checkNextCourse()
                                                }
                                            }
                                        }
                                    }
                                    
                                    @JavascriptInterface
                                    fun onSelectResult(success: Boolean, message: String) {
                                        Log.d(TAG, "Select result: success=$success, message=$message")
                                        mainHandler.post selectResult@{
                                            val operation = activeSwitch
                                            if (operation != null &&
                                                operation.path == SwitchPath.DROP_THEN_SELECT &&
                                                operation.stage == SwitchStage.WAITING_TARGET_SELECT_RESULT
                                            ) {
                                                if (success) {
                                                    completeSwitch(
                                                        CourseSwitchState.VERIFIED,
                                                        "换班成功：已退选 ${operation.sourceRequest.courseId} 并选中 ${operation.targetObservation.request.courseId}"
                                                    )
                                                } else {
                                                    failSwitch("退课后选中目标课堂失败：$message")
                                                }
                                                return@selectResult
                                            }
                                            val code = if (currentCourseIndex < classCodes.size) classCodes[currentCourseIndex] else ""
                                            if (code.isNotEmpty()) {
                                                resultMap[code] = CourseCheckResult(
                                                    vacancy = currentVacancy,
                                                    isAlreadySelected = false,
                                                    selectResult = SelectResult(success, message)
                                                )
                                                if (success) {
                                                    skipRemainingAfterSelection(requests[currentCourseIndex])
                                                }
                                                currentCourseIndex++
                                                checkNextCourse()
                                            }
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onDropButtonClickResult(success: Boolean, message: String) {
                                        Log.d(TAG, "Drop button click result: success=$success, message=$message")
                                        mainHandler.post dropButtonResult@{
                                            val operation = activeSwitch
                                            if (operation == null ||
                                                operation.path != SwitchPath.DROP_THEN_SELECT ||
                                                operation.stage != SwitchStage.CLICKING_SOURCE_ACTION
                                            ) return@dropButtonResult
                                            if (!success) {
                                                failSwitch("点击退课按钮失败：$message")
                                                return@dropButtonResult
                                            }
                                            operation.stage = SwitchStage.CONFIRMING_DROP
                                            operation.actionLog.add(message)
                                            mainHandler.postDelayed({
                                                webView?.evaluateJavascript(
                                                    CourseCheckScriptUtils.getConfirmDropResultScript(operation.sourceRequest.courseId),
                                                    null
                                                )
                                            }, 500)
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onDropConfirmResult(success: Boolean, message: String) {
                                        Log.d(TAG, "Drop confirm result: success=$success, message=$message")
                                        mainHandler.post dropConfirmResult@{
                                            val operation = activeSwitch
                                            if (operation == null ||
                                                operation.path != SwitchPath.DROP_THEN_SELECT ||
                                                operation.stage != SwitchStage.CONFIRMING_DROP
                                            ) return@dropConfirmResult
                                            if (!success) {
                                                failSwitch("确认退课失败：$message")
                                                return@dropConfirmResult
                                            }
                                            operation.stage = SwitchStage.SEARCHING_TARGET_AFTER_DROP
                                            operation.actionLog.add(message)
                                            webView?.evaluateJavascript(
                                                CourseCheckScriptUtils.getQuickSearchScript(operation.targetObservation.request.courseId),
                                                null
                                            )
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onSingleCourseSwitchResult(success: Boolean, message: String) {
                                        Log.d(TAG, "Single course switch result: success=$success, message=$message")
                                        mainHandler.post singleCourseSwitchResult@{
                                            val operation = activeSwitch
                                            if (operation == null ||
                                                operation.path != SwitchPath.ADJUSTMENT_APPLY ||
                                                operation.stage != SwitchStage.CLICKING_SOURCE_ACTION
                                            ) return@singleCourseSwitchResult
                                            if (!success) {
                                                failSwitch("进入单课换班失败：$message")
                                                return@singleCourseSwitchResult
                                            }
                                            operation.stage = SwitchStage.WAITING_ADJUSTMENT_PAGE
                                            operation.actionLog.add(message)
                                            mainHandler.postDelayed({
                                                if (activeSwitch === operation &&
                                                    operation.stage == SwitchStage.WAITING_ADJUSTMENT_PAGE
                                                ) {
                                                    openAdjustmentCourseTable()
                                                }
                                            }, 8000)
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onAdjustmentCourseTableResult(payloadJson: String) {
                                        Log.d(TAG, "Received adjustment table payload (${payloadJson.length} chars)")
                                        mainHandler.post adjustmentTableResult@{
                                            val operation = activeSwitch
                                            if (operation == null ||
                                                operation.path != SwitchPath.ADJUSTMENT_APPLY ||
                                                operation.stage != SwitchStage.READING_ADJUSTMENT_TABLE
                                            ) return@adjustmentTableResult

                                            val payload = try {
                                                gson.fromJson(payloadJson, AdjustmentCourseTablePayload::class.java)
                                            } catch (e: Exception) {
                                                failSwitch("换班课程表返回了无效 JSON：${e.message.orEmpty()}")
                                                return@adjustmentTableResult
                                            }
                                            if (payload == null) {
                                                failSwitch("换班课程表返回内容为空")
                                                return@adjustmentTableResult
                                            }
                                            if (!payload.error.isNullOrBlank()) {
                                                failSwitch("未能完整抓取换班课程表：${payload.error}")
                                                return@adjustmentTableResult
                                            }
                                            if (payload.courses.isEmpty()) {
                                                failSwitch("换班课程表没有返回任何课程")
                                                return@adjustmentTableResult
                                            }

                                            val candidateIds = operation.adjustmentCandidates.map { it.request.courseId }
                                            val evaluation = AdjustmentCourseEvaluator.evaluate(candidateIds, payload.courses)
                                            evaluation.effectiveVacancies.forEach { (courseId, vacancy) ->
                                                val observation = operation.adjustmentCandidates.firstOrNull {
                                                    it.request.courseId.equals(courseId, ignoreCase = true)
                                                }
                                                val previous = resultMap[courseId]
                                                resultMap[courseId] = previous?.copy(vacancy = vacancy)
                                                    ?: CourseCheckResult(
                                                        vacancy = vacancy,
                                                        isAlreadySelected = observation?.isAlreadySelected == true
                                                    )
                                            }
                                            operation.adjustmentCandidates.forEach { candidate ->
                                                payload.courses.firstOrNull {
                                                    it.classCode.equals(candidate.request.courseId, ignoreCase = true)
                                                }?.let { snapshot ->
                                                    operation.actionLog.add(
                                                        "换班页课堂 ${candidate.request.courseId}：${snapshot.rawSeatText}，" +
                                                            "有效余量=${snapshot.effectiveVacancy}"
                                                    )
                                                }
                                            }

                                            if (!evaluation.error.isNullOrBlank()) {
                                                failSwitch("本地评估换班课程失败：${evaluation.error}")
                                                return@adjustmentTableResult
                                            }
                                            val targetSnapshot = evaluation.target
                                            if (targetSnapshot == null) {
                                                finishAdjustmentWithoutSwitch(
                                                    "本轮已统一评估 ${candidateIds.size} 个意向课堂，均不满足“选中+待审核<课堂容量”"
                                                )
                                                return@adjustmentTableResult
                                            }

                                            val targetObservation = operation.adjustmentCandidates.first {
                                                it.request.courseId.equals(targetSnapshot.classCode, ignoreCase = true)
                                            }.copy(vacancy = targetSnapshot.effectiveVacancy)
                                            operation.targetObservation = targetObservation
                                            operation.stage = SwitchStage.CLICKING_ADJUSTMENT_TARGET
                                            operation.actionLog.add(
                                                "本地选定目标课堂 ${targetSnapshot.classCode}：" +
                                                    "${targetSnapshot.selectedCount}+${targetSnapshot.pendingCount}<${targetSnapshot.classroomCapacity}"
                                            )
                                            webView?.evaluateJavascript(
                                                CourseCheckScriptUtils.getClickAdjustmentApplyScript(
                                                    targetClassCode = targetSnapshot.classCode,
                                                    selectedCount = targetSnapshot.selectedCount!!,
                                                    selectionLimit = targetSnapshot.selectionLimit!!,
                                                    classroomCapacity = targetSnapshot.classroomCapacity!!,
                                                    pendingCount = targetSnapshot.pendingCount!!
                                                ),
                                                null
                                            )
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onAdjustmentApplyResult(success: Boolean, message: String) {
                                        Log.d(TAG, "Adjustment apply result: success=$success, message=$message")
                                        mainHandler.post adjustmentApplyResult@{
                                            val operation = activeSwitch
                                            if (operation == null ||
                                                operation.path != SwitchPath.ADJUSTMENT_APPLY ||
                                                operation.stage != SwitchStage.CLICKING_ADJUSTMENT_TARGET
                                            ) return@adjustmentApplyResult
                                            if (!success) {
                                                failSwitch("定位目标课堂并申请失败：$message")
                                                return@adjustmentApplyResult
                                            }
                                            operation.stage = SwitchStage.WAITING_APPLICATION_FORM
                                            operation.actionLog.add(message)
                                            adjustmentFormScriptInjected = false
                                            mainHandler.postDelayed({ injectAdjustmentFormScript() }, 1500)
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onAdjustmentSubmitClicked(success: Boolean, message: String) {
                                        Log.d(TAG, "Adjustment submit click result: success=$success, message=$message")
                                        mainHandler.post adjustmentSubmitClicked@{
                                            val operation = activeSwitch
                                            if (operation == null ||
                                                operation.path != SwitchPath.ADJUSTMENT_APPLY ||
                                                operation.stage != SwitchStage.FILLING_APPLICATION_FORM
                                            ) return@adjustmentSubmitClicked
                                            if (!success) {
                                                failSwitch("填写或提交换班申请失败：$message")
                                                return@adjustmentSubmitClicked
                                            }
                                            operation.stage = SwitchStage.WAITING_SUBMIT_OUTCOME
                                            operation.actionLog.add(message)
                                            mainHandler.postDelayed({
                                                webView?.evaluateJavascript(
                                                    CourseCheckScriptUtils.getCheckAdjustmentSubmitOutcomeScript(),
                                                    null
                                                )
                                            }, 500)
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onAdjustmentSubmitOutcome(hasPopup: Boolean, message: String) {
                                        Log.d(TAG, "Adjustment submit outcome: hasPopup=$hasPopup, message=$message")
                                        mainHandler.post adjustmentSubmitOutcome@{
                                            val operation = activeSwitch
                                            if (operation == null ||
                                                operation.path != SwitchPath.ADJUSTMENT_APPLY ||
                                                operation.stage != SwitchStage.WAITING_SUBMIT_OUTCOME
                                            ) return@adjustmentSubmitOutcome
                                            if (hasPopup) {
                                                failSwitch("提交换班申请后出现弹窗：$message")
                                            } else {
                                                completeSwitch(CourseSwitchState.PENDING_VERIFICATION, message)
                                            }
                                        }
                                    }
                                    
                                    @JavascriptInterface
                                    fun onCourseNotFound(code: String) {
                                        Log.w(TAG, "Course not found")
                                        mainHandler.post courseNotFound@{
                                            val operation = activeSwitch
                                            if (operation != null &&
                                                operation.stage == SwitchStage.SEARCHING_TARGET_AFTER_DROP &&
                                                operation.targetObservation.request.courseId.equals(code, ignoreCase = true)
                                            ) {
                                                failSwitch("退课后未找到目标课堂 $code")
                                                return@courseNotFound
                                            }
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
                                            val operation = activeSwitch
                                            if (operation?.path == SwitchPath.ADJUSTMENT_APPLY &&
                                                url.startsWith(COURSE_ADJUSTMENT_URL_PREFIX)
                                            ) {
                                                when (operation.stage) {
                                                    SwitchStage.WAITING_ADJUSTMENT_PAGE -> {
                                                        view?.postDelayed({ openAdjustmentCourseTable() }, 500)
                                                        return
                                                    }
                                                    SwitchStage.WAITING_APPLICATION_FORM -> {
                                                        view?.postDelayed({ injectAdjustmentFormScript() }, 500)
                                                        return
                                                    }
                                                    else -> Unit
                                                }
                                            }
                                            if (url.contains("id.ustc.edu.cn") || url.contains("passport.ustc.edu.cn")) {
                                                view?.evaluateJavascript(LoginScriptUtils.getCredentialCaptureScript(), null)
                                                view?.evaluateJavascript(
                                                    LoginScriptUtils.getSecondFactorAutoRequestScript(verificationCodeMethod),
                                                    null
                                                )
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
                                                failSwitchOrReload()
                                            }
                                        }
                                    }
                                    
                                    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                                        super.onReceivedHttpError(view, request, errorResponse)
                                        Log.w(TAG, "HTTP error: ${errorResponse?.statusCode}, url: ${request?.url}")
                                        
                                        // 只处理主框架的错误
                                        if (request?.isForMainFrame == true && !isResumed) {
                                            mainHandler.post {
                                                failSwitchOrReload()
                                            }
                                        }
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                        result?.confirm()
                                        val operation = activeSwitch
                                        if (operation?.path == SwitchPath.ADJUSTMENT_APPLY &&
                                            operation.stage in setOf(
                                                SwitchStage.FILLING_APPLICATION_FORM,
                                                SwitchStage.WAITING_SUBMIT_OUTCOME
                                            )
                                        ) {
                                            view?.post {
                                                failSwitch("提交换班申请后出现弹窗：${message.orEmpty()}")
                                            }
                                            return true
                                        }
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
                                        val operation = activeSwitch
                                        if (operation != null) {
                                            if (operation.path == SwitchPath.ADJUSTMENT_APPLY &&
                                                operation.stage in setOf(
                                                    SwitchStage.FILLING_APPLICATION_FORM,
                                                    SwitchStage.WAITING_SUBMIT_OUTCOME
                                                )
                                            ) {
                                                view?.post {
                                                    failSwitch("提交换班申请后出现确认弹窗：${message.orEmpty()}")
                                                }
                                            } else {
                                                operation.actionLog.add("已确认页面提示：${message.orEmpty()}")
                                            }
                                            return true
                                        }
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

                                    override fun onJsPrompt(
                                        view: WebView?,
                                        url: String?,
                                        message: String?,
                                        defaultValue: String?,
                                        result: JsPromptResult?
                                    ): Boolean {
                                        val operation = activeSwitch
                                        if (operation?.path == SwitchPath.ADJUSTMENT_APPLY &&
                                            operation.stage in setOf(
                                                SwitchStage.FILLING_APPLICATION_FORM,
                                                SwitchStage.WAITING_SUBMIT_OUTCOME
                                            )
                                        ) {
                                            result?.cancel()
                                            view?.post {
                                                failSwitch("提交换班申请后出现输入弹窗：${message.orEmpty()}")
                                            }
                                            return true
                                        }
                                        return super.onJsPrompt(view, url, message, defaultValue, result)
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
