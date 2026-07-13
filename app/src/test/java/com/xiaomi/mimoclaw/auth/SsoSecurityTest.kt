package com.xiaomi.mimoclaw.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SsoSecurityTest {
    @Test
    fun `navigation requires https and exact host`() {
        assertTrue(SsoNavigationPolicy.isAllowed("https://account.xiaomi.com/fe/service/login"))
        assertTrue(SsoNavigationPolicy.isAllowed("https://login.xiaomi.com/pass/serviceLogin"))
        assertTrue(SsoNavigationPolicy.isAllowed("https://passport.xiaomi.com/oauth2/authorize"))
        assertTrue(SsoNavigationPolicy.isAllowed("https://aistudio.xiaomimimo.com/sts"))
        assertFalse(SsoNavigationPolicy.isAllowed("http://account.xiaomi.com/fe/service/login"))
        assertFalse(SsoNavigationPolicy.isAllowed("https://account.xiaomi.com.evil.example/login"))
        assertFalse(SsoNavigationPolicy.isAllowed("javascript:alert(1)"))
    }

    @Test
    fun `cookie lookup matches complete name`() {
        val header = "serviceToken=abc; xiaomichatbot_ph=def; myserviceToken=wrong"
        assertTrue(CookieHeader.contains(header, "serviceToken"))
        assertTrue(CookieHeader.contains(header, "xiaomichatbot_ph"))
        assertFalse(CookieHeader.contains(header, "Token"))
        assertFalse(CookieHeader.contains(header, "service"))
    }
}
