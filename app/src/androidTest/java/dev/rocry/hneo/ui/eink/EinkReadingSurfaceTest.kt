package dev.rocry.hneo.ui.eink

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import dev.rocry.hneo.ui.theme.HneoTheme
import org.junit.Rule
import org.junit.Test

/** Real viewport regression: long lazy items must remain readable within the same item. */
class EinkReadingSurfaceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun singleLongItemPagesThroughItsTailAndBack() {
        showList(withHeader = false)

        compose.onNodeWithText("line-0").assertIsDisplayed()
        compose.onNodeWithText("line-8").assertIsNotDisplayed()
        compose.onNodeWithText("Prev").assertIsNotEnabled()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("line-8").assertIsDisplayed()
        compose.onNodeWithText("Prev").performClick()
        compose.onNodeWithText("line-0").assertIsDisplayed()

        turnToEnd()
        compose.onNodeWithText("line-23").assertIsDisplayed()
        compose.onNodeWithText("Next").assertIsNotEnabled()
        compose.onNodeWithText("Prev").performClick()
        compose.onNodeWithText("Next").assertIsDisplayed()
    }

    @Test
    fun lastLongItemHasPageControlsEvenWhenAllItemsAreVisible() {
        // Both lazy items are visible initially, but the last one's tail is below the viewport.
        showList(withHeader = true)

        compose.onNodeWithText("header").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("line-8").assertIsDisplayed()

        turnToEnd()
        compose.onNodeWithText("line-23").assertIsDisplayed()
        compose.onNodeWithText("Next").assertIsNotEnabled()
    }

    @Test
    fun repeatedSwipesPageThroughTheSameLongItemAfterRecomposition() {
        showList(withHeader = false)

        swipeToNextPage()
        compose.onNodeWithText("line-8").assertIsDisplayed()
        swipeToNextPage()
        compose.onNodeWithText("line-14").assertIsDisplayed()
        compose.onNodeWithText("Prev").performClick()
        compose.onNodeWithText("line-8").assertIsDisplayed()
    }

    private fun showList(withHeader: Boolean) {
        compose.setContent {
            HneoTheme(einkMode = true) {
                EinkPagedList(modifier = Modifier.width(320.dp).height(300.dp).testTag("surface")) {
                    if (withHeader) {
                        item { Box(modifier = Modifier.height(40.dp)) { Text("header") } }
                    }
                    item {
                        Column {
                            repeat(24) { index ->
                                Box(modifier = Modifier.height(40.dp)) { Text("line-$index") }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun swipeToNextPage() {
        compose.onNodeWithTag("surface").performTouchInput {
            // Start above the page chrome so the content surface receives the whole gesture.
            swipe(
                start = Offset(center.x, height * 0.65f),
                end = Offset(center.x, height * 0.15f),
                durationMillis = 150,
            )
        }
    }

    private fun turnToEnd() {
        repeat(10) {
            if (compose.onAllNodes(hasText("Next") and isEnabled()).fetchSemanticsNodes().isEmpty()) return
            compose.onNodeWithText("Next").performClick()
        }
        error("Long item did not reach its final page after 10 turns")
    }
}
