package com.baseras.fieldpharma.billing

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory + prefs-backed snapshot of the tenant's subscription state.
 * The server is authoritative; this cache keeps the UI stable between refreshes
 * and across app restarts so a locked tenant stays locked while offline.
 */
class BillingStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("fp_billing", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<BillingSnapshot> = _state

    fun set(snapshot: BillingSnapshot) {
        prefs.edit()
            .putString(KEY_STATUS, snapshot.status.name)
            .putString(KEY_CYCLE, snapshot.cycle)
            .putString(KEY_TRIAL_ENDS, snapshot.trialEndsAt)
            .putLong(KEY_FETCHED_AT, snapshot.fetchedAt)
            .apply()
        _state.value = snapshot
    }

    fun clear() {
        prefs.edit().clear().apply()
        _state.value = BillingSnapshot.UNKNOWN
    }

    private fun load(): BillingSnapshot {
        val raw = prefs.getString(KEY_STATUS, null) ?: return BillingSnapshot.UNKNOWN
        val status = runCatching { BillingStatus.valueOf(raw) }.getOrElse { BillingStatus.UNKNOWN }
        return BillingSnapshot(
            status = status,
            cycle = prefs.getString(KEY_CYCLE, null),
            trialEndsAt = prefs.getString(KEY_TRIAL_ENDS, null),
            fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0L),
        )
    }

    private companion object {
        const val KEY_STATUS = "status"
        const val KEY_CYCLE = "cycle"
        const val KEY_TRIAL_ENDS = "trial_ends"
        const val KEY_FETCHED_AT = "fetched_at"
    }
}

enum class BillingStatus { UNKNOWN, TRIAL, ACTIVE, PAST_DUE, LOCKED, CANCELLED }

data class BillingSnapshot(
    val status: BillingStatus,
    val cycle: String?,
    val trialEndsAt: String?,
    val fetchedAt: Long,
) {
    val isLocked: Boolean get() = status == BillingStatus.LOCKED || status == BillingStatus.CANCELLED
    val isTrial: Boolean get() = status == BillingStatus.TRIAL

    /** Days remaining in trial (floor). null outside trial or when the date is unparseable. */
    val trialDaysLeft: Int?
        get() {
            if (!isTrial || trialEndsAt == null) return null
            val ends = runCatching { java.time.Instant.parse(trialEndsAt).toEpochMilli() }
                .getOrElse { return null }
            val diff = ends - System.currentTimeMillis()
            if (diff <= 0) return 0
            return (diff / 86_400_000L).toInt()
        }

    companion object {
        val UNKNOWN = BillingSnapshot(BillingStatus.UNKNOWN, null, null, 0L)
    }
}
