package com.dataloom.checklist.testing.screenshot

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Assertions that a picture cannot make. A golden shows what a component looks like; these fail the build
 * when the layout breaks in a way people with large text, long translations or motor impairments feel:
 * clipped text, a tap target under 48 dp, or content pushed off the screen.
 */

/** Everything the assertions found wrong, one line each. */
private fun describe(node: SemanticsNode): String {
    val texts = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("|") { it.text }
    val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("|")
    return listOfNotNull(texts?.let { "text=\"$it\"" }, description?.let { "description=\"$it\"" }).joinToString(" ")
        .ifEmpty { "node ${node.id}" }
}

/**
 * Fails when any text is cut: wider than its box (a word that cannot wrap), taller than its box (a max
 * line count or a fixed height), or ellipsized. Looks at the unmerged tree so every `Text` is checked, and
 * also fails when a node sticks out past the right edge of the scene.
 */
internal fun ComposeTestRule.assertNoTextOverflow() {
    waitForIdle()
    val problems = mutableListOf<String>()
    val nodes = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        .fetchSemanticsNodes()
    nodes.forEach { node ->
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        results.forEach { layout ->
            if (layout.didOverflowWidth) problems += "text wider than its box: ${describe(node)}"
            if (layout.didOverflowHeight) problems += "text taller than its box: ${describe(node)}"
            if (layout.hasVisualOverflow && !layout.didOverflowWidth && !layout.didOverflowHeight) {
                problems += "text is truncated: ${describe(node)}"
            }
        }
    }
    val scene = onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, SCENE_TAG)).fetchSemanticsNodes()
        .firstOrNull()
    if (scene != null) {
        val limit = scene.boundsInRoot.right + 1f
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { it.boundsInRoot.right > limit }
            .forEach { problems += "content is pushed past the right edge: ${describe(it)}" }
    }
    check(problems.isEmpty()) { "Layout problems:\n" + problems.distinct().joinToString("\n") { " - $it" } }
}

/**
 * Fails when something you can tap is smaller than [min] in width or height. "Tappable" means the node has
 * a click action and is enabled. It measures the real layout size, not the 48 dp that Compose adds around
 * small targets for the touch system, so a 44 dp control has to sit inside a 48 dp box on purpose.
 */
internal fun ComposeTestRule.assertTouchTargetsAtLeast(min: Dp = 48.dp) {
    waitForIdle()
    val problems = mutableListOf<String>()
    val nodes = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = false)
        .fetchSemanticsNodes()
    nodes.filter { SemanticsProperties.Disabled !in it.config }.forEach { node ->
        val density = node.layoutInfo.density
        val minPx = min.value * density.density
        val width = node.size.width.toFloat()
        val height = node.size.height.toFloat()
        // One pixel of rounding is not a failure.
        if (width + 1f < minPx || height + 1f < minPx) {
            problems += "touch target too small (${width / density.density} x ${height / density.density} dp): ${describe(node)}"
        }
    }
    check(problems.isEmpty()) { "Touch targets under $min:\n" + problems.distinct().joinToString("\n") { " - $it" } }
}

/** [assertTouchTargetsAtLeast] with the 48 dp of the accessibility guidelines. */
internal fun ComposeTestRule.assertTouchTargetsAtLeast48() = assertTouchTargetsAtLeast(48.dp)
