package com.baseras.fieldpharma.data.repo

import com.baseras.fieldpharma.data.local.AttendanceDao
import com.baseras.fieldpharma.data.local.AttendanceEntity
import com.baseras.fieldpharma.data.local.PendingSyncDao
import com.baseras.fieldpharma.data.local.PendingSyncEntity
import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.PunchReq
import com.baseras.fieldpharma.data.remote.UploadHelper
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

    fun observeToday(): Flow<AttendanceEntity?> = attendanceDao.observe(today())
    fun observePendingCount(): Flow<Int> = pendingDao.observeCount()

    suspend fun punchIn(lat: Double?, lng: Double?, photoFile: File?) {
        val date = today()
        val nowIso = Instant.now().toString()
        val current = attendanceDao.get(date)

        // 1. Save locally first (offline-first)
        val updated = (current ?: AttendanceEntity(date)).copy(
            punchInAt = nowIso,
            punchInLat = lat,
            punchInLng = lng,
            punchInPhotoPath = photoFile?.absolutePath,
            syncedAt = null,
        )
        attendanceDao.upsert(updated)

        // 2. Try remote: upload photo if any, then POST
        try {
            val photoUrl = photoFile?.let { UploadHelper.uploadImage(api, it) }
            api.punchIn(PunchReq(date, nowIso, lat, lng, photoUrl))
            attendanceDao.upsert(updated.copy(
                punchInPhotoUrl = photoUrl,
                punchInPhotoPath = null,
                syncedAt = System.currentTimeMillis(),
            ))
            photoFile?.delete()
        } catch (t: Throwable) {
            // Queue for sync
            pendingDao.add(
                PendingSyncEntity(
                    type = "PUNCH_IN",
                    payloadJson = json.encodeToString(PunchReq(date, nowIso, lat, lng, null)),
                    localPhotoPath = photoFile?.absolutePath,
                )
            )
        }
    }

    suspend fun punchOut(lat: Double?, lng: Double?) {
        val date = today()
        val nowIso = Instant.now().toString()
        val current = attendanceDao.get(date)
        val updated = (current ?: AttendanceEntity(date)).copy(
            punchOutAt = nowIso,
            punchOutLat = lat,
            punchOutLng = lng,
            syncedAt = null,
        )
        attendanceDao.upsert(updated)
        try {
            api.punchOut(PunchReq(date, nowIso, lat, lng))
            attendanceDao.upsert(updated.copy(syncedAt = System.currentTimeMillis()))
        } catch (t: Throwable) {
            pendingDao.add(
                PendingSyncEntity(
                    type = "PUNCH_OUT",
                    payloadJson = json.encodeToString(PunchReq(date, nowIso, lat, lng)),
                )
            )
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
