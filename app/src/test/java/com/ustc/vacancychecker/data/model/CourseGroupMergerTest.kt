package com.ustc.vacancychecker.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseGroupMergerTest {
    @Test
    fun `historical classrooms merge only after authority resolves same course key`() {
        val original = listOf(course("MATH1001.01"), course("MATH1001.02"), course("PHYS1001.01"))

        val result = CourseGroupMerger.merge(
            original,
            mapOf(
                "MATH1001.01" to ResolvedCourseIdentity("catalog-course:42"),
                "MATH1001.02" to ResolvedCourseIdentity("catalog-course:42")
            )
        )

        assertTrue(result.changed)
        assertEquals(1, result.mergedGroupCount)
        assertEquals(
            listOf("course:catalog-course:42", "course:catalog-course:42", "legacy:PHYS1001.01"),
            result.courses.map { it.trackingGroupId }
        )
        assertEquals(listOf(0, 1), result.courses.take(2).map { it.priority })
        assertNull(result.courses.last().courseKey)
    }

    @Test
    fun `existing group settings and order win when historical classroom joins`() {
        val original = listOf(
            course("MATH1001.02").copy(isMonitoring = false),
            course("MATH1001.01", "catalog-course:42").copy(
                priority = 0,
                groupMonitoringEnabled = false,
                selectedCourseBehavior = SelectedCourseBehavior.PRIORITY_UPGRADE
            )
        )

        val result = CourseGroupMerger.merge(
            original,
            mapOf("MATH1001.02" to ResolvedCourseIdentity("catalog-course:42"))
        )
        val group = result.courses.sortedBy { it.priority }

        assertEquals(listOf("MATH1001.01", "MATH1001.02"), group.map { it.courseId })
        assertTrue(group.all { !it.isGroupMonitoringEnabled })
        assertTrue(group.all { it.effectiveSelectedCourseBehavior == SelectedCourseBehavior.PRIORITY_UPGRADE })
        assertFalse(group.last().isMonitoring)
    }

    @Test
    fun `unresolved historical classroom is not guessed into a group`() {
        val original = listOf(course("MATH1001.01"), course("MATH1001.02"))

        val result = CourseGroupMerger.merge(
            original,
            mapOf("MATH1001.01" to ResolvedCourseIdentity("catalog-course:42"))
        )

        assertEquals("course:catalog-course:42", result.courses[0].trackingGroupId)
        assertEquals("legacy:MATH1001.02", result.courses[1].trackingGroupId)
    }

    private fun course(id: String, key: String? = null) = TrackedCourse(
        courseId = id,
        courseName = "Test",
        courseKey = key
    )
}
