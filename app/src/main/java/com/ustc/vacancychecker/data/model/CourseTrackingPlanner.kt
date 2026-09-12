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
                    selectedCourseBehavior = course.effectiveSelectedCourseBehavior,
                    pendingSwitchSourceId = course.pendingSwitchSourceId,
                    pendingSwitchTargetId = course.pendingSwitchTargetId
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

    /**
     * 返回允许自动换班的更高优先级课堂。关闭“自动选课”的课堂仍可被后台检查，
     * 但不能成为“优先级升级”的自动操作目标。
     */
    fun eligibleUpgradeCandidates(
        selected: CourseCheckRequest,
        requests: List<CourseCheckRequest>
    ): List<CourseCheckRequest> = requests
        .asSequence()
        .filter { candidate ->
            candidate.groupId == selected.groupId &&
                candidate.priority < selected.priority &&
                candidate.autoSelectEnabled
        }
        .sortedBy { it.priority }
        .toList()
}
