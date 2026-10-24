package com.alo.downloader

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Launches the real app UI and walks through the three top-level tabs. */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS))
        .around(compose)

    @Test
    fun tabsAreReachable() {
        compose.onNodeWithText("Nothing in the queue").assertExists()
        compose.onAllNodesWithText("Library").onFirst().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("No downloads yet").assertExists()
        compose.onAllNodesWithText("Settings").onFirst().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Accounts and cookies").assertExists()
    }
}
