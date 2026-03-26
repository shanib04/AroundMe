package com.colman.aroundme.data.remote

import android.net.Uri
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/// Simple Firebase manager to abstract away direct Firebase calls from repositories
class FirebaseModel private constructor() {

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val imageUploader = ImageUploader(storage)

    companion object {
        private var INSTANCE: FirebaseModel? = null
        private const val USERS_COLLECTION = "Users"
        private const val EVENTS_COLLECTION = "events"
        fun getInstance(): FirebaseModel = INSTANCE ?: synchronized(this) {
            val inst = FirebaseModel()
            INSTANCE = inst
            inst
        }
    }

    suspend fun uploadImage(uri: Uri, remotePath: String, progressCallback: (Int) -> Unit): String {
        return imageUploader.upload(uri, remotePath, progressCallback)
    }

    suspend fun pushUser(user: User) {
        firestore.collection(USERS_COLLECTION).document(user.id)
            .set(user).await()
    }

    suspend fun updateUserProfile(user: User) {
        firestore.collection(USERS_COLLECTION).document(user.id)
            .set(
                mapOf(
                    "username" to user.username,
                    "displayName" to user.displayName,
                    "profileImageUrl" to user.profileImageUrl
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
    }

    // Check if a username exists in Firestore (excluding a specific userId)
    suspend fun isUsernameTaken(username: String, excludingUserId: String? = null): Boolean {
        return try {
            val docs = firestore.collection(USERS_COLLECTION).whereEqualTo("username", username).get().await().documents
            when {
                docs.isEmpty() -> false
                excludingUserId == null -> true
                else -> docs.any { it.id != excludingUserId }
            }
        } catch (_: FirebaseFirestoreException) {
            false
        }
    }

    // Delete a user and all events published by that user from Firestore
    suspend fun deleteUserAndEvents(userId: String) {
        try {
            // delete user doc
            firestore.collection(USERS_COLLECTION).document(userId).delete().await()
            // delete events by querying publisherId
            val events = firestore.collection(EVENTS_COLLECTION).whereEqualTo("publisherId", userId).get().await()
            for (doc in events.documents) {
                firestore.collection(EVENTS_COLLECTION).document(doc.id).delete().await()
            }
        } catch (_: FirebaseFirestoreException) {
            // ignore permission or network failures in best-effort cleanup
        }
    }

    suspend fun fetchUsersSince(since: Long): List<User> {
        return try {
            firestore.collection(USERS_COLLECTION)
                .whereGreaterThan("lastUpdated", since)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(User::class.java) }
        } catch (_: FirebaseFirestoreException) {
            emptyList()
        }
    }

    suspend fun pushEvent(event: Event) {
        firestore.collection(EVENTS_COLLECTION).document(event.id)
            .set(event).await()
    }

    suspend fun fetchEventsSince(since: Long): List<Event> {
        return try {
            firestore.collection(EVENTS_COLLECTION)
                .whereGreaterThan("lastUpdated", since)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(Event::class.java) }
        } catch (_: FirebaseFirestoreException) {
            emptyList()
        }
    }

    suspend fun fetchUserById(id: String): User? {
        return try {
            firestore.collection(USERS_COLLECTION)
                .document(id)
                .get()
                .await()
                .toObject(User::class.java)
        } catch (_: FirebaseFirestoreException) {
            null
        }
    }
}
