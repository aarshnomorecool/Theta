package com.seasaw.theta.feature.adblock

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.seasaw.theta.feature.adblock.engine.FilterEngine
import com.seasaw.theta.feature.adblock.engine.ResourceType
import com.seasaw.theta.feature.adblock.engine.SnapshotIo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The project budget: matching must stay under 1 ms per request on the test device.
 * Run against the real bundled lists, because synthetic rule sets flatter the matcher.
 *
 * Requests are timed in small batches. Reading the clock can cost tens of microseconds under a hypervisor, which would
 * otherwise swamp what is being measured. A batch still shows a pause from GC or JIT, but spreads it across the batch.
 */
@RunWith(AndroidJUnit4::class)
class FilterMatchBenchmarkTest {
    private class Sample(val url: String, val page: String, val type: ResourceType) {
        val host = hostOf(url)
        val pageHost = hostOf(page)
        val isThirdParty = host != pageHost

        private fun hostOf(value: String) = value.substringAfter("://").substringBefore('/').substringBefore('?')
    }

    private val home = "https://www.example.com/"
    private val document = Sample(home, home, ResourceType.DOCUMENT)
    private val ordinaryScript = Sample("https://cdn.example.com/assets/app.min.js?v=1234", home, ResourceType.SCRIPT)
    private val ordinaryStyle = Sample("https://cdn.example.com/css/site.css", home, ResourceType.STYLESHEET)
    private val adScript =
        Sample("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js", home, ResourceType.SCRIPT)
    private val analytics = Sample("https://www.google-analytics.com/analytics.js", home, ResourceType.SCRIPT)
    private val adTag =
        Sample("https://securepubads.g.doubleclick.net/tag/js/gpt.js", "https://news.example.in/", ResourceType.SCRIPT)

    private val samples = listOf(
        document,
        ordinaryScript,
        ordinaryStyle,
        adScript,
        analytics,
        adTag,
        Sample("https://img.example.com/photos/2026/11/hero-1280x720.webp", home, ResourceType.IMAGE),
        Sample("https://api.example.co.in/v2/feed?lang=hi&page=2", "https://www.example.co.in/", ResourceType.XHR),
        Sample("https://fonts.gstatic.com/s/roboto/v30/KFOmCnqEu92Fr1Mu4mxK.woff2", home, ResourceType.FONT),
        Sample("https://connect.facebook.net/en_US/fbevents.js", "https://shop.example.in/", ResourceType.SCRIPT),
        Sample(
            "https://static.example.org/widgets/banner_ad_300x250.png",
            "https://blog.example.org/",
            ResourceType.IMAGE,
        ),
        Sample(
            "https://tracker.example.net/collect?v=2&tid=UA-1234&cid=" + "a".repeat(400) +
                "&dl=https%3A%2F%2Fwww.example.com%2Flong%2Fpath",
            home,
            ResourceType.PING,
        ),
    )

    private fun loadEngine(): FilterEngine {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.assets.open("filters.bin").use { FilterEngine(SnapshotIo.read(it)) }
    }

    private fun FilterEngine.check(sample: Sample): Boolean =
        shouldBlock(sample.url, sample.host, sample.page, sample.pageHost, sample.isThirdParty, sample.type)

    @Test
    fun realListsBlockKnownAdsAndAllowOrdinaryContent() {
        val engine = loadEngine()
        assertTrue(engine.check(adScript))
        assertTrue(engine.check(analytics))
        assertTrue(engine.check(adTag))
        assertFalse(engine.check(document))
        assertFalse(engine.check(ordinaryScript))
        assertFalse(engine.check(ordinaryStyle))
    }

    @Test
    fun matchingStaysWithinOneMillisecondPerRequest() {
        val loadStart = System.nanoTime()
        val engine = loadEngine()
        val loadMillis = (System.nanoTime() - loadStart) / NANOS_PER_MILLI

        repeat(WARMUP_ROUNDS) { samples.forEach { engine.check(it) } }

        val perRequest = Array(samples.size) { LongArray(BATCHES) }
        var blocked = 0
        repeat(BATCHES) { batch ->
            samples.forEachIndexed { index, sample ->
                val start = System.nanoTime()
                repeat(BATCH_SIZE) { if (engine.check(sample)) blocked++ }
                perRequest[index][batch] = (System.nanoTime() - start) / BATCH_SIZE
            }
        }

        samples.forEachIndexed { index, sample ->
            val sorted = perRequest[index].sortedArray()
            Log.i(
                TAG,
                "medianUs=${micros(sorted[sorted.size / 2])} p99Us=${micros(sorted[sorted.size * 99 / 100])} " +
                    sample.url.take(LOGGED_URL_LENGTH),
            )
        }
        val all = perRequest.flatMap { it.asIterable() }.sorted()
        val p99 = all[all.size * 99 / 100]
        Log.i(
            TAG,
            "OVERALL rules=${engine.ruleCount} loadMs=$loadMillis blocked=$blocked " +
                "medianUs=${micros(all[all.size / 2])} p99Us=${micros(p99)} worstBatchUs=${micros(all.last())}",
        )
        assertTrue("p99 was ${micros(p99)} us", p99 < BUDGET_NANOS)
    }

    private fun micros(nanos: Long): String = "%.1f".format(nanos / NANOS_PER_MICRO)

    private companion object {
        const val TAG = "FilterBenchmark"
        const val WARMUP_ROUNDS = 2_000
        const val BATCHES = 300
        const val BATCH_SIZE = 20
        const val LOGGED_URL_LENGTH = 60
        const val NANOS_PER_MICRO = 1_000.0
        const val NANOS_PER_MILLI = 1_000_000L
        const val BUDGET_NANOS = 1_000_000L
    }
}
