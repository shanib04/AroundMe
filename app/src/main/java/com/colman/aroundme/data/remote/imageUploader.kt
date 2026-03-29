package com.colman.aroundme.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
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
        val fileName = remotePath.substringAfterLast('/').ifBlank { "upload_image.jpg" }
        val localFile = File(appContext.cacheDir, fileName)
        appContext.contentResolver.openInputStream(sourceUri).use { inputStream ->
            requireNotNull(inputStream) { "Unable to open the selected image for upload." }
            FileOutputStream(localFile).use { output ->
                inputStream.copyTo(output)
                output.flush()
            }
        }
        normalizeOrientation(localFile)
        return Uri.fromFile(localFile)
    }

    private fun normalizeOrientation(imageFile: File) {
        val exif = ExifInterface(imageFile.absolutePath)
        val rotation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation == 0f) return

        val originalBitmap = BitmapFactory.decodeFile(imageFile.absolutePath) ?: return
        val rotatedBitmap = Bitmap.createBitmap(
            originalBitmap,
            0,
            0,
            originalBitmap.width,
            originalBitmap.height,
            Matrix().apply { postRotate(rotation) },
            true
        )

        FileOutputStream(imageFile).use { output ->
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
            output.flush()
        }
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.saveAttributes()

        if (rotatedBitmap != originalBitmap) {
            originalBitmap.recycle()
        }
        rotatedBitmap.recycle()
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
