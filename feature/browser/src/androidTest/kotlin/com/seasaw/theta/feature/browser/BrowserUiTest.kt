package com.seasaw.theta.feature.browser

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
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
        compose.onNodeWithTag(BrowserTestTags.ADDRESS_FIELD).assertTextEquals("example.com")
        compose.onNodeWithTag(BrowserTestTags.ADDRESS_FIELD).performClick()
        compose.onNodeWithTag(BrowserTestTags.ADDRESS_FIELD).assertTextContains("https://www.example.com/path")
    }

    /** Regression test: credentials in the address made https://bank.example@evil.example read as bank.example. */
    @Test
    fun addressBarShowsTheRealHostNotTheCredentials() {
        compose.setContent {
            ThetaTheme { AddressBar("https://bank.example@evil.example/login", false, false, {}, {}, {}) }
        }
        compose.onNodeWithTag(BrowserTestTags.ADDRESS_FIELD).assertTextEquals("evil.example")
    }

    /** Regression test: an https page whose certificate failed still showed the "Secure connection" lock. */
    @Test
    fun certificateErrorRemovesTheLock() {
        compose.setContent {
            ThetaTheme {
                AddressBar("https://bad.example/", false, false, {}, {}, {}, isCertificateError = true)
            }
        }
        compose.onNodeWithContentDescription("Secure connection").assertDoesNotExist()
        compose.onNodeWithContentDescription("Connection not secure").assertIsDisplayed()
    }

    /** Regression test: private tabs replaced the "not secure" warning with the private-mode icon. */
    @Test
    fun privateTabsStillWarnAboutPlainHttp() {
        compose.setContent { ThetaTheme { AddressBar("http://plain.example/", true, false, {}, {}, {}) } }
        compose.onNodeWithContentDescription("Connection not secure").assertIsDisplayed()
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
        val events = mutableListOf<BrowserUiEvent>()
        compose.setContent {
            ThetaTheme {
                TabSwitcher(
                    state = BrowserUiState(
                        tabs = listOf(
                            BrowserTab("a", "https://a.example", "Alpha"),
                            BrowserTab("b", "https://b.example", "Beta"),
                        ),
                        selectedTabId = "a",
                    ),
                    onEvent = { events += it },
                    navigation = SwitcherNavigation(),
                )
            }
        }
        compose.onNodeWithText("Beta").assertIsDisplayed()
        compose.onNodeWithTag(TabSwitcherTestTags.CARD_PREFIX + "b").performClick()
        compose.onNodeWithTag(TabSwitcherTestTags.CLOSE_PREFIX + "a").performClick()
        assertEquals(listOf(BrowserUiEvent.SelectTab("b"), BrowserUiEvent.CloseTab("a")), events)
    }

    @Test
    fun tabSwitcherKeepsPrivateTabsInTheirOwnSection() {
        compose.setContent {
            ThetaTheme {
                TabSwitcher(
                    state = BrowserUiState(
                        tabs = listOf(
                            BrowserTab("a", "https://a.example", "Regular page"),
                            BrowserTab("p", "https://p.example", "Private page", isIncognito = true),
                        ),
                        selectedTabId = "a",
                    ),
                    onEvent = {},
                    navigation = SwitcherNavigation(),
                )
            }
        }
        compose.onNodeWithText("Regular page").assertIsDisplayed()
        compose.onNodeWithText("Private page").assertDoesNotExist()
        compose.onNodeWithTag(TabSwitcherTestTags.INCOGNITO_SECTION).performClick()
        compose.onNodeWithText("Private page").assertIsDisplayed()
        compose.onNodeWithText("Regular page").assertDoesNotExist()
    }

    @Test
    fun groupedTabsShowAsOneCardThatOpensTheGroup() {
        compose.setContent {
            ThetaTheme {
                TabSwitcher(
                    state = BrowserUiState(
                        tabs = listOf(
                            BrowserTab("a", "https://a.example", "Alpha", groupId = "g"),
                            BrowserTab("b", "https://b.example", "Beta", groupId = "g"),
                            BrowserTab("c", "https://c.example", "Gamma"),
                        ),
                        groups = listOf(TabGroup("g", "Cricket", 1)),
                        selectedTabId = "c",
                    ),
                    onEvent = {},
                    navigation = SwitcherNavigation(),
                )
            }
        }
        compose.onNodeWithText("Alpha").assertDoesNotExist()
        compose.onNodeWithTag(TabSwitcherTestTags.GROUP_PREFIX + "g").performClick()
        compose.onNodeWithText("Alpha").assertIsDisplayed()
        compose.onNodeWithText("Beta").assertIsDisplayed()
        compose.onNodeWithText("Gamma").assertDoesNotExist()
    }

    @Test
    fun selectedTabsCanBeGroupedFromTheSwitcher() {
        val events = mutableListOf<BrowserUiEvent>()
        compose.setContent {
            ThetaTheme {
                TabSwitcher(
                    state = BrowserUiState(
                        tabs = listOf(
                            BrowserTab("a", "https://a.example", "Alpha"),
                            BrowserTab("b", "https://b.example", "Beta"),
                        ),
                        selectedTabId = "a",
                    ),
                    onEvent = { events += it },
                    navigation = SwitcherNavigation(),
                )
            }
        }
        compose.onNodeWithTag(TabSwitcherTestTags.CARD_PREFIX + "a").performTouchInput { longClick() }
        compose.onNodeWithTag(TabSwitcherTestTags.CARD_PREFIX + "b").performClick()
        compose.onNodeWithTag(TabSwitcherTestTags.SELECTION_GROUP).performClick()
        compose.onNodeWithTag(TabGroupTestTags.NAME_FIELD).performTextInput("Work")
        compose.onNodeWithTag(TabGroupTestTags.CONFIRM).performClick()
        val created = events.filterIsInstance<BrowserUiEvent.CreateGroup>().single()
        assertEquals(setOf("a", "b"), created.tabIds)
        assertEquals("Work", created.name)
    }

    @Test
    fun switcherMenuOffersEveryShortcut() {
        compose.setContent {
            ThetaTheme {
                TabSwitcher(
                    state = BrowserUiState(
                        tabs = listOf(BrowserTab("a", "https://a.example", "Alpha")),
                        selectedTabId = "a",
                    ),
                    onEvent = {},
                    navigation = SwitcherNavigation(),
                )
            }
        }
        compose.onNodeWithTag(TabSwitcherTestTags.MENU).performClick()
        listOf(
            "New tab",
            "New private tab",
            "New tab group",
            "Close all tabs",
            "Select tabs",
            "Delete browsing data",
            "Settings",
        ).forEach { compose.onNodeWithText(it).assertIsDisplayed() }
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
        compose.onNodeWithText("New private tab").assertIsDisplayed()
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
