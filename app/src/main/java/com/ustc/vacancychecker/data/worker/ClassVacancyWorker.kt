package com.ustc.vacancychecker.data.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.ustc.vacancychecker.VacancyCheckerApp
import com.ustc.vacancychecker.data.local.CourseRepository
import com.ustc.vacancychecker.data.local.CredentialsManager
import com.ustc.vacancychecker.data.model.SelectResult
import com.ustc.vacancychecker.data.model.CourseTrackingPlanner
import com.ustc.vacancychecker.data.model.CourseSwitchState
import com.ustc.vacancychecker.data.model.SelectedCourseBehavior
import com.ustc.vacancychecker.data.model.VacancyNotificationPolicy
import com.ustc.vacancychecker.data.remote.CatalogCourseResolver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ClassVacancyWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: CourseRepository,
    private val credentialsManager: CredentialsManager,
    private val bgJwChecker: BackgroundJwVacancyChecker,
    private val catalogCourseResolver: CatalogCourseResolver
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val LEGACY_PERIODIC_WORK_NAME = "VacancyCheckWork"
        const val IMMEDIATE_WORK_NAME = "VacancyCheckWorkImmediate"

        /**
         * 构建立即执行的一次性工作请求，用于手动刷新
         */
        fun buildImmediateOneTimeRequest(): androidx.work.OneTimeWorkRequest {
            Log.d("ClassVacancyWorker", "Building immediate request")
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            return androidx.work.OneTimeWorkRequest.Builder(ClassVacancyWorker::class.java)
                .setConstraints(constraints)
                .build()
        }
    }

    override suspend fun doWork(): Result {
        Log.d("ClassVacancyWorker", "=== doWork() called ===")
        val storedCourses = repository.getTrackedCourses()
        val missingCourseKeys = storedCourses
            .filter { it.isMonitoring && it.isGroupMonitoringEnabled && it.courseKey == null }
            .map { it.courseId }
        val resolvedMetadata = catalogCourseResolver.resolve(missingCourseKeys)
        for ((classCode, metadata) in resolvedMetadata) {
            repository.updateCourseMetadata(classCode, metadata.courseKey, metadata.courseNumber)
        }
        val allCourses = storedCourses.map { course ->
            resolvedMetadata[course.courseId]?.let { metadata ->
                course.copy(
                    courseKey = metadata.courseKey,
                    courseNumber = metadata.courseNumber ?: course.courseNumber,
                    courseName = metadata.courseName ?: course.courseName
                )
            } ?: course
        }
        Log.d("ClassVacancyWorker", "Total courses: ${allCourses.size}")
        val requests = CourseTrackingPlanner.buildRequests(allCourses)
        val courses = requests.mapNotNull { request -> allCourses.firstOrNull { it.courseId == request.courseId } }
        Log.d("ClassVacancyWorker", "Monitoring courses: ${courses.size}")
        
        if (courses.isEmpty()) {
            Log.d("ClassVacancyWorker", "No courses to monitor, returning success")
            return Result.success()
        }

        // 设置前台服务，确保后台执行
        val foregroundInfo = createForegroundInfo(courses.size)
        setForeground(foregroundInfo)
        
        Log.d("ClassVacancyWorker", "Starting background check for ${courses.size} courses")

        try {
            val username = credentialsManager.getUsername()
            val password = credentialsManager.getPassword()
            if (username == null || password == null) {
                Log.e("ClassVacancyWorker", "No credentials available for JW check")
                return Result.failure()
            }
            
            val classCodes = requests.map { it.courseId }
            val result = bgJwChecker.performCheck(requests, username, password)

            if (result.isSuccess) {
                val checkResults = result.getOrThrow()
                val handledGroups = mutableSetOf<String>()
                val handledSwitchGroups = mutableSetOf<String>()
                val groupsWithSwitchResult = requests.mapNotNull { request ->
                    request.groupId.takeIf { checkResults[request.courseId]?.switchResult != null }
                }.toSet()
                for (request in requests) {
                    val course = allCourses.firstOrNull { it.courseId == request.courseId } ?: continue
                    val data = checkResults[course.courseId]
                    if (data != null) {
                        val vacancy = data.vacancy
                        val selectResult = data.selectResult
                        Log.d("ClassVacancyWorker", "Check ${course.courseId}: $vacancy vacancy available, selectResult=$selectResult")

                        repository.updateCourseStatus(
                            courseId = course.courseId,
                            vacancy = vacancy,
                            isAlreadySelected = data.isAlreadySelected,
                            lastSelectMessage = selectResult?.message
                        )

                        data.switchResult?.takeIf { handledSwitchGroups.add(request.groupId) }?.let { switchResult ->
                            repository.updateGroupSwitchState(
                                groupId = request.groupId,
                                sourceCourseId = switchResult.sourceCourseId,
                                targetCourseId = switchResult.targetCourseId,
                                state = switchResult.state,
                                message = switchResult.message,
                                actionLog = switchResult.actionLog
                            )
                            sendSwitchNotification(
                                state = switchResult.state,
                                sourceCourseId = switchResult.sourceCourseId,
                                targetCourseId = switchResult.targetCourseId,
                                message = switchResult.message,
                                actionLog = switchResult.actionLog
                            )
                        }

                        when {
                            selectResult?.success == true && !selectResult.isAlreadySelected -> {
                                sendSelectSuccessNotification(course.courseId, course.courseName, selectResult.message)
                            }
                            selectResult != null && !selectResult.success -> {
                                sendSelectFailedNotification(course.courseId, course.courseName, selectResult.message)
                                repository.updateCourseStatus(
                                    courseId = course.courseId,
                                    vacancy = vacancy,
                                    autoSelectEnabled = false,
                                    lastSelectMessage = selectResult.message,
                                    isAlreadySelected = false
                                )
                            }
                            !data.isAlreadySelected &&
                                request.groupId !in groupsWithSwitchResult &&
                                VacancyNotificationPolicy.shouldNotify(
                                    currentVacancy = vacancy,
                                    previousCheckedVacancy = course.vacancy,
                                    lastNotifiedVacancy = course.lastNotifiedVacancy
                                ) -> {
                                sendVacancyNotification(course.courseId, course.courseName, vacancy)
                                repository.recordVacancyNotification(course.courseId, vacancy)
                            }
                        }

                        if (data.selectionConfirmed && handledGroups.add(request.groupId)) {
                            when (request.selectedCourseBehavior) {
                                SelectedCourseBehavior.DISABLE_GROUP ->
                                    repository.setGroupMonitoring(request.groupId, false)
                                SelectedCourseBehavior.DELETE_GROUP ->
                                    repository.removeTrackedGroup(request.groupId)
                                SelectedCourseBehavior.PRIORITY_UPGRADE -> {
                                    // 这里只应用安全的检查短路。自动退课/换班必须在页面按钮组合
                                    // 得到完整验证后由专用状态机执行，不能降级为盲点“退课”。
                                }
                            }
                        }
                    }
                }
            } else {
                Log.e("ClassVacancyWorker", "Background JW check failed", result.exceptionOrNull())
                repository.updateCheckTimeForCourses(classCodes)
                return Result.retry()
            }

        } catch (e: Exception) {
            Log.e("ClassVacancyWorker", "Error while fetching vacancy data", e)
            repository.updateCheckTimeForCourses(courses.map { it.courseId })
            return Result.retry()
        }

        return Result.success()
    }

    /**
     * 创建前台服务通知信息
     */
    private fun createForegroundInfo(courseCount: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(appContext, VacancyCheckerApp.FOREGROUND_CHANNEL_ID)
            .setContentTitle("正在监控课程空位")
            .setContentText("正在检查 $courseCount 门课程...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ 需要指定 foregroundServiceType
            ForegroundInfo(
                VacancyCheckerApp.WORKER_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            ForegroundInfo(VacancyCheckerApp.WORKER_NOTIFICATION_ID, notification)
        }
    }

    private fun sendVacancyNotification(courseId: String, courseName: String, vacancyCount: Int) {
        Log.i("ClassVacancyWorker", "🔔 NOTIFICATION: Course $courseId ($courseName) has $vacancyCount vacancies!")

        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        // Create notification intent (e.g. to open MainActivity or CourseCheckScreen)
        val intent = android.content.Intent(appContext, com.ustc.vacancychecker.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: android.app.PendingIntent = android.app.PendingIntent.getActivity(
            appContext, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(appContext, com.ustc.vacancychecker.VacancyCheckerApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Replace with your app's icon
            .setContentTitle("发现课程空位！")
            .setContentText("你关注的 [$courseName] ($courseId) 当前有 $vacancyCount 个余量，请尽快前往选课！")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Use courseId hash code as unique notification ID
        notificationManager.notify(courseId.hashCode(), notification)
    }
    
    private fun sendSelectSuccessNotification(courseId: String, courseName: String, message: String) {
        Log.i("ClassVacancyWorker", "🎉 NOTIFICATION: Course $courseId ($courseName) selected successfully!")

        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        val intent = android.content.Intent(appContext, com.ustc.vacancychecker.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: android.app.PendingIntent = android.app.PendingIntent.getActivity(
            appContext, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(appContext, com.ustc.vacancychecker.VacancyCheckerApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🎉 选课成功！")
            .setContentText("[$courseName] ($courseId) 已成功选课！")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify("${courseId}_select_success".hashCode(), notification)
    }
    
    private fun sendSelectFailedNotification(courseId: String, courseName: String, message: String) {
        Log.i("ClassVacancyWorker", "❌ NOTIFICATION: Course $courseId ($courseName) selection failed: $message")

        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        val intent = android.content.Intent(appContext, com.ustc.vacancychecker.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: android.app.PendingIntent = android.app.PendingIntent.getActivity(
            appContext, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(appContext, com.ustc.vacancychecker.VacancyCheckerApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("❌ 选课失败")
            .setContentText("[$courseName] ($courseId) 选课失败：$message")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText("[$courseName] ($courseId) 选课失败：$message"))
            .build()

        notificationManager.notify("${courseId}_select_failed".hashCode(), notification)
    }

    private fun sendSwitchNotification(
        state: CourseSwitchState,
        sourceCourseId: String,
        targetCourseId: String,
        message: String,
        actionLog: List<String>
    ) {
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val intent = android.content.Intent(appContext, com.ustc.vacancychecker.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            appContext,
            0,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val title = when (state) {
            CourseSwitchState.PENDING_VERIFICATION -> "换班申请已提交，待核验"
            CourseSwitchState.VERIFIED -> "换班成功"
            CourseSwitchState.FAILED -> "换班失败"
        }
        val details = buildString {
            append("$sourceCourseId → $targetCourseId\n$message")
            if (actionLog.isNotEmpty()) {
                append("\n\n执行记录：\n")
                append(actionLog.joinToString("\n"))
            }
        }
        val notification = NotificationCompat.Builder(appContext, VacancyCheckerApp.CHANNEL_ID)
            .setSmallIcon(
                if (state == CourseSwitchState.FAILED) android.R.drawable.ic_dialog_alert
                else android.R.drawable.ic_dialog_info
            )
            .setContentTitle(title)
            .setContentText("$sourceCourseId → $targetCourseId：$message")
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify("${sourceCourseId}_${targetCourseId}_switch".hashCode(), notification)
    }
}
