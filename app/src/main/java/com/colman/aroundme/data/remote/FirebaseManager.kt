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

    // Check if a username exists in Firestore (excluding a specific userId)
    suspend fun isUsernameTaken(username: String, excludingUserId: String? = null): Boolean {
        val q = firestore.collection("users").whereEqualTo("username", username).get().await()
        val docs = q.documents
        if (docs.isEmpty()) return false
        if (excludingUserId == null) return docs.isNotEmpty()
        return docs.any { it.id != excludingUserId }
    }

    // Delete a user and all events published by that user from Firestore
    suspend fun deleteUserAndEvents(userId: String) {
        // delete user doc
        firestore.collection("users").document(userId).delete().await()
        // delete events by querying publisherId
        val events = firestore.collection("events").whereEqualTo("publisherId", userId).get().await()
        for (doc in events.documents) {
            firestore.collection("events").document(doc.id).delete().await()
        }
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
