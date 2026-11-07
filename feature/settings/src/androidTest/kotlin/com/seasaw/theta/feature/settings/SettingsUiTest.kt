package com.seasaw.theta.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seasaw.theta.core.data.settings.SettingFlag
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

    private fun showSettings(settings: UserSettings = UserSettings(), page: SettingsPage = SettingsPage.HOME) {
        compose.setContent {
            ThetaTheme {
                SettingsScreen(
                    state = SettingsUiState(settings),
                    versionName = "0.2.0",
                    showAdPrivacy = false,
                    onBack = {},
                    onEvent = { events += it },
                    onDefaultBrowser = {},
                    onOpenAdPrivacy = {},
                    onOpenLink = {},
                    startPage = page,
                )
            }
        }
    }

    @Test
    fun homeGroupsSettingsUnderClearHeadings() {
        showSettings()
        listOf("Features", "General", "Display").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Accessibility").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun searchFindsASettingAndOpensItsPage() {
        showSettings()
        compose.onNodeWithTag(SettingsTestTags.SEARCH).performTextInput("text size")
        compose.onNodeWithText("Page text size").performClick()
        compose.onNodeWithTag(SettingsTestTags.TEXT_SIZE).assertIsDisplayed()
    }

    @Test
    fun togglingAdBlockReportsTheNewValue() {
        showSettings(UserSettings(adBlockEnabled = true), SettingsPage.SHIELDS)
        compose.onNodeWithTag(SettingsTestTags.ADBLOCK).performClick()
        assertEquals(listOf<SettingsUiEvent>(SettingsUiEvent.SetAdBlock(false)), events)
    }

    @Test
    fun increasingContrastReportsTheNewValue() {
        showSettings(page = SettingsPage.ACCESSIBILITY)
        compose.onNodeWithTag(SettingsTestTags.HIGH_CONTRAST).performScrollTo().performClick()
        assertEquals(listOf<SettingsUiEvent>(SettingsUiEvent.Toggle(SettingFlag.HIGH_CONTRAST, true)), events)
    }

    @Test
    fun clearingBrowsingDataAsksFirstThenClearsTheDefaults() {
        showSettings(page = SettingsPage.CLEAR_DATA)
        compose.onNodeWithTag(SettingsTestTags.CLEAR_BUTTON).performScrollTo().performClick()
        assertEquals("Nothing is erased until the user confirms", emptyList<SettingsUiEvent>(), events)
        // The dialog's button, not the page's: the dialog is drawn last.
        compose.onAllNodesWithText("Clear data").onLast().performClick()
        assertEquals(listOf<SettingsUiEvent>(SettingsUiEvent.Clear(ClearSelection())), events)
    }

    @Test
    fun deletingAllDataAsksFirstThenTakesOneTap() {
        showSettings(page = SettingsPage.CLEAR_DATA)
        compose.onNodeWithTag(SettingsTestTags.DELETE_ALL).performScrollTo().performClick()
        assertEquals("Nothing is erased until the user confirms", emptyList<SettingsUiEvent>(), events)
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf<SettingsUiEvent>(SettingsUiEvent.DeleteAllData), events)
    }

    @Test
    fun filterListsAndIconsAreCredited() {
        showSettings(page = SettingsPage.LICENCES)
        compose.onNodeWithText("EasyList", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Lucide icons").assertIsDisplayed()
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
