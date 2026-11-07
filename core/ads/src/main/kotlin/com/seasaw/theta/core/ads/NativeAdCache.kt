package com.seasaw.theta.core.ads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a native ad slot should show. */
sealed interface NativeSlotState<out T> {
    /** An ad may still come, so the slot keeps its space reserved (ads rule 5). */
    data object Loading : NativeSlotState<Nothing>

    data class Showing<T>(val ad: T) : NativeSlotState<T>

    /** No ad is coming for now, so the slot collapses. */
    data object Empty : NativeSlotState<Nothing>
}

/**
 * Keeps one native ad per placement and decides when a new one may be requested.
 *
 * Without this, every slot asked for a fresh ad each time its screen reappeared: going back to the new-tab page,
 * opening the tab switcher or scrolling a list did it. That wasted data on cheap plans, and an app that requests far
 * more ads than it shows looks like ad spam to the network. Now a placement keeps its ad until [refreshMillis] has
 * passed, and a failure waits [retryMillis] before the next attempt.
 *
 * Generic over the ad type so these rules can be tested without the Ads SDK. Main thread only, like the SDK callbacks.
 */
class NativeAdCache<K : Any, T : Any>(
    private val refreshMillis: Long,
    private val retryMillis: Long,
    private val destroy: (T) -> Unit,
) {
    private val states = HashMap<K, MutableStateFlow<NativeSlotState<T>>>()
    private val loadedAt = HashMap<K, Long>()
    private val failedAt = HashMap<K, Long>()
    private val inFlight = HashSet<K>()

    fun state(key: K): StateFlow<NativeSlotState<T>> = flowFor(key).asStateFlow()

    /** Returns true when a request should go out now, and marks it as in flight. */
    fun shouldLoad(key: K, nowMillis: Long): Boolean {
        if (key in inFlight) return false
        val current = flowFor(key).value
        val loaded = loadedAt[key]
        if (current is NativeSlotState.Showing && loaded != null && nowMillis - loaded < refreshMillis) return false
        val failed = failedAt[key]
        if (current !is NativeSlotState.Showing && failed != null && nowMillis - failed < retryMillis) return false
        inFlight += key
        return true
    }

    /** Swaps in the new ad and destroys the one it replaces, which the SDK requires to free the old ad's resources. */
    fun onLoaded(key: K, ad: T, nowMillis: Long) {
        inFlight -= key
        failedAt -= key
        val flow = flowFor(key)
        val previous = (flow.value as? NativeSlotState.Showing)?.ad
        flow.value = NativeSlotState.Showing(ad)
        loadedAt[key] = nowMillis
        if (previous != null && previous !== ad) destroy(previous)
    }

    /** A failed refresh keeps showing the ad already there. Only a slot with nothing to show collapses. */
    fun onFailed(key: K, nowMillis: Long) {
        inFlight -= key
        failedAt[key] = nowMillis
        val flow = flowFor(key)
        if (flow.value !is NativeSlotState.Showing) flow.value = NativeSlotState.Empty
    }

    private fun flowFor(key: K) = states.getOrPut(key) { MutableStateFlow(NativeSlotState.Loading) }
}
