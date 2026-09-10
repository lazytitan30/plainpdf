package com.leaf.app.data.billing

import android.content.Context
import com.leaf.app.R
import com.leaf.app.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope

fun createSupporterRepository(
    context: Context,
    @Suppress("UNUSED_PARAMETER") settings: SettingsRepository,
    @Suppress("UNUSED_PARAMETER") scope: CoroutineScope,
): SupporterRepository = AlwaysUnlockedSupporterRepository(context.getString(R.string.foss_donate_url))
