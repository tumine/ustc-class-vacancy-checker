package com.ustc.vacancychecker.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseTrackingPlannerTest {
    @Test
    fun `legacy courses without authority key stay in separate groups`() {
        val requests = CourseTrackingPlanner.buildRequests(
            listOf(course("MATH1001.01"), course("MATH1001.02"))
        )

        assertEquals(2, requests.map { it.groupId }.distinct().size)
    }

    @Test
    fun `authoritative group is ordered by persisted priority`() {
        val requests = CourseTrackingPlanner.buildRequests(
            listOf(
                course("MATH1001.01", key = "catalog-course:42", priority = 2),
                course("MATH1001.02", key = "catalog-course:42", priority = 0),
                course("MATH1001.03", key = "catalog-course:42", priority = 1)
            )
        )

        assertEquals(listOf("MATH1001.02", "MATH1001.03", "MATH1001.01"), requests.map { it.courseId })
    }

    @Test
    fun `paused group is excluded without changing classroom switches`() {
        val paused = course("MATH1001.01", key = "catalog-course:42")
            .copy(groupMonitoringEnabled = false, isMonitoring = true)

        assertTrue(CourseTrackingPlanner.buildRequests(listOf(paused)).isEmpty())
        assertTrue(paused.isMonitoring)
    }

    @Test
    fun `disable and delete policies skip only the selected group`() {
        val selected = request("A.01", "course:a", 0, SelectedCourseBehavior.DISABLE_GROUP)
        assertTrue(CourseTrackingPlanner.shouldSkipAfterSelection(selected, request("A.02", "course:a", 1)))
        assertFalse(CourseTrackingPlanner.shouldSkipAfterSelection(selected, request("B.01", "course:b", 0)))
    }

    @Test
    fun `priority upgrade skips lower priorities but not higher priorities`() {
        val selected = request("A.02", "course:a", 2, SelectedCourseBehavior.PRIORITY_UPGRADE)
        assertFalse(CourseTrackingPlanner.shouldSkipAfterSelection(selected, request("A.01", "course:a", 1)))
        assertTrue(CourseTrackingPlanner.shouldSkipAfterSelection(selected, request("A.03", "course:a", 3)))
    }

    private fun course(id: String, key: String? = null, priority: Int? = null) = TrackedCourse(
        courseId = id,
        courseName = "Test",
        courseKey = key,
        priority = priority
    )

    private fun request(id: String, group: String, priority: Int, behavior: SelectedCourseBehavior = SelectedCourseBehavior.DISABLE_GROUP) =
        CourseCheckRequest(id, group, priority, false, behavior)
}
