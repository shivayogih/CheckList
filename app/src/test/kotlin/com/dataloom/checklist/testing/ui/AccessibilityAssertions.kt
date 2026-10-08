package com.dataloom.checklist.testing.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue

/*
 * Automated accessibility checks for one rendered screen (CL-174, docs/accessibility.md). They are
 * written against Compose semantics, so they run on Robolectric without a device:
 *
 * 1. Every actionable element has a label (text or content description) for TalkBack.
 * 2. Every actionable element has a touch target of at least 48 x 48 dp.
 * 3. The screen has at least one heading (its title).
 * 4. Every checkbox row has a state description ("Completed" / "Not completed").
 * 5. No text is clipped or cut off (no visual overflow), which matters at 200% font scale.
 *
 * Text checks need real text measurement: run the test class with @GraphicsMode(NATIVE).
 */

private val anyNode = SemanticsMatcher("any node") { true }
private val minTouchTarget = 48.dp

/** Runs all checks and fails with every problem found, not just the first. */
fun ComposeTestRule.assertAccessible(screen: String) {
    waitForIdle()
    val problems = buildList {
        val merged = onAllNodes(anyNode).fetchSemanticsNodes(atLeastOneRootRequired = true)
        val unmerged = onAllNodes(anyNode, useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = true)

        merged.filter { it.isActionable() }.forEach { node ->
            if (node.label().isBlank()) add("unlabelled ${node.role()} control (node ${node.id}: ${node.config})")
            val minPx = with(node.layoutInfo.density) { minTouchTarget.toPx() }
            // touchBoundsInRoot is clipped to the visible area, so a control scrolled out of view
            // reports 0 x 0; its laid-out size is the real target then.
            val bounds = node.touchBoundsInRoot
            val width = maxOf(bounds.width, node.size.width.toFloat())
            val height = maxOf(bounds.height, node.size.height.toFloat())
            if (width + 0.5f < minPx || height + 0.5f < minPx) {
                val size = with(node.layoutInfo.density) { "${width.toDp()} x ${height.toDp()}" }
                add("touch target $size < 48dp for '${node.label()}'")
            }
        }
        if (unmerged.none { SemanticsProperties.Heading in it.config }) add("no heading")
        merged.filter {
            it.config.getOrNull(SemanticsProperties.Role) == Role.Checkbox &&
                SemanticsProperties.ToggleableState in it.config
        }.forEach { node ->
            if (node.config.getOrNull(SemanticsProperties.StateDescription).isNullOrBlank()) {
                add("checkbox row '${node.label()}' has no state description")
            }
        }
        unmerged
            .filter { SemanticsActions.GetTextLayoutResult in it.config }
            .filter { SemanticsProperties.EditableText !in it.config }
            .forEach { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                layouts.firstOrNull()?.takeIf { it.isCutOff() }?.let {
                    add("text is cut off: '${it.layoutInput.text}' (${it.describe()})")
                }
            }
    }
    assertTrue(
        "$screen: ${problems.size} accessibility problem(s):\n" + problems.joinToString("\n"),
        problems.isEmpty(),
    )
}

/**
 * Text the user cannot read in full: an ellipsis, a line wider than the laid-out box, or a box
 * shorter than the text. `hasVisualOverflow` is not used: the layout this action returns is
 * measured against the parent's maximum width, so its paragraph is wider than the text node
 * ("Rice": node 34px, paragraph 220px) and it always reported a width overflow.
 */
private fun TextLayoutResult.isCutOff(): Boolean =
    (0 until lineCount).any { isLineEllipsized(it) } ||
        widestLine() > size.width + 1 ||
        multiParagraph.height > size.height + 1

private fun TextLayoutResult.widestLine(): Float =
    (0 until lineCount).maxOfOrNull { getLineRight(it) - getLineLeft(it) } ?: 0f

private fun TextLayoutResult.describe(): String =
    "box $size, widest line ${widestLine()}, text height ${multiParagraph.height}, lines $lineCount, " +
        "ellipsized ${(0 until lineCount).any { isLineEllipsized(it) }}"

private fun SemanticsNode.isActionable(): Boolean =
    SemanticsActions.OnClick in config || SemanticsActions.SetText in config

private fun SemanticsNode.label(): String = listOfNotNull(
    config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
    config.getOrNull(SemanticsProperties.Text)?.joinToString(" "),
    config.getOrNull(SemanticsProperties.EditableText)?.text,
).joinToString(" ").trim()

private fun SemanticsNode.role(): String = config.getOrNull(SemanticsProperties.Role)?.toString() ?: "clickable"
