package com.leaf.app.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.leaf.app.MainActivity
import com.leaf.app.data.prefs.AppIcon

/**
 * Alternate launcher icons are activity-aliases in the manifest; exactly one is enabled.
 * The real MainActivity keeps the VIEW and SEND filters and is never disabled.
 */
class AppIconSwitcher(private val context: Context) {

    // The package of the component is the application id; the class name of an alias is
    // the manifest namespace plus ".launcher.X". The two differ (com.plainpdf.app versus
    // com.leaf.app), and building the class from the application id crashed on every phone.
    private fun alias(icon: AppIcon): ComponentName = ComponentName(context, aliasClassName(icon))

    companion object {
        /** Fully qualified class name of the alias for [icon], next to MainActivity in the manifest. */
        fun aliasClassName(icon: AppIcon): String {
            val namespace = MainActivity::class.java.name.substringBeforeLast('.')
            return "$namespace.launcher." + when (icon) {
                AppIcon.DEFAULT -> "Default"
                AppIcon.MONO -> "Mono"
                AppIcon.PAPER -> "Paper"
                AppIcon.NIGHT -> "Night"
            }
        }
    }

    fun apply(icon: AppIcon) {
        val pm = context.packageManager
        // Enable the new alias first so a launcher entry always exists.
        pm.setComponentEnabledSetting(alias(icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        AppIcon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(alias(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
