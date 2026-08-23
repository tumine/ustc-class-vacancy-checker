package com.ustc.vacancychecker.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginScriptUtilsTest {

    @Test
    fun autoFillScript_excludesVerificationFieldsAndRequiresDistinctLoginInputs() {
        val script = LoginScriptUtils.getAutoFillScript("Alice", "Bob")

        assertTrue(script.contains("one-time-code"))
        assertTrue(script.contains("验证码"))
        assertTrue(script.contains("usernameInput === passwordInput"))
        assertTrue(script.contains("usernameElement === passwordElement"))
        assertFalse(script.contains("document.querySelector('.passwordInput input')"))
        assertFalse(script.contains("|| document.querySelector('input[type=\"text\"]')"))
    }

    @Test
    fun autoFillScript_verifiesOnlyTheOriginallyFilledElementsAndStops() {
        val script = LoginScriptUtils.getAutoFillScript("Alice", "Bob")

        assertTrue(script.contains("document.contains(usernameElement)"))
        assertTrue(script.contains("document.contains(passwordElement)"))
        assertTrue(script.contains("if (attempts >= 10)"))
        assertFalse(script.contains("var inputs = findInputs();\n                        if (!inputs.username || !inputs.password)"))
    }

    @Test
    fun credentialCaptureScript_doesNotTreatOneVerificationFieldAsBothCredentials() {
        val script = LoginScriptUtils.getCredentialCaptureScript()

        assertTrue(script.contains("isVerificationInput"))
        assertTrue(script.contains("usernameInput === passwordInput"))
        assertFalse(script.contains("document.querySelector('.passwordInput input')"))
    }
}
