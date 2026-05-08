package com.baseras.fieldpharma.data.repo

import com.baseras.fieldpharma.data.local.PendingSyncDao
import com.baseras.fieldpharma.data.local.PendingSyncEntity
import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.RcpaDto
import com.baseras.fieldpharma.data.remote.RcpaReq
import com.baseras.fieldpharma.sync.SyncTrigger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RcpaRepository(
    private val api: Api,
    private val pendingDao: PendingSyncDao,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun list(): Result<List<RcpaDto>> = runCatching { api.listRcpa().entries }

    suspend fun submit(req: RcpaReq) {
        try {
            api.createRcpa(req)
        } catch (t: Throwable) {
            pendingDao.add(
                PendingSyncEntity(
                    type = "RCPA",
                    payloadJson = json.encodeToString(req),
                )
            )
            SyncTrigger.fire()
        }
    }

    suspend fun syncPending(): Boolean {
        val items = pendingDao.next().filter { it.type == "RCPA" }
        var allOk = true
        for (item in items) {
            try {
                api.createRcpa(json.decodeFromString(item.payloadJson))
                pendingDao.delete(item.id)
            } catch (t: Throwable) {
                pendingDao.bumpAttempts(item.id)
                allOk = false
            }
        }
        return allOk
    }
}
