package com.ustc.vacancychecker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VacancyCheckerApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
            
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 课程空余提醒渠道
            val name = "课程空余提醒"
            val descriptionText = "检测到有空余名额时发送的高优先级通知"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }

            // 前台服务通知渠道
            val fgName = "后台监控服务"
            val fgDescriptionText = "应用在后台时持续监控课程空位"
            val fgChannel = NotificationChannel(FOREGROUND_CHANNEL_ID, fgName, NotificationManager.IMPORTANCE_LOW).apply {
                description = fgDescriptionText
                enableVibration(false)
                setShowBadge(false)
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(fgChannel)
        }
    }

    companion object {
        const val CHANNEL_ID = "VacancyAlertChannel"
        const val FOREGROUND_CHANNEL_ID = "ForegroundServiceChannel"
        const val FOREGROUND_NOTIFICATION_ID = 1001
    }
}
