package com.seasaw.theta.core.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdPolicyTest {
    private val fourMinutes = 240_000L
    private val gate = InterstitialGate { fourMinutes }

    private fun mayShow(
        now: Long = 0,
        isFirstSession: Boolean = false,
        isPageLoading: Boolean = false,
        enabled: Boolean = true,
    ) = gate.mayShow(now, isFirstSession, isPageLoading, enabled)

    @Test
    fun `allowed when nothing rules it out`() {
        assertTrue(mayShow())
    }

    @Test
    fun `never in the first session`() {
        assertFalse(mayShow(isFirstSession = true))
    }

    @Test
    fun `never while a page is loading`() {
        assertFalse(mayShow(isPageLoading = true))
    }

    @Test
    fun `remote kill switch wins`() {
        assertFalse(mayShow(enabled = false))
    }

    @Test
    fun `frequency cap holds until the full interval has passed`() {
        gate.recordShown(1_000)
        assertFalse(mayShow(now = 1_000))
        assertFalse(mayShow(now = 1_000 + fourMinutes - 1))
        assertTrue(mayShow(now = 1_000 + fourMinutes))
    }

    @Test
    fun `interval is read fresh so remote changes apply`() {
        var interval = fourMinutes
        val adjustable = InterstitialGate { interval }
        adjustable.recordShown(0)
        interval = 10_000
        assertTrue(
            adjustable.mayShow(10_000, isFirstSession = false, isPageLoading = false, interstitialsEnabled = true),
        )
    }

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
