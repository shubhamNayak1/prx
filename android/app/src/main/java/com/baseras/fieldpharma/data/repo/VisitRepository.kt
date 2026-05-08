package com.baseras.fieldpharma.data.repo

import com.baseras.fieldpharma.data.local.VisitDao
import com.baseras.fieldpharma.data.local.VisitEntity
import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.VisitReq
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class VisitRepository(
    private val api: Api,
    private val dao: VisitDao,
) {
    fun observeRecent(): Flow<List<VisitEntity>> = dao.observeRecent()
    suspend fun active(): VisitEntity? = dao.activeVisit()
    suspend fun byId(id: Long): VisitEntity? = dao.get(id)

    suspend fun checkIn(clientId: String, lat: Double?, lng: Double?): Long {
        val v = VisitEntity(
            clientId = clientId,
            checkInAt = Instant.now().toString(),
            checkInLat = lat,
            checkInLng = lng,
            state = "IN_PROGRESS",
        )
        return dao.insert(v)
    }

    suspend fun saveNotes(localId: Long, notes: String, products: List<String>) {
        val v = dao.get(localId) ?: return
        dao.update(v.copy(
            notes = notes,
            productsJson = if (products.isEmpty()) null else products.joinToString("|"),
        ))
    }

    /** Mark visit completed locally, then try to push to server. */
    suspend fun checkOut(localId: Long, lat: Double?, lng: Double?) {
        val v = dao.get(localId) ?: return
        val updated = v.copy(
            checkOutAt = Instant.now().toString(),
            checkOutLat = lat,
            checkOutLng = lng,
            state = "COMPLETED",
        )
        dao.update(updated)
        trySync(updated)
    }

    /** Returns true if all unsynced visits succeeded. */
    suspend fun syncPending(): Boolean {
        var allOk = true
        for (v in dao.unsynced()) {
            if (!trySync(v)) allOk = false
        }
        return allOk
    }

    private suspend fun trySync(v: VisitEntity): Boolean {
        return try {
            val res = api.saveVisit(VisitReq(
                clientId = v.clientId,
                checkInAt = v.checkInAt,
                checkInLat = v.checkInLat,
                checkInLng = v.checkInLng,
                checkOutAt = v.checkOutAt,
                checkOutLat = v.checkOutLat,
                checkOutLng = v.checkOutLng,
                productsDiscussed = v.productsJson?.split("|"),
                notes = v.notes,
            ))
            dao.markSynced(v.localId, res.visit.id)
            true
        } catch (t: Throwable) {
            false
        }
    }
}
