package com.baseras.fieldpharma.data.remote

import com.baseras.fieldpharma.auth.AuthStore
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object ApiClient {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun create(baseUrl: String, authStore: AuthStore): Pair<Api, OkHttpClient> {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val auth = okhttp3.Interceptor { chain ->
            val req = chain.request()
            val isLogin = req.url.encodedPath.endsWith("/auth/login")
            val newReq = if (!isLogin && authStore.token != null) {
                req.newBuilder().addHeader("Authorization", "Bearer ${authStore.token}").build()
            } else req

            val res = chain.proceed(newReq)
            if (!isLogin && res.code == 401) {
                authStore.onSessionExpired()
            }
            res
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(logging)
            .build()

        val api = Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(Api::class.java)

        return api to client
    }
}
