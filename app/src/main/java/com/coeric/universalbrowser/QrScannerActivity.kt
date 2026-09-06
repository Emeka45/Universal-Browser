package com.coeric.universalbrowser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult

/** Standalone low-friction QR scanner. Results can be opened directly in Universal Browser. */
class QrScannerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startScan()
    }

    private fun startScan() {
        IntentIntegrator(this)
            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setPrompt("Point the camera at a QR code")
            .setOrientationLocked(false)
            .setBeepEnabled(false)
            .setBarcodeImageEnabled(false)
            .initiateScan()
    }

    @Deprecated("ZXing's integration API uses Activity result callbacks on API 26+.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result: IntentResult? = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            val value = result.contents?.trim()
            if (value.isNullOrBlank()) {
                Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            val intent = Intent(this, MainActivity::class.java)
            intent.action = Intent.ACTION_VIEW
            intent.data = android.net.Uri.parse(value)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
