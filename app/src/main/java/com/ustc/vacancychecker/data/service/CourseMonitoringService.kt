package com.ustc.vacancychecker.data.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.ustc.vacancychecker.MainActivity
import com.ustc.vacancychecker.VacancyCheckerApp
import com.ustc.vacancychecker.data.local.CourseRepository
import com.ustc.vacancychecker.data.worker.ClassVacancyWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps the user-requested monitoring schedule alive while the app is in the background.
 * WorkManager still performs each individual check so network constraints and retries remain intact.
 */
@AndroidEntryPoint
class CourseMonitoringService : Service() {

    @Inject
    lateinit var repository: CourseRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitoringJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile
    private var strongBackgroundTracking = false

    override fun onCreate() {
        super.onCreate()
        startInForeground(buildNotification(courseCount = null, intervalMinutes = null))
        cancelLegacyPeriodicWork()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            return START_NOT_STICKY
        }

        if (intent?.hasExtra(EXTRA_STRONG_BACKGROUND_TRACKING) == true) {
            updateStrongBackgroundTracking(
                intent.getBooleanExtra(EXTRA_STRONG_BACKGROUND_TRACKING, false)
            )
        }

        if (monitoringJob?.isActive != true) {
            monitoringJob = serviceScope.launch {
                observeConfigurationAndScheduleChecks()
            }
        }
        // A null intent is only delivered when Android recreates a previously sticky service.
        return if (intent == null || strongBackgroundTracking) START_STICKY else START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!strongBackgroundTracking) {
            Log.i(TAG, "App removed from recent tasks; stopping non-strong monitoring")
            stopMonitoring()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        monitoringJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    /**
     * A foreground service raises process priority but does not keep the CPU running after the
     * display turns off. Keep a partial wake lock for exactly the lifetime of active monitoring so
     * coroutine timers and the work they enqueue are not postponed until the next screen wake-up.
     */
    @SuppressLint("WakelockTimeout")
    @Synchronized
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return

        val powerManager = getSystemService(PowerManager::class.java)
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            Log.w(
                TAG,
                "Battery optimization exemption not granted; Doze can suspend monitoring"
            )
        }
        wakeLock = powerManager
            .newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$packageName:course-monitoring"
            )
            .apply {
                setReferenceCounted(false)
                acquire()
            }
        Log.d(TAG, "CPU wake lock acquired")
    }

    @Synchronized
    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
        Log.d(TAG, "CPU wake lock released")
    }

    private suspend fun observeConfigurationAndScheduleChecks() {
        combine(
            repository.monitoringIntervalFlow,
            repository.trackedCoursesFlow,
            repository.strongBackgroundTrackingFlow
        ) { interval, courses, strongTracking ->
            MonitoringConfiguration(
                intervalMinutes = interval,
                courseCount = courses.count { it.isEffectivelyMonitoring },
                strongBackgroundTracking = strongTracking
            )
        }
            .distinctUntilChanged()
            .collectLatest { configuration ->
                if (configuration.intervalMinutes <= 0 || configuration.courseCount == 0) {
                    Log.i(TAG, "Monitoring disabled or no enabled courses; stopping service")
                    stopMonitoring()
                    return@collectLatest
                }

                updateStrongBackgroundTracking(configuration.strongBackgroundTracking)
                updateNotification(configuration)
                enqueueCheck()

                val delayMillis = configuration.intervalMinutes.toLong() * 60_000L
                while (true) {
                    delay(delayMillis)
                    enqueueCheck()
                }
            }
    }

    private fun enqueueCheck() {
        val request = ClassVacancyWorker.buildImmediateOneTimeRequest()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            ClassVacancyWorker.IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
        Log.d(TAG, "Course vacancy check enqueued")
    }

    private fun updateNotification(configuration: MonitoringConfiguration) {
        startInForeground(
            buildNotification(configuration.courseCount, configuration.intervalMinutes)
        )
    }

    private fun updateStrongBackgroundTracking(enabled: Boolean) {
        strongBackgroundTracking = enabled
        if (enabled) {
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }
    }

    private fun startInForeground(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            VacancyCheckerApp.MONITORING_SERVICE_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    private fun buildNotification(courseCount: Int?, intervalMinutes: Int?): Notification {
        val contentIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = if (courseCount != null && intervalMinutes != null) {
            "每 $intervalMinutes 分钟检查 $courseCount 门课程"
        } else {
            "正在启动课程空位监控"
        }

        return NotificationCompat.Builder(this, VacancyCheckerApp.FOREGROUND_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("课程空位监控运行中")
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun cancelLegacyPeriodicWork() {
        WorkManager.getInstance(applicationContext)
            .cancelUniqueWork(ClassVacancyWorker.LEGACY_PERIODIC_WORK_NAME)
    }

    private fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        cancelLegacyPeriodicWork()
        WorkManager.getInstance(applicationContext)
            .cancelUniqueWork(ClassVacancyWorker.IMMEDIATE_WORK_NAME)
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private data class MonitoringConfiguration(
        val intervalMinutes: Int,
        val courseCount: Int,
        val strongBackgroundTracking: Boolean
    )

    companion object {
        private const val TAG = "CourseMonitoringService"
        private const val ACTION_START = "com.ustc.vacancychecker.action.START_MONITORING"
        private const val ACTION_STOP = "com.ustc.vacancychecker.action.STOP_MONITORING"
        private const val EXTRA_STRONG_BACKGROUND_TRACKING =
            "com.ustc.vacancychecker.extra.STRONG_BACKGROUND_TRACKING"

        fun start(context: Context, strongBackgroundTracking: Boolean) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(ClassVacancyWorker.LEGACY_PERIODIC_WORK_NAME)
            ContextCompat.startForegroundService(
                context,
                Intent(context, CourseMonitoringService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_STRONG_BACKGROUND_TRACKING, strongBackgroundTracking)
            )
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context.applicationContext).apply {
                cancelUniqueWork(ClassVacancyWorker.LEGACY_PERIODIC_WORK_NAME)
                cancelUniqueWork(ClassVacancyWorker.IMMEDIATE_WORK_NAME)
            }
            context.stopService(
                Intent(context, CourseMonitoringService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
