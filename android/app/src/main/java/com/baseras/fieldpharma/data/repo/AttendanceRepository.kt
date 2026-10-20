package com.baseras.fieldpharma.data.repo

import com.baseras.fieldpharma.data.local.AttendanceDao
import com.baseras.fieldpharma.data.local.AttendanceEntity
import com.baseras.fieldpharma.data.local.PendingSyncDao
import com.baseras.fieldpharma.data.local.PendingSyncEntity
import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.PunchReq
import com.baseras.fieldpharma.data.remote.UploadHelper
import com.baseras.fieldpharma.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class AttendanceRepository(
    private val api: Api,
    private val attendanceDao: AttendanceDao,
    private val pendingDao: PendingSyncDao,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** All of today's sessions, oldest first. */
    fun observeTodaySessions(): Flow<List<AttendanceEntity>> =
        attendanceDao.observeForDate(today())

    fun observePendingCount(): Flow<Int> = pendingDao.observeCount()

    /** Starts a new session unless one is already open. */
    suspend fun punchIn(lat: Double?, lng: Double?, photoFile: File?) {
        val date = today()
        val nowIso = Instant.now().toString()

        if (attendanceDao.openSession(date) != null) return // already punched in

        val row = AttendanceEntity(
            date = date,
            punchInAt = nowIso,
            punchInLat = lat,
            punchInLng = lng,
            punchInPhotoPath = photoFile?.absolutePath,
            syncedAt = null,
        )
        val localId = attendanceDao.insert(row)

        try {
            val photoUrl = photoFile?.let { UploadHelper.uploadImage(api, it) }
            api.punchIn(PunchReq(date, nowIso, lat, lng, photoUrl))
            attendanceDao.update(row.copy(
                localId = localId,
                punchInPhotoUrl = photoUrl,
                punchInPhotoPath = null,
                syncedAt = System.currentTimeMillis(),
            ))
            photoFile?.delete()
        } catch (t: Throwable) {
            pendingDao.add(PendingSyncEntity(
                type = "PUNCH_IN",
                payloadJson = json.encodeToString(PunchReq(date, nowIso, lat, lng, null)),
                localPhotoPath = photoFile?.absolutePath,
            ))
            SyncTrigger.fire()
        }
    }

    /** Closes the latest open session. No-op if nothing is open. */
    suspend fun punchOut(lat: Double?, lng: Double?) {
        val date = today()
        val nowIso = Instant.now().toString()
        val open = attendanceDao.openSession(date) ?: return

        val closed = open.copy(
            punchOutAt = nowIso,
            punchOutLat = lat,
            punchOutLng = lng,
            syncedAt = null,
        )
        attendanceDao.update(closed)

        try {
            api.punchOut(PunchReq(date, nowIso, lat, lng))
            attendanceDao.update(closed.copy(syncedAt = System.currentTimeMillis()))
        } catch (t: Throwable) {
            pendingDao.add(PendingSyncEntity(
                type = "PUNCH_OUT",
                payloadJson = json.encodeToString(PunchReq(date, nowIso, lat, lng)),
            ))
            SyncTrigger.fire()
        }
    }

    /** Drain queued punches. Returns true if all sent. */
    suspend fun syncPending(): Boolean {
        val items = pendingDao.next()
        var allOk = true
        for (item in items) {
            try {
                var req = json.decodeFromString<PunchReq>(item.payloadJson)

                // Upload pending photo first, if any
                if (item.localPhotoPath != null && req.photo == null) {
                    val f = File(item.localPhotoPath)
                    if (f.exists()) {
                        val url = UploadHelper.uploadImage(api, f)
                        req = req.copy(photo = url)
                        f.delete()
                    }
                }

                when (item.type) {
                    "PUNCH_IN" -> api.punchIn(req)
                    "PUNCH_OUT" -> api.punchOut(req)
                    else -> {} // visits handled separately
                }
                pendingDao.delete(item.id)
            } catch (t: Throwable) {
                pendingDao.bumpAttempts(item.id)
                allOk = false
            }
        }
        return allOk
    }

    private fun today(): String =
        LocalDate.now(ZoneOffset.systemDefault()).toString()
}
