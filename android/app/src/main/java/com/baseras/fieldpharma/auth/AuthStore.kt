package com.baseras.fieldpharma.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class AuthStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "fp_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AuthEvent> = _events

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
            }.apply()
        }

    var user: AuthUser?
        get() {
            val id = prefs.getString(KEY_USER_ID, null) ?: return null
            return AuthUser(
                id = id,
                email = prefs.getString(KEY_EMAIL, "")!!,
                name = prefs.getString(KEY_NAME, "")!!,
                role = prefs.getString(KEY_ROLE, "MR")!!,
            )
        }
        set(value) {
            val ed = prefs.edit()
            if (value == null) {
                ed.remove(KEY_USER_ID).remove(KEY_EMAIL).remove(KEY_NAME).remove(KEY_ROLE)
            } else {
                ed.putString(KEY_USER_ID, value.id)
                    .putString(KEY_EMAIL, value.email)
                    .putString(KEY_NAME, value.name)
                    .putString(KEY_ROLE, value.role)
            }
            ed.apply()
        }

    fun clear() {
        token = null
        user = null
    }

    /** Called by API client when a 401 is observed on a non-login request. */
    fun onSessionExpired() {
        clear()
        _events.tryEmit(AuthEvent.SessionExpired)
    }

    val isAuthenticated: Boolean get() = token != null

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_NAME = "name"
        const val KEY_ROLE = "role"
    }
}

data class AuthUser(
    val id: String,
    val email: String,
    val name: String,
    val role: String,
)

sealed interface AuthEvent {
    data object SessionExpired : AuthEvent
}
