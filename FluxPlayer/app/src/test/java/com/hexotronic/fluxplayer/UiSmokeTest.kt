package com.hexotronic.fluxplayer

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UiSmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun allTabsRender() {
        rule.onNodeWithText("Zugriff erlauben").assertExists()
        rule.onNodeWithText("Netzwerk").performClick()
        rule.onNodeWithText("Abspielen").assertExists()
        rule.onNodeWithText("Verlauf").performClick()
        rule.onNodeWithText("Noch nichts abgespielt").assertExists()
        rule.onNodeWithText("Einstellungen").performClick()
        rule.onNodeWithText("Frame-Generierung").assertExists()
        rule.onNodeWithText("Modus").performClick()
        assertTrue(rule.onAllNodesWithText("Bewegungsinterpolation").fetchSemanticsNodes().isNotEmpty())
    }
}
