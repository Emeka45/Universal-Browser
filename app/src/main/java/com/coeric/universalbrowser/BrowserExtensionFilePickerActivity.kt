package com.coeric.universalbrowser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import org.mozilla.geckoview.WebExtensionController

/** Small transparent hand-off activity used to choose a local signed XPI without involving the main tab UI. */
class BrowserExtensionFilePickerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BrowserExtensionCenter.attach(this)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/x-xpinstall"
        }
        try {
            startActivityForResult(intent, REQUEST_XPI)
        } catch (_: Throwable) {
            Toast.makeText(this, "No file picker is available", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("Android still delivers this callback for the compatibility file-picker flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_XPI || resultCode != RESULT_OK) {
            finish()
            return
        }
        val uri = data?.data
        if (uri == null) {
            finish()
            return
        }
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Throwable) { }

        val controller = (application as UniversalBrowserApp).getRuntime().webExtensionController
        controller.install(uri.toString(), WebExtensionController.INSTALLATION_METHOD_FROM_FILE).accept({ extension ->
            runOnUiThread {
                Toast.makeText(this, "Installed ${extension?.metaData?.name ?: extension?.id ?: "extension"}", Toast.LENGTH_LONG).show()
                finish()
            }
        }, { error ->
            runOnUiThread {
                Toast.makeText(this, "Extension install failed: ${error?.message ?: "unsupported or invalid XPI"}", Toast.LENGTH_LONG).show()
                finish()
            }
        })
    }

    companion object { private const val REQUEST_XPI = 4208 }
}
