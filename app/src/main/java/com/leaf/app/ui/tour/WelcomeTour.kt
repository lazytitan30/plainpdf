package com.leaf.app.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leaf.app.R
import com.leaf.app.util.PdfFeatures
import com.leaf.app.ui.common.QuireButton
import com.leaf.app.ui.common.QuireTextButton
import com.leaf.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/** One screen of the tour: a picture, a headline, two sentences and, rarely, a note. */
private class TourPage(val image: Int, val title: Int, val body: Int, val note: Int? = null)

/**
 * Shown once, on the first launch. Five pages in order of what people value most, each
 * skippable at any moment; the back button skips too. Big type, one idea per page.
 */
@Composable
fun WelcomeTour(onFinished: () -> Unit) {
    // Forms, drawing and highlighting depend on the phone's PDF extension. The page stays,
    // so people learn the tool exists, but on a phone without it a note says so plainly
    // rather than sending them to look for a button that is not there.
    val editNote = when {
        PdfFeatures.annotationsAvailable -> null
        PdfFeatures.formEditingAvailable -> R.string.tour_edit_note_no_drawing
        else -> R.string.tour_edit_note_unavailable
    }
    val pages = listOf(
        TourPage(R.drawable.tour_welcome, R.string.tour_welcome_title, R.string.tour_welcome_body),
        TourPage(R.drawable.tour_sign, R.string.tour_sign_title, R.string.tour_sign_body),
        TourPage(R.drawable.tour_scan, R.string.tour_scan_title, R.string.tour_scan_body),
        TourPage(R.drawable.tour_edit, R.string.tour_edit_title, R.string.tour_edit_body, editNote),
        TourPage(R.drawable.tour_arrange, R.string.tour_arrange_title, R.string.tour_arrange_body),
    )
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == pages.lastIndex

    BackHandler(onBack = onFinished)

    // Surface sets the content colour for the theme; drawn on its own, text would default to black.
    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
            QuireTextButton(onClick = onFinished) { Text(stringResource(R.string.tour_skip)) }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            val page = pages[index]
            Column(
                Modifier.fillMaxSize().padding(horizontal = Spacing.screenHorizontal),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Image(painterResource(page.image), contentDescription = null, modifier = Modifier.size(220.dp))
                Spacer(Modifier.height(32.dp))
                Text(
                    stringResource(page.title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(page.body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (page.note != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(page.note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp).semantics {
                contentDescription = "${pagerState.currentPage + 1} / ${pages.size}"
            },
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            repeat(pages.size) { i ->
                val active = i == pagerState.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (active) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = 12.dp)) {
            QuireButton(
                onClick = {
                    if (last) onFinished() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(if (last) R.string.tour_start else R.string.tour_next)) }
        }
        Spacer(Modifier.width(0.dp))
    }
    }
}
