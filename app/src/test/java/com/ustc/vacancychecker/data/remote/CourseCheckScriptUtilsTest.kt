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
    fun `select result script recognizes current iview result dialog`() {
        val script = CourseCheckScriptUtils.getCheckSelectResultScript()

        assertTrue(script.contains(".ivu-modal"))
        assertTrue(script.contains(".ivu-modal-wrap"))
        assertTrue(script.contains(".ivu-modal-header-inner"))
        assertTrue(script.contains(".ivu-modal-body"))
        assertTrue(script.contains("hasResultTitle"))
        assertTrue(script.contains("return reportResult(modal, false)"))
    }

    @Test
    fun `select result script checks failure before success`() {
        val script = CourseCheckScriptUtils.getCheckSelectResultScript()

        val errorCheck = script.indexOf("containsAny(messageText, errorPatterns)")
        val successCheck = script.indexOf("containsAny(messageText, successPatterns)")
        assertTrue(errorCheck >= 0)
        assertTrue(successCheck > errorCheck)
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
        assertTrue(script.contains("switchButton.click()"))
        assertTrue(script.contains("=== '单课换班'"))
        assertTrue(script.contains(".dropdown-menu a"))
        assertTrue(script.contains("findMenuLink(buttonGroup) || findMenuLink(document)"))
        assertFalse(script.contains("querySelectorAll('li, a"))
        assertFalse(script.contains("dropButtons[0].click()"))
    }

    @Test
    fun `adjustment table script reads combined seat column without applying`() {
        val tableScript = CourseCheckScriptUtils.getReadAdjustmentCourseTableScript()

        assertTrue(tableScript.contains("选中/选课上限/课堂容量/待审核人数"))
        assertTrue(tableScript.contains("selectedCount: Number(match[1])"))
        assertTrue(tableScript.contains("selectionLimit: Number(match[2])"))
        assertTrue(tableScript.contains("classroomCapacity: Number(match[3])"))
        assertTrue(tableScript.contains("pendingCount: Number(match[4])"))
        assertTrue(tableScript.contains("onAdjustmentCourseTableResult(JSON.stringify(payload))"))
        assertFalse(tableScript.contains("apply.click()"))
    }

    @Test
    fun `adjustment apply script rechecks exact snapshot before click`() {
        val applyScript = CourseCheckScriptUtils.getClickAdjustmentApplyScript(
            targetClassCode = "011144.02",
            selectedCount = 140,
            selectionLimit = 140,
            classroomCapacity = 143,
            pendingCount = 2
        )

        assertTrue(applyScript.contains("011144.02"))
        assertTrue(applyScript.contains("expectedCounts = [140, 140, 143, 2]"))
        assertTrue(applyScript.contains("var unchanged"))
        assertTrue(applyScript.indexOf("if (!unchanged)") < applyScript.indexOf("apply.click()"))
        assertTrue(applyScript.contains("text === '申请'"))
    }

    @Test
    fun `adjustment scripts fill reason and submit`() {
        val submitScript = CourseCheckScriptUtils.getFillAndSubmitAdjustmentScript()
        val outcomeScript = CourseCheckScriptUtils.getCheckAdjustmentSubmitOutcomeScript()

        assertTrue(submitScript.contains("申请原因及学生本人签名"))
        assertTrue(submitScript.contains("同课程换班"))
        assertTrue(submitScript.contains("t === '提交'"))
        assertTrue(outcomeScript.contains("fullText"))
        assertTrue(outcomeScript.contains("换班提交成功、待核验"))
    }
}
