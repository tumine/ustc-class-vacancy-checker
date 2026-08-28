package com.ustc.vacancychecker.ui.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ustc.vacancychecker.data.local.CourseRepository
import com.ustc.vacancychecker.data.model.TrackedCourse
import com.ustc.vacancychecker.data.model.SelectedCourseBehavior
import com.ustc.vacancychecker.data.model.ResolvedCourseIdentity
import com.ustc.vacancychecker.data.remote.CatalogCourseResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackerViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val catalogCourseResolver: CatalogCourseResolver
) : ViewModel() {

    private val _isReconcilingGroups = MutableStateFlow(false)
    val isReconcilingGroups: StateFlow<Boolean> = _isReconcilingGroups.asStateFlow()

    val trackedCourses: StateFlow<List<TrackedCourse>> = courseRepository.trackedCoursesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        reconcileLegacyCourseGroups()
    }

    private fun reconcileLegacyCourseGroups() {
        viewModelScope.launch {
            _isReconcilingGroups.value = true
            try {
                val legacyCourses = courseRepository.getTrackedCourses().filter { it.courseKey == null }
                if (legacyCourses.isEmpty()) return@launch
                val resolved = catalogCourseResolver.resolve(legacyCourses.map { it.courseId })
                courseRepository.mergeResolvedCourseGroups(
                    resolved.mapValues { (_, metadata) ->
                        ResolvedCourseIdentity(metadata.courseKey, metadata.courseNumber)
                    }
                )
            } catch (error: Exception) {
                android.util.Log.w("TrackerViewModel", "Failed to reconcile legacy course groups", error)
            } finally {
                _isReconcilingGroups.value = false
            }
        }
    }

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

    fun setCourseOrder(groupId: String, orderedCourseIds: List<String>) {
        viewModelScope.launch {
            courseRepository.setCourseOrder(groupId, orderedCourseIds)
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
