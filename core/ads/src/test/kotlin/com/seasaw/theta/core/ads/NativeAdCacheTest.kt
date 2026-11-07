package com.seasaw.theta.core.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAdCacheTest {
    private val destroyed = mutableListOf<String>()
    private val cache = NativeAdCache<String, String>(refreshMillis = 60_000, retryMillis = 60_000) { destroyed += it }
    private val slot = "new_tab"

    @Test
    fun `a slot starts out loading so its space is reserved`() {
        assertEquals(NativeSlotState.Loading, cache.state(slot).value)
    }

    /** Regression test: every return to a screen used to request a new ad, piling up validator bubbles. */
    @Test
    fun `a slot that reappears keeps its ad instead of requesting another`() {
        assertTrue(cache.shouldLoad(slot, nowMillis = 0))
        cache.onLoaded(slot, "ad-1", nowMillis = 0)

        assertFalse(cache.shouldLoad(slot, nowMillis = 1_000))
        assertFalse(cache.shouldLoad(slot, nowMillis = 59_999))
        assertEquals(NativeSlotState.Showing("ad-1"), cache.state(slot).value)
    }

    @Test
    fun `only one request is in flight at a time`() {
        assertTrue(cache.shouldLoad(slot, nowMillis = 0))
        assertFalse(cache.shouldLoad(slot, nowMillis = 0))
    }

    @Test
    fun `an old ad is replaced and destroyed`() {
        cache.shouldLoad(slot, nowMillis = 0)
        cache.onLoaded(slot, "ad-1", nowMillis = 0)

        assertTrue(cache.shouldLoad(slot, nowMillis = 60_000))
        cache.onLoaded(slot, "ad-2", nowMillis = 60_000)

        assertEquals(NativeSlotState.Showing("ad-2"), cache.state(slot).value)
        assertEquals(listOf("ad-1"), destroyed)
    }

    @Test
    fun `a failure with nothing to show collapses the slot and waits before retrying`() {
        cache.shouldLoad(slot, nowMillis = 0)
        cache.onFailed(slot, nowMillis = 0)

        assertEquals(NativeSlotState.Empty, cache.state(slot).value)
        assertFalse(cache.shouldLoad(slot, nowMillis = 30_000))
        assertTrue(cache.shouldLoad(slot, nowMillis = 60_000))
    }

    @Test
    fun `a failed refresh keeps the ad already showing`() {
        cache.shouldLoad(slot, nowMillis = 0)
        cache.onLoaded(slot, "ad-1", nowMillis = 0)
        cache.shouldLoad(slot, nowMillis = 60_000)
        cache.onFailed(slot, nowMillis = 60_000)

        assertEquals(NativeSlotState.Showing("ad-1"), cache.state(slot).value)
        assertTrue(destroyed.isEmpty())
    }

    @Test
    fun `placements are independent`() {
        cache.shouldLoad(slot, nowMillis = 0)
        cache.onLoaded(slot, "ad-1", nowMillis = 0)
        assertTrue(cache.shouldLoad("history", nowMillis = 1_000))
    }
}
