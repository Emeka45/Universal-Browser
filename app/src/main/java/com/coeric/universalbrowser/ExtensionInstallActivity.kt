package com.coeric.universalbrowser

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class ExtensionInstallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) BrowserExtensionCenter.openInstaller(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (BrowserExtensionCenter.handleResult(this, requestCode, resultCode, data, (application as UniversalBrowserApp).getRuntime())) {
            finish()
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
