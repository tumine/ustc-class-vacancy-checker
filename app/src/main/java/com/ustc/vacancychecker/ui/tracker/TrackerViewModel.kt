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

    fun refreshAll(context: android.content.Context) {
        val intervalMinutes = monitoringInterval.value.toLong()
        val workRequest = com.ustc.vacancychecker.data.worker.ClassVacancyWorker.buildImmediateOneTimeRequest(intervalMinutes)
        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            com.ustc.vacancychecker.data.worker.ClassVacancyWorker.WORK_NAME,
            androidx.work.ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}
