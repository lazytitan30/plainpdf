package com.leaf.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import com.leaf.app.MainActivity
import com.leaf.app.R
import com.leaf.app.ui.OpenRequests

/**
 * A one-row home-screen widget: Scan, Open PDF, Library. Plain RemoteViews, no extra library.
 * At four cells or more it shows icons with words; squeezed to two or three cells it keeps
 * only the icons, so it still fits next to other widgets.
 */
class QuickActionsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, views(context, manager.getAppWidgetOptions(id))) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        manager.updateAppWidget(id, views(context, options))
    }

    private fun views(context: Context, options: Bundle): RemoteViews {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The launcher picks the closest size itself as the widget is resized.
            return RemoteViews(
                mapOf(
                    SizeF(110f, 40f) to build(context, compact = true),
                    SizeF(FULL_WIDTH_DP.toFloat(), 40f) to build(context, compact = false),
                ),
            )
        }
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, FULL_WIDTH_DP)
        return build(context, compact = width < FULL_WIDTH_DP)
    }

    private fun build(context: Context, compact: Boolean): RemoteViews =
        RemoteViews(context.packageName, if (compact) R.layout.widget_quick_actions_compact else R.layout.widget_quick_actions).apply {
            setOnClickPendingIntent(R.id.widget_scan, launch(context, OpenRequests.ACTION_SCAN, 1))
            setOnClickPendingIntent(R.id.widget_open, launch(context, OpenRequests.ACTION_OPEN, 2))
            setOnClickPendingIntent(R.id.widget_library, launch(context, Intent.ACTION_MAIN, 3))
        }

    private fun launch(context: Context, action: String, code: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private companion object {
        /** Below this the words no longer fit three buttons; icons only. */
        const val FULL_WIDTH_DP = 250
    }
}
