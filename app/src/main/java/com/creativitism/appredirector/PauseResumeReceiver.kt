package com.creativitism.appredirector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * "Resume now" from the service notification: ends every pause early. The
 * service's next tick sees the cleared pause and re-arms blocking and grayscale.
 */
class PauseResumeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PauseStore(context).clearAll()
    }
}
