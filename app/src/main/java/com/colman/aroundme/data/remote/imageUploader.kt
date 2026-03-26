package com.colman.aroundme.data.remote

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream

class ImageUploader(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val appContext: Context = FirebaseStorage.getInstance().app.applicationContext
) {

    suspend fun upload(uri: Uri, remotePath: String, onProgress: (Int) -> Unit = {}): String {
        val uploadUri = prepareUploadUri(uri, remotePath)
        val storageRef = storage.reference.child(remotePath)
        val uploadTask = storageRef.putFile(uploadUri)

        uploadTask.addOnProgressListener { snapshot ->
            val percent = if (snapshot.totalByteCount > 0) {
                ((100.0 * snapshot.bytesTransferred) / snapshot.totalByteCount).toInt()
            } else {
                0
            }
            onProgress(percent)
        }

        try {
            uploadTask.await()
            return storageRef.downloadUrl.await().toString()
        } catch (exception: Exception) {
            throw exception.toReadableUploadException(storage.app.options.storageBucket, remotePath)
        }
    }

    private fun prepareUploadUri(sourceUri: Uri, remotePath: String): Uri {
        if (sourceUri.scheme == ContentResolver.SCHEME_FILE) {
            return sourceUri
        }

        val inputStream = appContext.contentResolver.openInputStream(sourceUri)
            ?: error("Unable to open the selected image for upload.")
        val fileName = remotePath.substringAfterLast('/').ifBlank { "upload_image.jpg" }
        val localFile = File(appContext.cacheDir, fileName)
        inputStream.use { input ->
            FileOutputStream(localFile).use { output ->
                input.copyTo(output)
                output.flush()
            }
        }
        return Uri.fromFile(localFile)
    }
}

private fun Exception.toReadableUploadException(storageBucket: String?, remotePath: String): Exception {
    val storageException = this as? StorageException ?: return this
    val bucketText = storageBucket?.takeIf { it.isNotBlank() } ?: "<missing bucket>"
    val readableMessage = when (storageException.errorCode) {
        StorageException.ERROR_NOT_AUTHENTICATED ->
            "Image upload failed because the user is not signed in to Firebase Storage."
        StorageException.ERROR_NOT_AUTHORIZED ->
            "Image upload was denied by Firebase Storage rules for '$remotePath' in bucket '$bucketText'."
        StorageException.ERROR_BUCKET_NOT_FOUND ->
            "Image upload failed because Firebase Storage bucket '$bucketText' was not found."
        StorageException.ERROR_PROJECT_NOT_FOUND ->
            "Image upload failed because the Firebase project linked to bucket '$bucketText' was not found."
        StorageException.ERROR_QUOTA_EXCEEDED ->
            "Image upload failed because the Firebase Storage quota was exceeded."
        else -> storageException.localizedMessage ?: "Image upload failed."
    }
    return IllegalStateException(readableMessage, this)
}
