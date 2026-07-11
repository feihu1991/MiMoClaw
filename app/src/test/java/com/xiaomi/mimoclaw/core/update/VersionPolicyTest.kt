package com.xiaomi.mimoclaw.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionPolicyTest {
    @Test
    fun `next build is considered an update`() {
        val remote = requireNotNull(VersionPolicy.parse("build-5"))
        assertTrue(VersionPolicy.shouldUpdate(remote, 4, "4.0.0-test"))
        assertFalse(VersionPolicy.shouldUpdate(remote, 5, "4.0.0-test"))
    }

    @Test
    fun `semantic versions compare all components`() {
        val patch = requireNotNull(VersionPolicy.parse("v4.0.1"))
        val older = requireNotNull(VersionPolicy.parse("v3.9.9"))
        assertTrue(VersionPolicy.shouldUpdate(patch, 4, "4.0.0-test"))
        assertFalse(VersionPolicy.shouldUpdate(older, 4, "4.0.0-test"))
    }

    @Test
    fun `invalid tags are rejected`() {
        assertFalse(VersionPolicy.parse("release-latest") != null)
        assertFalse(VersionPolicy.parse("v4.0") != null)
    }
}
