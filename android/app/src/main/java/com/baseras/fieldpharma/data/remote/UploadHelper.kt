package com.baseras.fieldpharma.data.remote

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

object UploadHelper {
    suspend fun uploadImage(api: Api, file: File): String {
        val body = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", file.name, body)
        val res = api.upload(part)
        return res.url
    }
}
