package com.ustc.vacancychecker.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ustc.vacancychecker.data.local.CourseRepository
import com.ustc.vacancychecker.data.local.CredentialsManager
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
        val courses = repository.getTrackedCourses().filter { it.isMonitoring }
        if (courses.isEmpty()) {
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

            val classCodes = courses.map { it.courseId }
            val result = bgJwChecker.performCheck(classCodes, username, password)

            if (result.isSuccess) {
                val vacancyMap = result.getOrThrow()
                for (course in courses) {
                    val vacancy = vacancyMap[course.courseId]
                    if (vacancy != null) {
                        Log.d("ClassVacancyWorker", "Check ${course.courseId}: $vacancy vacancy available")
                        repository.updateCourseStatus(course.courseId, vacancy)
                        
                        if (vacancy > 0) {
                            sendVacancyNotification(course.courseId, course.courseName, vacancy)
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
}
