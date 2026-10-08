package com.dataloom.checklist.presentation.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dataloom.checklist.R
import com.dataloom.checklist.presentation.components.BackButton
import com.dataloom.checklist.presentation.components.PrimaryButton

/**
 * 00b to 00d Tutorial: a three-page pager with Back (hidden on page 1) and Skip on top, the
 * illustration, title and body in the middle, and the dots and the Next / Get started button at the
 * bottom. Swiping is an extra; the buttons alone are enough to move through the pages.
 */
@Composable
fun TutorialContent(
    page: Int,
    onPageSettled: (Int) -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = page) { TUTORIAL_PAGE_COUNT }
    // Buttons change the page through the ViewModel; the pager follows. A swipe reports back.
    LaunchedEffect(page) {
        if (pagerState.currentPage != page) pagerState.animateScrollToPage(page)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { onPageSettled(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (page > 0) BackButton(onBack) else Spacer(Modifier.size(48.dp))
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    stringResource(R.string.onboarding_skip),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            TutorialPage(index)
        }
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageDots(current = page)
            PrimaryButton(
                text = stringResource(if (page >= TUTORIAL_PAGE_COUNT - 1) R.string.onboarding_get_started else R.string.onboarding_next),
                onClick = onNext,
                leadingIcon = if (page >= TUTORIAL_PAGE_COUNT - 1) painterResource(R.drawable.ic_check) else null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TutorialPage(index: Int) {
    val (title, body) = when (index) {
        0 -> R.string.onboarding_page1_title to R.string.onboarding_page1_body
        1 -> R.string.onboarding_page2_title to R.string.onboarding_page2_body
        else -> R.string.onboarding_page3_title to R.string.onboarding_page3_body
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IllustrationPanel(modifier = Modifier.heightIn(max = 420.dp)) {
            when (index) {
                0 -> FirstIllustration()
                1 -> SecondIllustration()
                else -> ThirdIllustration()
            }
        }
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 32.dp)
                .semantics { heading() },
        )
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/**
 * Page dots (active 28 x 10 dp, others 10 x 10). For TalkBack they are one element that reads
 * "Page 1 of 3" and announces the new position whenever it changes, whether by swipe or by button.
 */
@Composable
private fun PageDots(current: Int) {
    val description = stringResource(R.string.onboarding_page_position, current + 1, TUTORIAL_PAGE_COUNT)
    Row(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(TUTORIAL_PAGE_COUNT) { index ->
            val active = index == current
            val width by animateDpAsState(if (active) 28.dp else 10.dp, label = "dotWidth")
            Box(
                modifier = Modifier
                    .width(width)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}
