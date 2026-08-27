package com.ustc.vacancychecker.data.model

data class ResolvedCourseIdentity(
    val courseKey: String,
    val courseNumber: String? = null
)

data class CourseGroupMergeResult(
    val courses: List<TrackedCourse>,
    val changed: Boolean,
    val mergedGroupCount: Int
)

/**
 * 将历史未分组课堂按权威标识归组。这里只接受查询结果，不从课堂号推断课程。
 */
object CourseGroupMerger {
    fun merge(
        courses: List<TrackedCourse>,
        identities: Map<String, ResolvedCourseIdentity>
    ): CourseGroupMergeResult {
        if (courses.isEmpty() || identities.isEmpty()) {
            return CourseGroupMergeResult(courses, changed = false, mergedGroupCount = 0)
        }

        val sourceIndex = courses.withIndex().associate { it.value.courseId to it.index }
        val originalGroupIds = courses.associate { it.courseId to it.trackingGroupId }
        val updated = courses.map { course ->
            identities[course.courseId]?.let { identity ->
                course.copy(
                    courseKey = identity.courseKey,
                    courseNumber = identity.courseNumber ?: course.courseNumber
                )
            } ?: course
        }.toMutableList()

        var mergedGroupCount = 0
        identities.values.map { it.courseKey }.distinct().forEach { courseKey ->
            val members = updated.filter { it.courseKey == courseKey }
            if (members.isEmpty()) return@forEach

            val previouslyGrouped = members
                .filter { originalGroupIds[it.courseId] == "course:$courseKey" }
                .sortedWith(
                    compareBy<TrackedCourse> { it.priority ?: sourceIndex.getValue(it.courseId) }
                        .thenBy { sourceIndex.getValue(it.courseId) }
                )
            val newlyResolved = members
                .filterNot { originalGroupIds[it.courseId] == "course:$courseKey" }
                .sortedBy { sourceIndex.getValue(it.courseId) }
            val ordered = previouslyGrouped + newlyResolved
            val template = previouslyGrouped.firstOrNull() ?: ordered.first()
            val settingsEnabled = template.isGroupMonitoringEnabled
            val settingsBehavior = template.effectiveSelectedCourseBehavior

            if (ordered.map { originalGroupIds.getValue(it.courseId) }.distinct().size > 1) {
                mergedGroupCount++
            }
            ordered.forEachIndexed { priority, member ->
                val index = updated.indexOfFirst { it.courseId == member.courseId }
                updated[index] = updated[index].copy(
                    groupMonitoringEnabled = settingsEnabled,
                    selectedCourseBehavior = settingsBehavior,
                    priority = priority
                )
            }
        }

        return CourseGroupMergeResult(
            courses = updated,
            changed = updated != courses,
            mergedGroupCount = mergedGroupCount
        )
    }
}
