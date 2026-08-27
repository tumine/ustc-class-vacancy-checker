package com.ustc.vacancychecker.data.model

/** 纯 Kotlin 的分组/排序规则，供 Worker 和单元测试共同使用。 */
object CourseTrackingPlanner {
    fun buildRequests(courses: List<TrackedCourse>): List<CourseCheckRequest> {
        val sourceOrder = courses.withIndex().associate { it.value.courseId to it.index }
        return courses
            .filter { it.isEffectivelyMonitoring }
            .groupBy { it.trackingGroupId }
            .values
            .flatMap { group ->
                group.sortedWith(
                    compareBy<TrackedCourse> { it.priority ?: sourceOrder.getValue(it.courseId) }
                        .thenBy { sourceOrder.getValue(it.courseId) }
                )
            }
            .mapIndexed { fallbackPriority, course ->
                CourseCheckRequest(
                    courseId = course.courseId,
                    groupId = course.trackingGroupId,
                    priority = course.priority ?: fallbackPriority,
                    autoSelectEnabled = course.autoSelectEnabled == true,
                    selectedCourseBehavior = course.effectiveSelectedCourseBehavior
                )
            }
    }

    fun shouldSkipAfterSelection(
        selected: CourseCheckRequest,
        candidate: CourseCheckRequest
    ): Boolean {
        if (candidate.groupId != selected.groupId) return false
        return when (selected.selectedCourseBehavior) {
            SelectedCourseBehavior.PRIORITY_UPGRADE -> candidate.priority >= selected.priority
            SelectedCourseBehavior.DISABLE_GROUP,
            SelectedCourseBehavior.DELETE_GROUP -> true
        }
    }
}
