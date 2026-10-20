package com.baseras.fieldpharma.data.repo

import com.baseras.fieldpharma.auth.AuthStore
import com.baseras.fieldpharma.auth.AuthUser
import com.baseras.fieldpharma.billing.BillingStore
import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.LoginReq

class AuthRepository(
    private val api: Api,
    private val authStore: AuthStore,
    private val billingStore: BillingStore,
) {
    suspend fun login(email: String, password: String): Result<AuthUser> = runCatching {
        val res = api.login(LoginReq(email, password))
        authStore.token = res.token
        val u = AuthUser(res.user.id, res.user.email, res.user.name, res.user.role)
        authStore.user = u
        // Clear any previous tenant's cached billing snapshot — the first
        // post-login refresh will populate the real one.
        billingStore.clear()
        u
    }

    fun logout() {
        authStore.clear()
        billingStore.clear()
    }

    val current: AuthUser? get() = authStore.user
    val isAuthenticated: Boolean get() = authStore.isAuthenticated
}
