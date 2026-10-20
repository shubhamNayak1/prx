package com.baseras.fieldpharma.data.repo

import com.baseras.fieldpharma.auth.AuthStore
import com.baseras.fieldpharma.auth.AuthUser
import com.baseras.fieldpharma.billing.BillingStore
import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.AuthOtpReq
import com.baseras.fieldpharma.data.remote.AuthOtpRes
import com.baseras.fieldpharma.data.remote.AuthOtpVerifyReq
import com.baseras.fieldpharma.data.remote.AuthOtpVerifyRes
import com.baseras.fieldpharma.data.remote.LoginReq
import com.baseras.fieldpharma.data.remote.LoginRes
import com.baseras.fieldpharma.data.remote.PinLoginReq
import com.baseras.fieldpharma.data.remote.SetPinReq
import com.baseras.fieldpharma.data.remote.SignupCompleteReq

class AuthRepository(
    private val api: Api,
    private val authStore: AuthStore,
    private val billingStore: BillingStore,
) {
    /** Legacy email + password sign-in (kept for admins migrating from the old flow). */
    suspend fun login(email: String, password: String): Result<AuthUser> = runCatching {
        val res = api.login(LoginReq(email, password))
        persist(res)
    }

    /** Phone + 4-digit PIN. Primary login after the first OTP setup. */
    suspend fun pinLogin(phone: String, pin: String): Result<AuthUser> = runCatching {
        val res = api.pinLogin(PinLoginReq(phone.trim(), pin))
        persist(res)
    }

    /** Sends a WhatsApp OTP; works for login + reset. Backend does not reveal whether the user exists here. */
    suspend fun requestOtp(phone: String, purpose: String = "LOGIN"): Result<AuthOtpRes> =
        runCatching { api.requestAuthOtp(AuthOtpReq(phone.trim(), purpose)) }

    /** Verifies the OTP and returns a setup-token the caller uses to set-pin or complete-signup. */
    suspend fun verifyOtp(phone: String, otp: String, purpose: String = "LOGIN"): Result<AuthOtpVerifyRes> =
        runCatching { api.verifyAuthOtp(AuthOtpVerifyReq(phone.trim(), otp, purpose)) }

    /** Sets the user's PIN (requires setup token from verifyOtp) and signs them in. */
    suspend fun setPin(setupToken: String, pin: String, confirmPin: String): Result<AuthUser> = runCatching {
        val res = api.setPin(SetPinReq(setupToken, pin, confirmPin))
        persist(res)
    }

    /** For brand-new phones: creates company + admin + sets PIN atomically. */
    suspend fun signupComplete(
        setupToken: String,
        companyName: String,
        gstin: String?,
        adminName: String,
        adminEmail: String,
        pin: String,
    ): Result<AuthUser> = runCatching {
        val res = api.signupComplete(SignupCompleteReq(
            setupToken = setupToken,
            companyName = companyName.trim(),
            gstin = gstin?.trim()?.ifBlank { null },
            adminName = adminName.trim(),
            adminEmail = adminEmail.trim().lowercase(),
            pin = pin,
        ))
        persist(res)
    }

    fun logout() {
        authStore.clear()
        billingStore.clear()
    }

    val current: AuthUser? get() = authStore.user
    val isAuthenticated: Boolean get() = authStore.isAuthenticated

    private fun persist(res: LoginRes): AuthUser {
        authStore.token = res.token
        val u = AuthUser(res.user.id, res.user.email, res.user.name, res.user.role)
        authStore.user = u
        billingStore.clear()
        return u
    }
}
