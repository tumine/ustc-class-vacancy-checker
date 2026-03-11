package com.ustc.vacancychecker.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ustc.vacancychecker.data.local.CourseRepository
import com.ustc.vacancychecker.data.local.CredentialsManager
import com.ustc.vacancychecker.data.model.SelectResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ClassVacancyWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: CourseRepository,
    private val credentialsManager: CredentialsManager,
    private val bgJwChecker: BackgroundJwVacancyChecker
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "VacancyCheckWork"
        private const val KEY_SCHEDULE_NEXT = "schedule_next"
        private const val KEY_INTERVAL_MINUTES = "interval_minutes"

        fun buildOneTimeRequest(intervalMinutes: Long, recursive: Boolean = true): androidx.work.OneTimeWorkRequest {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            val data = androidx.work.workDataOf(
                KEY_SCHEDULE_NEXT to recursive,
                KEY_INTERVAL_MINUTES to intervalMinutes
            )

            // If interval is 0, start immediately, otherwise wait for intervalMinutes
            val delayMs = if (intervalMinutes > 0) intervalMinutes * 60 * 1000 else 0

            return androidx.work.OneTimeWorkRequest.Builder(ClassVacancyWorker::class.java)
                .setConstraints(constraints)
                .setInitialDelay(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()
        }

        /**
         * Builds a request that starts immediately but, after completing, re-schedules
         * the periodic chain using nextIntervalMinutes as the delay for subsequent runs.
         * @param nextIntervalMinutes interval in minutes for subsequent periodic runs (0 to disable re-scheduling)
         */
        fun buildImmediateOneTimeRequest(nextIntervalMinutes: Long): androidx.work.OneTimeWorkRequest {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            val data = androidx.work.workDataOf(
                KEY_SCHEDULE_NEXT to (nextIntervalMinutes > 0),
                KEY_INTERVAL_MINUTES to nextIntervalMinutes
            )

            return androidx.work.OneTimeWorkRequest.Builder(ClassVacancyWorker::class.java)
                .setConstraints(constraints)
                .setInputData(data)
                .build()
        }
    }

    override suspend fun doWork(): Result {
        Log.d("ClassVacancyWorker", "=== doWork() called ===")
        val allCourses = repository.getTrackedCourses()
        Log.d("ClassVacancyWorker", "Total courses: ${allCourses.size}")
        val courses = allCourses.filter { it.isMonitoring }
        Log.d("ClassVacancyWorker", "Monitoring courses: ${courses.size}")
        if (courses.isEmpty()) {
            Log.d("ClassVacancyWorker", "No courses to monitor, returning success")
            return Result.success()
        }

        Log.d("ClassVacancyWorker", "Starting background check for ${courses.size} courses")

        try {
            val username = credentialsManager.getUsername()
            val password = credentialsManager.getPassword()
            if (username == null || password == null) {
                Log.e("ClassVacancyWorker", "No credentials available for JW check")
                return Result.failure()
            }
            
            // 构建每个课程的自动选课开关映射
            val autoSelectMap = courses.associate { it.courseId to (it.autoSelectEnabled ?: false) }
            Log.d("ClassVacancyWorker", "Auto select map: $autoSelectMap")

            val classCodes = courses.map { it.courseId }
            val result = bgJwChecker.performCheck(classCodes, username, password, autoSelectMap)

            if (result.isSuccess) {
                val vacancyMap = result.getOrThrow()
                for (course in courses) {
                    val data = vacancyMap[course.courseId]
                    if (data != null) {
                        val vacancy = data.first
                        val selectResult = data.second
                        Log.d("ClassVacancyWorker", "Check ${course.courseId}: $vacancy vacancy available, selectResult=$selectResult")
                        
                        // 如果有选课结果
                        if (selectResult != null) {
                            if (selectResult.success) {
                                // 选课成功：发送通知并删除课程
                                sendSelectSuccessNotification(course.courseId, course.courseName, selectResult.message)
                                repository.removeTrackedCourse(course.courseId)
                            } else if (!selectResult.isAlreadySelected) {
                                // 选课失败：发送通知，存储反馈信息，关闭自动选课开关
                                sendSelectFailedNotification(course.courseId, course.courseName, selectResult.message)
                                repository.updateCourseStatus(
                                    courseId = course.courseId,
                                    vacancy = vacancy,
                                    autoSelectEnabled = false,
                                    lastSelectMessage = selectResult.message
                                )
                            } else {
                                // 已选课程：仅更新状态
                                repository.updateCourseStatus(course.courseId, vacancy)
                            }
                        } else if (vacancy > 0) {
                            // 只有空位但没有自动选课（可能已选或未启用自动选课）
                            sendVacancyNotification(course.courseId, course.courseName, vacancy)
                            repository.updateCourseStatus(course.courseId, vacancy)
                        } else {
                            // 没有空位，仅更新状态
                            repository.updateCourseStatus(course.courseId, vacancy)
                        }
                    } else {
                        Log.w("ClassVacancyWorker", "Course ${course.courseId} not found in jw data")
                        repository.updateCourseStatus(course.courseId, 0)
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
        } finally {
            val scheduleNext = inputData.getBoolean(KEY_SCHEDULE_NEXT, false)
            val intervalMinutes = inputData.getLong(KEY_INTERVAL_MINUTES, 0)
            
            if (!isStopped && scheduleNext && intervalMinutes > 0) {
                Log.d("ClassVacancyWorker", "Scheduling next check in $intervalMinutes minutes")
                val nextRequest = buildOneTimeRequest(intervalMinutes, true)
                androidx.work.WorkManager.getInstance(appContext).enqueueUniqueWork(
                    WORK_NAME,
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    nextRequest
                )
            }
        }

        return Result.success()
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
}
