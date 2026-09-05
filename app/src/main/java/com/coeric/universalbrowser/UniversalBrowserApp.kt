package com.coeric.universalbrowser

import android.app.Application
import org.mozilla.geckoview.GeckoRuntime

class UniversalBrowserApp : Application() {
    lateinit var runtime: GeckoRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        runtime = GeckoRuntime.create(this)
    }
}
