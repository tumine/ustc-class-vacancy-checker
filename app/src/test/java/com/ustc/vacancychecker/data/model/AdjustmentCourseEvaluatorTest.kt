package com.ustc.vacancychecker.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdjustmentCourseEvaluatorTest {
    @Test
    fun `selected plus pending must be strictly below classroom capacity`() {
        assertFalse(snapshot("A.01", 140, 140, 142, 2).isAvailable)
        assertTrue(snapshot("A.01", 140, 140, 143, 2).isAvailable)
    }

    @Test
    fun `selection limit does not restrict adjustment availability`() {
        val course = snapshot("A.01", selected = 140, limit = 140, capacity = 145, pending = 1)

        assertTrue(course.isAvailable)
        assertEquals(4, course.effectiveVacancy)
    }

    @Test
    fun `all tracked candidates are evaluated locally and highest available priority wins`() {
        val result = AdjustmentCourseEvaluator.evaluate(
            candidateCourseIds = listOf("A.01", "A.02", "A.03"),
            snapshots = listOf(
                snapshot("A.01", 140, 140, 142, 2),
                snapshot("A.02", 120, 120, 125, 1),
                snapshot("A.03", 50, 50, 60, 0)
            )
        )

        assertEquals("A.02", result.target?.classCode)
        assertEquals(mapOf("A.01" to 0, "A.02" to 4, "A.03" to 10), result.effectiveVacancies)
        assertNull(result.error)
    }

    @Test
    fun `full candidates finish without target or error`() {
        val result = AdjustmentCourseEvaluator.evaluate(
            candidateCourseIds = listOf("A.01", "A.02"),
            snapshots = listOf(
                snapshot("A.01", 140, 140, 142, 2),
                snapshot("A.02", 100, 100, 100, 0)
            )
        )

        assertNull(result.target)
        assertNull(result.error)
        assertEquals(mapOf("A.01" to 0, "A.02" to 0), result.effectiveVacancies)
    }

    @Test
    fun `malformed higher priority candidate blocks lower priority application`() {
        val malformed = snapshot("A.01", 0, 0, 0, 0).copy(
            selectedCount = null,
            parseError = "格式错误"
        )
        val result = AdjustmentCourseEvaluator.evaluate(
            candidateCourseIds = listOf("A.01", "A.02"),
            snapshots = listOf(malformed, snapshot("A.02", 10, 10, 20, 0))
        )

        assertNull(result.target)
        assertTrue(result.error.orEmpty().contains("A.01"))
    }

    @Test
    fun `unrelated malformed page row does not affect tracked candidates`() {
        val unrelated = snapshot("B.01", 0, 0, 0, 0).copy(
            pendingCount = null,
            parseError = "格式错误"
        )
        val result = AdjustmentCourseEvaluator.evaluate(
            candidateCourseIds = listOf("A.01"),
            snapshots = listOf(unrelated, snapshot("A.01", 10, 10, 20, 0))
        )

        assertEquals("A.01", result.target?.classCode)
        assertNull(result.error)
    }

    private fun snapshot(
        code: String,
        selected: Int,
        limit: Int,
        capacity: Int,
        pending: Int,
        hasApplyButton: Boolean = true
    ) = AdjustmentCourseSnapshot(
        classCode = code,
        rawSeatText = "$selected/$limit/$capacity/$pending",
        selectedCount = selected,
        selectionLimit = limit,
        classroomCapacity = capacity,
        pendingCount = pending,
        hasApplyButton = hasApplyButton
    )
}
