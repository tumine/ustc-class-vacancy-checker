package com.ustc.vacancychecker.ui.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ustc.vacancychecker.data.local.CourseRepository
import com.ustc.vacancychecker.data.model.TrackedCourse
import com.ustc.vacancychecker.data.model.SelectedCourseBehavior
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

    fun toggleGroupMonitoring(groupId: String, enabled: Boolean) {
        viewModelScope.launch {
            courseRepository.setGroupMonitoring(groupId, enabled)
        }
    }

    fun setGroupBehavior(groupId: String, behavior: SelectedCourseBehavior) {
        viewModelScope.launch {
            courseRepository.setGroupBehavior(groupId, behavior)
        }
    }

    fun removeGroup(groupId: String) {
        viewModelScope.launch {
            courseRepository.removeTrackedGroup(groupId)
        }
    }

    fun moveCourse(groupId: String, courseId: String, direction: Int) {
        viewModelScope.launch {
            courseRepository.moveCourseWithinGroup(groupId, courseId, direction)
        }
    }

    fun refreshAll(context: android.content.Context) {
        android.util.Log.d("TrackerViewModel", "refreshAll called")
        val workRequest = com.ustc.vacancychecker.data.worker.ClassVacancyWorker.buildImmediateOneTimeRequest()

        // 入队立即执行的工作
        androidx.work.WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            com.ustc.vacancychecker.data.worker.ClassVacancyWorker.IMMEDIATE_WORK_NAME,
            androidx.work.ExistingWorkPolicy.KEEP,
            workRequest
        )
        android.util.Log.d("TrackerViewModel", "Immediate work enqueued successfully")
    }
}
