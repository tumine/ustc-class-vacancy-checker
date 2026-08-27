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

    @Test
    fun `drop-only script refuses to drop when switch button also exists`() {
        val script = CourseCheckScriptUtils.getClickDropButtonScript("011144.01")

        assertTrue(script.contains("switchButton"))
        assertTrue(script.contains("同时存在换班按钮，安全拒绝点击退课"))
        assertTrue(script.indexOf("if (switchButton)") < script.indexOf("dropButtons[0].click()"))
    }

    @Test
    fun `combined-button script clicks switch and single-course switch only`() {
        val script = CourseCheckScriptUtils.getClickSingleCourseSwitchScript("011144.01")

        assertTrue(script.contains("!hasDrop || switches.length === 0"))
        assertTrue(script.contains("switches[0].click()"))
        assertTrue(script.contains("=== '单课换班'"))
        assertFalse(script.contains("dropButtons[0].click()"))
    }

    @Test
    fun `adjustment scripts search apply fill reason and submit`() {
        val searchScript = CourseCheckScriptUtils.getSearchAndApplyAdjustmentScript("011144.02")
        val submitScript = CourseCheckScriptUtils.getFillAndSubmitAdjustmentScript()
        val outcomeScript = CourseCheckScriptUtils.getCheckAdjustmentSubmitOutcomeScript()

        assertTrue(searchScript.contains("011144.02"))
        assertTrue(searchScript.contains("t === '申请'"))
        assertTrue(submitScript.contains("申请原因及学生本人签名"))
        assertTrue(submitScript.contains("同课程换班"))
        assertTrue(submitScript.contains("t === '提交'"))
        assertTrue(outcomeScript.contains("fullText"))
        assertTrue(outcomeScript.contains("换班提交成功、待核验"))
    }
}
