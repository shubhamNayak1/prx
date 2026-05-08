package com.baseras.fieldpharma.data.repo

import com.baseras.fieldpharma.auth.AuthStore
import com.baseras.fieldpharma.auth.AuthUser
import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.LoginReq

class AuthRepository(
    private val api: Api,
    private val authStore: AuthStore,
) {
    suspend fun login(email: String, password: String): Result<AuthUser> = runCatching {
        val res = api.login(LoginReq(email, password))
        if (res.user.role != "MR") {
            error("Only MR accounts can sign in here. Use admin panel for managers.")
        }
        authStore.token = res.token
        val u = AuthUser(res.user.id, res.user.email, res.user.name, res.user.role)
        authStore.user = u
        u
    }

    fun logout() {
        authStore.clear()
    }

    val current: AuthUser? get() = authStore.user
    val isAuthenticated: Boolean get() = authStore.isAuthenticated
}
