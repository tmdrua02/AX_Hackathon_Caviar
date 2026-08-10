package com.haneul.medassist.di

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppModuleTest {
    @Test
    fun `demo API token accepts printable ASCII only`() {
        assertEquals("valid-token-123", AppModule.validDemoApiToken(" valid-token-123 "))
        assertNull(AppModule.validDemoApiToken(""))
        assertNull(AppModule.validDemoApiToken("현재 테스트 토큰"))
        assertNull(AppModule.validDemoApiToken("token\nvalue"))
    }
}
