package com.seasaw.theta.core.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdPolicyTest {
    @Test
    fun `no ad on a screen previewing a youtube page`() {
        assertFalse(AdPolicy.mayShowOnScreen(listOf("example.com", "m.youtube.com")))
    }

    @Test
    fun `ads are fine beside other previews or none`() {
        assertTrue(AdPolicy.mayShowOnScreen(listOf("example.com", null)))
        assertTrue(AdPolicy.mayShowOnScreen(emptyList()))
    }
}
