package com.ustc.vacancychecker.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ustc.vacancychecker.data.local.CourseRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Restores monitoring after a reboot only when the saved configuration requires it. */
class MonitoringBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ReceiverEntryPoint::class.java
                ).courseRepository()
                val interval = repository.monitoringIntervalFlow.first()
                val strongBackgroundTracking = repository.strongBackgroundTrackingFlow.first()
                val hasMonitoredCourses = repository.getTrackedCourses().any { it.isEffectivelyMonitoring }
                if (interval > 0 && hasMonitoredCourses) {
                    CourseMonitoringService.start(
                        context.applicationContext,
                        strongBackgroundTracking
                    )
                } else {
                    CourseMonitoringService.stop(context.applicationContext)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to restore course monitoring after boot", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "MonitoringBootReceiver"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun courseRepository(): CourseRepository
    }
}
