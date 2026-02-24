package com.metaai.studio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.DownloadManager
import android.widget.Toast

class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id != -1L) {
                Toast.makeText(
                    context,
                    "✅ Download complete! Saved to Downloads/MetaAI",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
