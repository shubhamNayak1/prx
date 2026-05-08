package com.baseras.fieldpharma.data.repo

import com.baseras.fieldpharma.data.local.PendingSyncDao
import com.baseras.fieldpharma.data.local.PendingSyncEntity
import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.SampleBalanceDto
import com.baseras.fieldpharma.data.remote.SampleDistReq
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SampleRepository(
    private val api: Api,
    private val pendingDao: PendingSyncDao,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun balance(): Result<List<SampleBalanceDto>> = runCatching {
        api.sampleBalance().balance
    }

    /** Distribute sample. Foreground attempt; queues if offline. */
    suspend fun distribute(issueId: String, quantity: Int, visitId: String? = null): Result<Unit> {
        val req = SampleDistReq(sampleIssueId = issueId, quantity = quantity, visitId = visitId)
        return try {
            api.distributeSample(req)
            Result.success(Unit)
        } catch (t: Throwable) {
            pendingDao.add(
                PendingSyncEntity(
                    type = "SAMPLE_DIST",
                    payloadJson = json.encodeToString(req),
                )
            )
            Result.success(Unit) // queued
        }
    }

    suspend fun syncPending(): Boolean {
        val items = pendingDao.next().filter { it.type == "SAMPLE_DIST" }
        var allOk = true
        for (item in items) {
            try {
                api.distributeSample(json.decodeFromString(item.payloadJson))
                pendingDao.delete(item.id)
            } catch (t: Throwable) {
                pendingDao.bumpAttempts(item.id)
                allOk = false
            }
        }
        return allOk
    }
}
