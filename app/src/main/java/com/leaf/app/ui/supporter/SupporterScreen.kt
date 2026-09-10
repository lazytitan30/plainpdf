package com.leaf.app.ui.supporter

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leaf.app.R
import com.leaf.app.data.billing.BillingProblem
import com.leaf.app.data.billing.RestoreResult
import com.leaf.app.data.billing.SupportEntry
import com.leaf.app.data.billing.SupporterProducts
import com.leaf.app.ui.common.LocalAppContainer
import com.leaf.app.ui.common.QuireButton
import com.leaf.app.ui.common.QuireOutlinedButton
import com.leaf.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Honest copy, no dark patterns. Reached only from Settings and About.
 * In the foss flavour the same screen shows the donate link instead of products.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupporterScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val supporter = container.supporter
    val isSupporter by supporter.isSupporter.collectAsStateWithLifecycle()
    val standing by supporter.standing.collectAsStateWithLifecycle()
    val products by supporter.products.collectAsStateWithLifecycle()
    val problem by supporter.problem.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { supporter.refresh() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val restoredMsg = stringResource(R.string.supporter_restore_found)
    val noneMsg = stringResource(R.string.supporter_restore_none)
    val unavailableMsg = stringResource(R.string.supporter_unavailable)
    val giftThanks = stringResource(R.string.supporter_snack_gift)
    val unlockThanks = stringResource(R.string.supporter_snack_unlock)

    // Say thank you the moment a purchase completes. The text below changes as well, but a
    // repeat gift from a Supporter changes nothing there and still deserves an answer.
    LaunchedEffect(Unit) {
        supporter.thanks.collect { id ->
            snackbar.showSnackbar(if (id == SupporterProducts.UNLOCK) unlockThanks else giftThanks)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.supporter_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.screenHorizontal)
                .verticalScroll(rememberScrollState()),
        ) {
            when (val entry = supporter.supportEntry) {
                is SupportEntry.DonateLink -> {
                    Text(stringResource(R.string.supporter_donate_intro), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(Spacing.section))
                    QuireButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.url))) }) {
                        Text(stringResource(R.string.supporter_donate_button))
                    }
                }
                SupportEntry.InApp -> {
                    Text(stringResource(R.string.supporter_intro), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(Spacing.section))
                    Text(stringResource(R.string.supporter_grants_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        R.string.supporter_grant_accents,
                        R.string.supporter_grant_icons,
                        R.string.supporter_grant_highlights,
                        R.string.supporter_grant_about,
                        R.string.supporter_grant_future,
                    ).forEach { res ->
                        Text(
                            stringResource(res),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(Spacing.section))

                    when {
                        standing.unlockOwned -> Text(stringResource(R.string.supporter_thanks), style = MaterialTheme.typography.titleMedium)
                        standing.tipped -> GiftThanks()
                        else -> {
                            val unlock = products.firstOrNull { it.id == SupporterProducts.UNLOCK }
                            QuireButton(
                                onClick = { (context as? Activity)?.let { supporter.purchase(it, SupporterProducts.UNLOCK) } },
                                enabled = unlock != null,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    if (unlock != null) "${stringResource(R.string.supporter_unlock)}  ${unlock.formattedPrice}"
                                    else stringResource(R.string.supporter_unlock),
                                )
                            }
                            if (unlock == null) {
                                // Say why the button is off instead of leaving it greyed and silent.
                                Text(
                                    when (problem) {
                                        BillingProblem.NOT_CONNECTED -> stringResource(R.string.supporter_problem_connection)
                                        BillingProblem.NOT_LISTED -> stringResource(R.string.supporter_problem_listing)
                                        BillingProblem.NOT_IN_REGION -> stringResource(R.string.supporter_problem_region)
                                        null -> unavailableMsg
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }

                    val tips = products.filter { it.consumable }
                    if (tips.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.section))
                        Text(
                            stringResource(if (isSupporter) R.string.supporter_tips_title else R.string.supporter_tips_title_first),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(if (isSupporter) R.string.supporter_tips_summary else R.string.supporter_tips_summary_first),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        tips.forEach { tip ->
                            QuireOutlinedButton(
                                onClick = { (context as? Activity)?.let { supporter.purchase(it, tip.id) } },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            ) {
                                Text("${tip.title}  ${tip.formattedPrice}")
                            }
                            if (!isSupporter && tip.id == SupporterProducts.TIP_GRANTS_PACK) {
                                Text(
                                    stringResource(R.string.supporter_tip_unlocks_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(Spacing.section))
                    QuireOutlinedButton(
                        onClick = {
                            scope.launch {
                                val message = when (supporter.restorePurchases()) {
                                    RestoreResult.RESTORED -> restoredMsg
                                    RestoreResult.NOTHING_FOUND -> noneMsg
                                    RestoreResult.UNAVAILABLE -> unavailableMsg
                                }
                                snackbar.showSnackbar(message)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.supporter_restore))
                    }
                }
            }
            Spacer(Modifier.height(Spacing.section))
        }
    }
}

/** Shown in place of the unlock button once the big tip has been given: the pack came with it. */
@Composable
private fun GiftThanks() {
    Text(stringResource(R.string.supporter_gift_title), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.supporter_gift_body), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.supporter_gift_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
