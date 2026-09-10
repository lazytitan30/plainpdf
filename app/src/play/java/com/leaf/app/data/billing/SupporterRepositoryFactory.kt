package com.leaf.app.data.billing

import android.content.Context
import com.leaf.app.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope

fun createSupporterRepository(
    context: Context,
    settings: SettingsRepository,
    scope: CoroutineScope,
): SupporterRepository = PlayBillingSupporterRepository(context, settings, scope)
