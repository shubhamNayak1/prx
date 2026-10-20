package com.baseras.fieldpharma.data.repo

import com.baseras.fieldpharma.billing.BillingSnapshot
import com.baseras.fieldpharma.billing.BillingStatus
import com.baseras.fieldpharma.billing.BillingStore
import com.baseras.fieldpharma.data.remote.Api

class BillingRepository(
    private val api: Api,
    private val store: BillingStore,
) {
    /**
     * Fetches the tenant's subscription status and updates the local snapshot.
     * On network error, keeps the previous cached snapshot — so a transient
     * outage doesn't lock out an ACTIVE tenant.
     */
    suspend fun refresh(): Result<BillingSnapshot> = runCatching {
        val res = api.billingStatus()
        val status = runCatching { BillingStatus.valueOf(res.subscription.status) }
            .getOrElse { BillingStatus.UNKNOWN }
        val snap = BillingSnapshot(
            status = status,
            cycle = res.subscription.cycle,
            trialEndsAt = res.subscription.trialEndsAt,
            fetchedAt = System.currentTimeMillis(),
        )
        store.set(snap)
        snap
    }

    val snapshot: BillingSnapshot get() = store.state.value
}
