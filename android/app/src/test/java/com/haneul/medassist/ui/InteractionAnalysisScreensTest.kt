package com.haneul.medassist.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionAnalysisScreensTest {
    @Test
    fun `only valid http and https evidence links are accepted`() {
        assertTrue(isValidHttpUrl("https://nedrug.mfds.go.kr/example"))
        assertTrue(isValidHttpUrl("http://example.org/source"))
        assertFalse(isValidHttpUrl("javascript:alert(1)"))
        assertFalse(isValidHttpUrl("file:///tmp/source"))
        assertFalse(isValidHttpUrl("not a url"))
        assertFalse(isValidHttpUrl(null))
    }
}
