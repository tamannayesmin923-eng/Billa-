package com.example.data.storage

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.model.StagedAttachment
import com.google.firebase.Firebase
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.UploadTask
import com.google.firebase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

sealed interface UploadOutcome {
    data class CloudUploaded(
        val downloadUrl: String,
        val storagePath: String
    ) : UploadOutcome

    data class InlineFallback(
        val base64Jpeg: String
    ) : UploadOutcome
}

class AttachmentTransferManager(private val context: Context) {

    private val okHttp = OkHttpClient()
    @Volatile
    private var activeTask: UploadTask? = null

    fun resolveAttachment(uri: Uri, forceImage: Boolean): Result<StagedAttachment> {
        return try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)
                ?: if (forceImage) "image/jpeg" else "application/octet-stream"
            var fileName = if (forceImage) "photo_${System.currentTimeMillis()}.jpg" else "document_${System.currentTimeMillis()}"
            var sizeBytes = 0L

            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) {
                        cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { fileName = it }
                    }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
            if (sizeBytes <= 0L) {
                resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    if (afd.length > 0) sizeBytes = afd.length
                }
            }
            val isImage = forceImage || mimeType.startsWith("image/")
            Result.success(
                StagedAttachment(
                    uri = uri,
                    isImage = isImage,
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes.coerceAtLeast(0L)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadAttachment(
        conversationId: String,
        senderUid: String,
        attachment: StagedAttachment,
        onProgress: (Float, Long, Long) -> Unit
    ): Result<UploadOutcome> {
        if (attachment.sizeBytes > MAX_UPLOAD_BYTES) {
            return Result.failure(IllegalArgumentException("File exceeds 15 MB limit"))
        }

        val sanitizedName = attachment.fileName
            .replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
            .take(80)
        val storagePath = "chat_attachments/$conversationId/$senderUid/${UUID.randomUUID()}_$sanitizedName"

        return try {
            val storageRef = Firebase.storage.reference.child(storagePath)
            val metadata = StorageMetadata.Builder()
                .setContentType(attachment.mimeType)
                .build()

            val task = storageRef.putFile(attachment.uri, metadata)
            activeTask = task
            task.addOnProgressListener { snap ->
                val total = snap.totalByteCount.coerceAtLeast(attachment.sizeBytes).coerceAtLeast(1L)
                val progress = (snap.bytesTransferred.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                onProgress(progress, snap.bytesTransferred, total)
            }
            task.await()
            activeTask = null
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Result.success(UploadOutcome.CloudUploaded(downloadUrl, storagePath))
        } catch (e: CancellationException) {
            activeTask?.cancel()
            activeTask = null
            throw e
        } catch (e: Exception) {
            activeTask = null
            Log.w("AttachmentUpload", "Firebase Storage upload failed, checking fallback: ${e.message}")
            if (attachment.isImage) {
                val compressed = compressImageToBase64(attachment.uri)
                if (compressed != null) {
                    onProgress(1f, attachment.sizeBytes, attachment.sizeBytes.coerceAtLeast(1L))
                    return Result.success(UploadOutcome.InlineFallback(compressed))
                }
            }
            Result.failure(e)
        }
    }

    suspend fun uploadAvatar(uid: String, imageUri: Uri): Result<String> {
        val storagePath = "avatars/$uid/avatar_${System.currentTimeMillis()}.jpg"
        return try {
            val ref = Firebase.storage.reference.child(storagePath)
            ref.putFile(imageUri).await()
            val url = ref.downloadUrl.await().toString()
            Result.success(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun cancelActiveUpload() {
        activeTask?.cancel()
        activeTask = null
    }

    suspend fun downloadAndOpen(
        fileName: String,
        mimeType: String,
        downloadUrl: String?,
        inlinePreviewBase64: String?,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "tammie_downloads").apply { mkdirs() }
            val safeFile = File(dir, fileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_"))

            if (!downloadUrl.isNullOrBlank()) {
                val req = Request.Builder().url(downloadUrl).build()
                okHttp.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code}")
                    }
                    val body = response.body ?: error("Empty download response")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        FileOutputStream(safeFile).use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            var downloaded = 0L
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (total > 0) {
                                    onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                }
            } else if (!inlinePreviewBase64.isNullOrBlank()) {
                val bytes = Base64.decode(inlinePreviewBase64, Base64.NO_WRAP)
                safeFile.writeBytes(bytes)
                onProgress(1f)
            } else {
                error("No file source available")
            }

            withContext(Dispatchers.Main) {
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    safeFile
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, mimeType.ifBlank { "*/*" })
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun compressImageToBase64(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@withContext null

            val maxDim = 900
            val scale = minOf(maxDim.toFloat() / rawBitmap.width, maxDim.toFloat() / rawBitmap.height, 1f)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    rawBitmap,
                    (rawBitmap.width * scale).toInt().coerceAtLeast(1),
                    (rawBitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                rawBitmap
            }

            var quality = 78
            var encoded: String
            do {
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                quality -= 14
            } while (encoded.length > 350_000 && quality >= 28)

            if (scaled !== rawBitmap) scaled.recycle()
            rawBitmap.recycle()

            encoded.takeIf { it.length <= 420_000 }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val MAX_UPLOAD_BYTES = 15L * 1024L * 1024L
    }
}
