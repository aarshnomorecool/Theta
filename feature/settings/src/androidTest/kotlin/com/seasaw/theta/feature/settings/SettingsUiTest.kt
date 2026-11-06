package com.seasaw.theta.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seasaw.theta.core.data.settings.UserSettings
import com.seasaw.theta.core.ui.ThetaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<SettingsUiEvent>()

    private fun showSettings(settings: UserSettings = UserSettings()) {
        compose.setContent {
            ThetaTheme {
                SettingsScreen(SettingsUiState(settings), "0.1.0", false, {}, { events += it }, {}, {}, {})
            }
        }
    }

    @Test
    fun togglingAdBlockReportsTheNewValue() {
        showSettings(UserSettings(adBlockEnabled = true))
        compose.onNodeWithTag(SettingsTestTags.ADBLOCK).performClick()
        assertEquals(listOf<SettingsUiEvent>(SettingsUiEvent.SetAdBlock(false)), events)
    }

    @Test
    fun deletingAllDataAsksFirstThenTakesOneTap() {
        showSettings()
        compose.onNodeWithTag(SettingsTestTags.DELETE_ALL).performScrollTo().performClick()
        assertEquals("Nothing is erased until the user confirms", emptyList<SettingsUiEvent>(), events)
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf<SettingsUiEvent>(SettingsUiEvent.DeleteAllData), events)
    }

    @Test
    fun filterListsAreCredited() {
        showSettings()
        compose.onNodeWithText("EasyList", substring = true).performScrollTo()
    }

    @Test
    fun consentDefaultsDiagnosticsToOff() {
        var answer: Boolean? = null
        compose.setContent { ThetaTheme { ConsentScreen(onContinue = { answer = it }) } }
        compose.onNodeWithTag(ConsentTestTags.CONTINUE).performClick()
        assertEquals(false, answer)
    }

    @Test
    fun consentRecordsAnOptIn() {
        var answer: Boolean? = null
        compose.setContent { ThetaTheme { ConsentScreen(onContinue = { answer = it }) } }
        compose.onNodeWithTag(ConsentTestTags.DIAGNOSTICS).performClick()
        compose.onNodeWithTag(ConsentTestTags.CONTINUE).performClick()
        assertEquals(true, answer)
    }
}
