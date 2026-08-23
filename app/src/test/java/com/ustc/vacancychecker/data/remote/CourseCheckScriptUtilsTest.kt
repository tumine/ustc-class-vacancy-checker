package com.ustc.vacancychecker.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseCheckScriptUtilsTest {

    @Test
    fun `vacancy script waits for async std count refresh`() {
        val script = CourseCheckScriptUtils.getReadVacancyScript("011144.01")

        assertTrue(script.contains("hasLoadedCountState"))
        assertTrue(script.contains(".std-count-progress"))
        assertTrue(script.contains("text-primary"))
        assertTrue(script.contains("text-danger"))
        assertTrue(script.contains("Waiting for async std-count"))
    }

    @Test
    fun `vacancy script does not coerce an unreadable count to zero`() {
        val script = CourseCheckScriptUtils.getReadVacancyScript("011144.01")

        assertTrue(script.contains("element.textContent"))
        assertTrue(script.contains("stdCount !== null && limitCount !== null"))
        assertFalse(script.contains("parseInt(stdCountEl.innerText.trim()) || 0"))
    }

    @Test
    fun `vacancy script supports current course page selectors`() {
        val script = CourseCheckScriptUtils.getReadVacancyScript("011144.01")

        assertTrue(script.contains(".course-name-main"))
        assertTrue(script.contains(".teacher-name"))
    }
}
