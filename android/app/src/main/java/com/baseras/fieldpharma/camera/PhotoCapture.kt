package com.baseras.fieldpharma.camera

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.FileProvider
import java.io.File

/**
 * Allocates a file URI under app-internal `files/<subdir>/` and returns
 * the (file, contentUri) pair. The contentUri goes to TakePicture(); we keep
 * the file path for offline upload via SyncWorker.
 */
fun newPhotoFile(ctx: Context, subdir: String): Pair<File, Uri> {
    val dir = File(ctx.filesDir, subdir).apply { mkdirs() }
    val prefix = subdir.takeWhile { it.isLetter() }.ifEmpty { "photo" }
    val file = File(dir, "$prefix-${System.currentTimeMillis()}.jpg")
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    return file to uri
}

/**
 * Composable photo-capture hook. [subdir] segregates files under `filesDir`
 * (e.g. "selfies", "bills"). Returns a () -> Unit launcher.
 */
@Composable
fun rememberPhotoCapture(
    ctx: Context,
    subdir: String = "photos",
    onCaptured: (File) -> Unit,
): () -> Unit {
    val pendingFile = remember { mutableStateOf<File?>(null) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val f = pendingFile.value ?: return@rememberLauncherForActivityResult
        if (success && f.exists() && f.length() > 0) {
            onCaptured(f)
        } else if (f.exists()) {
            f.delete()
        }
        pendingFile.value = null
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val (file, uri) = newPhotoFile(ctx, subdir)
            pendingFile.value = file
            takePicture.launch(uri)
        }
    }

    return {
        if (hasCameraPermission(ctx)) {
            val (file, uri) = newPhotoFile(ctx, subdir)
            pendingFile.value = file
            takePicture.launch(uri)
        } else {
            cameraPermission.launch(android.Manifest.permission.CAMERA)
        }
    }
}

/** Backwards-compat alias used by AttendanceScreen. */
@Composable
fun rememberSelfieCapture(ctx: Context, onCaptured: (File) -> Unit): () -> Unit =
    rememberPhotoCapture(ctx, subdir = "selfies", onCaptured = onCaptured)

private fun hasCameraPermission(ctx: Context): Boolean =
    ctx.checkSelfPermission(android.Manifest.permission.CAMERA) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
