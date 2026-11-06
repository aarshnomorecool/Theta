package com.seasaw.theta.feature.browser

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seasaw.theta.core.ui.ThetaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun addressBarShowsTheShortAddressUntilFocused() {
        compose.setContent {
            ThetaTheme { AddressBar("https://www.example.com/path", false, false, {}, {}, {}) }
        }
        compose.onNodeWithTag(BrowserTestTags.ADDRESS_FIELD).assertTextContains("example.com/path")
        compose.onNodeWithTag(BrowserTestTags.ADDRESS_FIELD).performClick()
        compose.onNodeWithTag(BrowserTestTags.ADDRESS_FIELD).assertTextContains("https://www.example.com/path")
    }

    @Test
    fun addressBarSubmitsWhatWasTyped() {
        var submitted: String? = null
        compose.setContent {
            ThetaTheme { AddressBar("", false, false, { submitted = it }, {}, {}) }
        }
        val field = compose.onNodeWithTag(BrowserTestTags.ADDRESS_FIELD)
        field.performTextClearance()
        field.performTextInput("example.com")
        field.performImeAction()
        assertEquals("example.com", submitted)
    }

    @Test
    fun tabSwitcherSelectsAndClosesTabs() {
        val selected = mutableListOf<String>()
        val closed = mutableListOf<String>()
        compose.setContent {
            ThetaTheme {
                TabSwitcher(
                    tabs = listOf(
                        BrowserTab("a", "https://a.example", "Alpha"),
                        BrowserTab("b", "https://b.example", "Beta"),
                    ),
                    selectedTabId = "a",
                    onSelect = { selected += it },
                    onClose = { closed += it },
                    onNewTab = {},
                    onCloseIncognito = {},
                    onDismiss = {},
                    adSlot = {},
                )
            }
        }
        compose.onNodeWithText("Beta").assertIsDisplayed()
        compose.onNodeWithTag(TabSwitcherTestTags.CARD_PREFIX + "b").performClick()
        compose.onNodeWithTag(TabSwitcherTestTags.CLOSE_PREFIX + "a").performClick()
        assertEquals(listOf("b"), selected)
        assertEquals(listOf("a"), closed)
    }

    @Test
    fun tabSwitcherKeepsIncognitoTabsInTheirOwnSection() {
        compose.setContent {
            ThetaTheme {
                TabSwitcher(
                    tabs = listOf(
                        BrowserTab("a", "https://a.example", "Regular page"),
                        BrowserTab("p", "https://p.example", "Private page", isIncognito = true),
                    ),
                    selectedTabId = "a",
                    onSelect = {},
                    onClose = {},
                    onNewTab = {},
                    onCloseIncognito = {},
                    onDismiss = {},
                    adSlot = {},
                )
            }
        }
        compose.onNodeWithText("Regular page").assertIsDisplayed()
        compose.onNodeWithText("Private page").assertDoesNotExist()
        compose.onNodeWithTag(TabSwitcherTestTags.INCOGNITO_SECTION).performClick()
        compose.onNodeWithText("Private page").assertIsDisplayed()
        compose.onNodeWithText("Regular page").assertDoesNotExist()
    }

    /** Regression test: the tap that focused the field cleared the selection, so typing added to the old address. */
    @Test
    fun typingAfterTappingTheAddressBarReplacesTheAddress() {
        compose.setContent {
            ThetaTheme { AddressBar("https://www.example.com/path", false, false, {}, {}, {}) }
        }
        val field = compose.onNodeWithTag(BrowserTestTags.ADDRESS_FIELD)
        field.performClick()
        compose.waitForIdle()
        field.performTextInput("new.example")
        field.assertTextEquals("new.example")
    }

    /** Regression test: the menu crashed on open, because a scroller was nested inside DropdownMenu's own. */
    @Test
    fun menuOpensAndOffersPageActions() {
        compose.setContent {
            ThetaTheme {
                BrowserMenu(
                    expanded = true,
                    state = BrowserUiState(tabs = listOf(BrowserTab("a", "https://a.example")), selectedTabId = "a"),
                    navigation = BrowserNavigation(),
                    onEvent = {},
                    onShare = {},
                    onTextSize = {},
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("Find in page").assertIsDisplayed()
        compose.onNodeWithText("New incognito tab").assertIsDisplayed()
    }

    /** Project rule: an SSL error must have no way forward. */
    @Test
    fun certificateErrorOffersNoWayToContinue() {
        var retried = false
        compose.setContent {
            ThetaTheme { PageErrorView(isSslError = true, onRetry = { retried = true }) }
        }
        compose.onNodeWithText("Try again").assertDoesNotExist()
        assertEquals(false, retried)
    }
}
