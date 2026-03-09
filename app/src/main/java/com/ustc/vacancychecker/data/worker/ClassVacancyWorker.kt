package com.ustc.vacancychecker.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ustc.vacancychecker.data.local.CourseRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import kotlin.random.Random

@HiltWorker
class ClassVacancyWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: CourseRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val courses = repository.getTrackedCourses().filter { it.isMonitoring }
        if (courses.isEmpty()) {
            return Result.success()
        }

        Log.d("ClassVacancyWorker", "Starting background check for ${courses.size} courses")

        try {
            // 目前使用 catalog 系统的接口作为检查源，其学期ID目前通过 catalog 硬编码为 421 (2026春)，
            // 真实情况可从 https://catalog.ustc.edu.cn/api/teach/semester/list 获取
            val semesterId = "421" 
            val url = "https://catalog.ustc.edu.cn/api/teach/lesson/list-for-teach/$semesterId"

            val client = OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(url)
                // 增加常规UA防止简单的拦截
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("ClassVacancyWorker", "API Request failed with code: ${response.code}")
                repository.updateCheckTimeForCourses(courses.map { it.courseId })
                return Result.retry()
            }

            val responseBody = response.body?.string()
            if (responseBody.isNullOrBlank()) {
                Log.e("ClassVacancyWorker", "Empty response from API")
                repository.updateCheckTimeForCourses(courses.map { it.courseId })
                return Result.retry()
            }

            // 解析 catalog.ustc.edu.cn 的返回 JSON 数组
            val jsonArray = JSONArray(responseBody)
            val vacancyMap = mutableMapOf<String, Int>()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val code = item.optString("code", "")
                val stdCount = item.optInt("stdCount", 0)
                val limitCount = item.optInt("limitCount", 0)
                
                if (code.isNotBlank()) {
                    val vacancy = maxOf(0, limitCount - stdCount)
                    vacancyMap[code] = vacancy
                }
            }

            for (course in courses) {
                val vacancy = vacancyMap[course.courseId]
                if (vacancy != null) {
                    Log.d("ClassVacancyWorker", "Check ${course.courseId}: $vacancy vacancy available")
                    repository.updateCourseStatus(course.courseId, vacancy)
                    
                    // 如果发现空位，发送警报并启动选课（结合下一阶段 TODO 1）
                    if (vacancy > 0) {
                        sendVacancyNotification(course.courseId, course.courseName, vacancy)
                    }
                } else {
                    Log.w("ClassVacancyWorker", "Course ${course.courseId} not found in catalog data")
                    repository.updateCourseStatus(course.courseId, null)
                }
            }

        } catch (e: Exception) {
            Log.e("ClassVacancyWorker", "Error while fetching vacancy data", e)
            repository.updateCheckTimeForCourses(courses.map { it.courseId })
            return Result.retry()
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
