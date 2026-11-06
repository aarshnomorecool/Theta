package com.seasaw.theta.benchmark

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE_NAME = "in.seasaw.browser"
private const val UI_TIMEOUT_MILLIS = 20_000L
private const val CONSENT_TIMEOUT_MILLIS = 5_000L
private const val PAGE_SETTLE_MILLIS = 10_000L
private const val SCROLL_REPEATS = 6
private const val SCROLL_SPEED = 3_000

/** A real long article, so that the scroll benchmark measures what a person does. Needs a network connection. */
private const val LONG_PAGE_URL = "https://en.wikipedia.org/wiki/India"

/** A fresh install opens on the consent screen, which has to be passed before any browsing can be measured. */
private fun MacrobenchmarkScope.acceptConsentIfShown() {
    device.wait(Until.findObject(By.text("Agree and continue")), CONSENT_TIMEOUT_MILLIS)?.click()
    device.waitForIdle()
}

private fun MacrobenchmarkScope.openLongPage() {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LONG_PAGE_URL)).setPackage(PACKAGE_NAME)
    startActivityAndWait(intent)
    device.wait(Until.hasObject(By.res("android:id/content")), UI_TIMEOUT_MILLIS)
    Thread.sleep(PAGE_SETTLE_MILLIS)
}

/** Budget: cold start to a usable new-tab page under 1.2 s. */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private fun startup(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = compilationMode,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        acceptConsentIfShown()
    }

    @Test
    fun coldStartWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun coldStartWithBaselineProfile() = startup(CompilationMode.Partial())
}

/** Budget: under 5% janky frames while scrolling. */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollLongPage() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        iterations = 3,
        startupMode = StartupMode.COLD,
        setupBlock = {
            startActivityAndWait()
            acceptConsentIfShown()
            openLongPage()
        },
    ) {
        val content = device.findObject(By.res("android:id/content"))
        content.setGestureMargin(device.displayWidth / SCROLL_REPEATS)
        repeat(SCROLL_REPEATS) { content.scroll(Direction.DOWN, 1f, SCROLL_SPEED) }
    }
}

/** Regenerate whenever startup code changes: ./gradlew :app:generateBaselineProfile */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE_NAME) {
        pressHome()
        startActivityAndWait()
        acceptConsentIfShown()
        device.wait(Until.hasObject(By.text("Speed dial")), UI_TIMEOUT_MILLIS)
        openLongPage()
    }
}
