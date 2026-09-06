package com.coeric.universalbrowser

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Background download service foundation; task execution is coordinated by DownloadTaskStore. */
class DownloadService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelfResult(startId)
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
