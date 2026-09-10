package com.leaf.app.util.ocr

import android.content.Context
import kotlinx.coroutines.CoroutineScope

fun createLanguagePackSource(
    context: Context,
    tessdata: TessdataStore,
    scope: CoroutineScope,
    onInstalled: (String) -> Unit,
): LanguagePackSource = PlayLanguagePackSource(context, tessdata, scope, onInstalled)
