package com.baseras.fieldpharma.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Fires a one-shot SyncWorker the moment the device has network. Used by
 * repositories right after queuing an offline operation, so the user doesn't
 * have to wait for the 15-min periodic worker.
 *
 * Behavior:
 * - Online → worker runs immediately (~real-time)
 * - Offline → worker waits in WorkManager's queue, runs the instant network is back
 * - Already-pending sync → KEEP policy ignores duplicate enqueues
 */
object SyncTrigger {
    private const val WORK_NAME = "fieldpharma-sync-now"

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun fire() {
        val ctx = appContext ?: return
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            req,
        )
    }
}
