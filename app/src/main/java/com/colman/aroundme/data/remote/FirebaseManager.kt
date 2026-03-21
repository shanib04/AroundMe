package com.colman.aroundme.data.remote

import android.net.Uri
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await

/// Simple Firebase manager to abstract away direct Firebase calls from repositories
class FirebaseModel private constructor() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    companion object {
        private var INSTANCE: FirebaseModel? = null
        fun getInstance(): FirebaseModel = INSTANCE ?: synchronized(this) {
            val inst = FirebaseModel()
            INSTANCE = inst
            inst
        }
    }

    suspend fun uploadImage(uri: Uri, remotePath: String): String {
        val ref = storage.reference.child(remotePath)
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    // New upload variant with progress reporting
    suspend fun uploadImage(uri: Uri, remotePath: String, progressCallback: (Int) -> Unit): String {
        val ref = storage.reference.child(remotePath)
        val deferred = CompletableDeferred<String>()
        val uploadTask = ref.putFile(uri)

        uploadTask.addOnProgressListener { snapshot ->
            val percent = if (snapshot.totalByteCount > 0) ((100.0 * snapshot.bytesTransferred) / snapshot.totalByteCount).toInt() else 0
            progressCallback(percent)
        }.addOnSuccessListener {
            // fetch download url
            ref.downloadUrl.addOnSuccessListener { url ->
                deferred.complete(url.toString())
            }.addOnFailureListener { e -> deferred.completeExceptionally(e) }
        }.addOnFailureListener { e ->
            deferred.completeExceptionally(e)
        }

        return deferred.await()
    }

    suspend fun pushUser(user: User) {
        firestore.collection("users").document(user.id)
            .set(user).await()
    }

    suspend fun fetchUsersSince(since: Long): List<User> {
        val snapshot = firestore.collection("users")
            .whereGreaterThan("lastUpdated", since)
            .get().await()
        return snapshot.documents.mapNotNull { it.toObject(User::class.java) }
    }

    suspend fun pushEvent(event: Event) {
        firestore.collection("events").document(event.id)
            .set(event).await()
    }

    suspend fun fetchEventsSince(since: Long): List<Event> {
        val snapshot = firestore.collection("events")
            .whereGreaterThan("lastUpdated", since)
            .get().await()
        return snapshot.documents.mapNotNull { it.toObject(Event::class.java) }
    }
}
