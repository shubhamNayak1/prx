package com.baseras.fieldpharma.data.repo

import com.baseras.fieldpharma.data.local.PendingSyncDao
import com.baseras.fieldpharma.data.local.PendingSyncEntity
import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.ExpenseDto
import com.baseras.fieldpharma.data.remote.ExpensePolicyDto
import com.baseras.fieldpharma.data.remote.ExpenseReq
import com.baseras.fieldpharma.data.remote.UploadHelper
import com.baseras.fieldpharma.sync.SyncTrigger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ExpenseRepository(
    private val api: Api,
    private val pendingDao: PendingSyncDao,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun list(): Result<List<ExpenseDto>> = runCatching {
        api.listExpenses().expenses
    }

    suspend fun myPolicy(): ExpensePolicyDto? = runCatching {
        api.myExpensePolicy().policy
    }.getOrNull()

    /** Submits an expense. If offline or upload fails, queues for sync. */
    suspend fun submit(req: ExpenseReq, billFile: File?) {
        try {
            val photoUrl = billFile?.let { UploadHelper.uploadImage(api, it) }
            val finalReq = if (photoUrl != null) req.copy(billPhoto = photoUrl) else req
            api.createExpense(finalReq)
            billFile?.delete()
        } catch (t: Throwable) {
            pendingDao.add(
                PendingSyncEntity(
                    type = "EXPENSE",
                    payloadJson = json.encodeToString(req),
                    localPhotoPath = billFile?.absolutePath,
                )
            )
            SyncTrigger.fire()
        }
    }

    /** Drains queued expenses. Returns true if all sent. Called by SyncWorker. */
    suspend fun syncPending(): Boolean {
        val items = pendingDao.next().filter { it.type == "EXPENSE" }
        var allOk = true
        for (item in items) {
            try {
                var req = json.decodeFromString<ExpenseReq>(item.payloadJson)
                if (item.localPhotoPath != null && req.billPhoto == null) {
                    val f = File(item.localPhotoPath)
                    if (f.exists()) {
                        val url = UploadHelper.uploadImage(api, f)
                        req = req.copy(billPhoto = url)
                        f.delete()
                    }
                }
                api.createExpense(req)
                pendingDao.delete(item.id)
            } catch (t: Throwable) {
                pendingDao.bumpAttempts(item.id)
                allOk = false
            }
        }
        return allOk
    }
}
