package com.baseras.fieldpharma.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.baseras.fieldpharma.FieldPharmaApp
import java.util.concurrent.TimeUnit

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FieldPharmaApp
        if (!app.authRepo.isAuthenticated) return Result.success()

        runCatching { app.clientRepo.refresh() }
        runCatching { app.edetailRepo.refresh() }

        val attendanceOk = app.attendanceRepo.syncPending()
        val visitsOk = app.visitRepo.syncPending()
        val expenseOk = app.expenseRepo.syncPending()
        val sampleOk = app.sampleRepo.syncPending()
        val edetailOk = app.edetailRepo.syncPending()
        val rcpaOk = app.rcpaRepo.syncPending()

        return if (attendanceOk && visitsOk && expenseOk && sampleOk && edetailOk && rcpaOk)
            Result.success() else Result.retry()
    }
}

fun scheduleSync(context: Context) {
    val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "fieldpharma-sync",
        ExistingPeriodicWorkPolicy.KEEP,
        req,
    )
}
