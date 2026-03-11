package com.ustc.vacancychecker.ui.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ustc.vacancychecker.data.local.CourseRepository
import com.ustc.vacancychecker.data.model.TrackedCourse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.work.await

@HiltViewModel
class TrackerViewModel @Inject constructor(
    private val courseRepository: CourseRepository
) : ViewModel() {

    val trackedCourses: StateFlow<List<TrackedCourse>> = courseRepository.trackedCoursesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val monitoringInterval: StateFlow<Int> = courseRepository.monitoringIntervalFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 15
        )

    fun removeCourse(courseId: String) {
        viewModelScope.launch {
            courseRepository.removeTrackedCourse(courseId)
        }
    }

    fun toggleMonitoring(courseId: String, isMonitoring: Boolean) {
        viewModelScope.launch {
            courseRepository.toggleMonitoringStatus(courseId, isMonitoring)
        }
    }

    fun toggleAutoSelect(courseId: String, enabled: Boolean) {
        viewModelScope.launch {
            courseRepository.toggleAutoSelectEnabled(courseId, enabled)
        }
    }

    fun clearSelectMessage(courseId: String) {
        viewModelScope.launch {
            courseRepository.clearSelectMessage(courseId)
        }
    }

    fun refreshAll(context: android.content.Context) {
        val intervalMinutes = monitoringInterval.value.toLong()
        val workRequest = com.ustc.vacancychecker.data.worker.ClassVacancyWorker.buildImmediateOneTimeRequest(intervalMinutes)

        // REPLACE 会自动替换同名 unique work，无需先 cancel
        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            com.ustc.vacancychecker.data.worker.ClassVacancyWorker.WORK_NAME,
            androidx.work.ExistingWorkPolicy.REPLACE,
            workRequest
        )

        if (com.ustc.vacancychecker.BuildConfig.DEBUG) {
            android.util.Log.d("TrackerViewModel", "refreshAll called, interval=$intervalMinutes")
            android.util.Log.d("TrackerViewModel", "Work enqueued successfully")

            // 检查网络状态（使用现代 API）
            val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            val hasInternet = networkCapabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            android.util.Log.d("TrackerViewModel", "Network has internet capability: $hasInternet")

            // 检查 WorkManager 状态
            viewModelScope.launch {
                try {
                    val workInfo = androidx.work.WorkManager.getInstance(context)
                        .getWorkInfosForUniqueWork(com.ustc.vacancychecker.data.worker.ClassVacancyWorker.WORK_NAME)
                        .await()
                    android.util.Log.d("TrackerViewModel", "WorkInfo: $workInfo")
                } catch (e: Exception) {
                    android.util.Log.e("TrackerViewModel", "Failed to get work info", e)
                }
            }
        }
    }
}
