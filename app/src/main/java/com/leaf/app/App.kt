package com.leaf.app

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process
import android.os.StrictMode
import com.leaf.app.di.AppContainer
import com.leaf.app.util.CrashReports

class App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // The PDF library renders in an isolated helper process that has no access to our
        // files or database; nothing of ours runs there, so skip the app set-up entirely.
        if (Process.isIsolated()) return
        CrashReports.install(this)
        if (isDebuggable) enableStrictMode()
        container = AppContainer(this)
        container.startBackgroundMaintenance()
    }

    private val isDebuggable: Boolean
        get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build(),
        )
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as App).container
