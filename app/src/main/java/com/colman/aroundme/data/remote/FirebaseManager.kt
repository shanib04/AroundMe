package com.colman.aroundme.data.remote

import android.net.Uri
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
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
        private const val EVENT_RATINGS_COLLECTION = "ratings"
        fun getInstance(): FirebaseModel = INSTANCE ?: synchronized(this) {
            val inst = FirebaseModel()
            INSTANCE = inst
            inst
        }
    }

    suspend fun uploadImage(uri: Uri, remotePath: String, progressCallback: (Int) -> Unit): String {
        return imageUploader.upload(uri, remotePath, progressCallback)
    }

    suspend fun updateEventRatingAggregate(eventId: String, userId: String, rating: Int): Event? {
        val normalizedRating = rating.coerceIn(1, 5)
        val eventRef = firestore.collection(EVENTS_COLLECTION).document(eventId)
        val ratingCollection = eventRef.collection(EVENT_RATINGS_COLLECTION)
        val ratingRef = ratingCollection.document(userId)

        val ratingsByUser = try {
            ratingCollection
                .get()
                .await()
                .documents
                .associate { doc ->
                    doc.id to (doc.getLong("rating")?.toInt()?.coerceIn(1, 5) ?: 0)
                }
                .toMutableMap()
        } catch (_: FirebaseFirestoreException) {
            mutableMapOf()
        }
        ratingsByUser[userId] = normalizedRating

        return firestore.runTransaction { transaction ->
            val eventSnapshot = transaction.get(eventRef)
            val currentEvent = eventSnapshot.toObject(Event::class.java) ?: return@runTransaction null

            val validRatings = ratingsByUser.values.filter { it in 1..5 }
            val updatedEvent = currentEvent.copy(
                averageRating = if (validRatings.isEmpty()) 0.0 else validRatings.average(),
                ratingCount = validRatings.size,
                lastUpdated = System.currentTimeMillis()
            )

            transaction.set(
                ratingRef,
                mapOf(
                    "userId" to userId,
                    "rating" to normalizedRating,
                    "lastUpdated" to updatedEvent.lastUpdated
                ),
                SetOptions.merge()
            )
            transaction.set(eventRef, updatedEvent)
            updatedEvent
        }.await()
    }

    suspend fun fetchEventRating(eventId: String, userId: String): Int? {
        return try {
            firestore.collection(EVENTS_COLLECTION)
                .document(eventId)
                .collection(EVENT_RATINGS_COLLECTION)
                .document(userId)
                .get()
                .await()
                .getLong("rating")
                ?.toInt()
                ?.coerceIn(1, 5)
        } catch (_: FirebaseFirestoreException) {
            null
        }
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
                    "profileImageUrl" to user.profileImageUrl,
                    "email" to user.email,
                    "achievementHistory" to user.achievementHistory,
                    "discoveryRadiusKm" to user.discoveryRadiusKm,
                    "points" to user.points,
                    "eventsPublishedCount" to user.eventsPublishedCount,
                    "validationsMadeCount" to user.validationsMadeCount,
                    "lastUpdated" to user.lastUpdated
                ),
                SetOptions.merge()
            ).await()
    }

    suspend fun updateUserDerivedStats(
        userId: String,
        points: Int,
        eventsPublishedCount: Int,
        validationsMadeCount: Int,
        lastUpdated: Long
    ) {
        firestore.collection(USERS_COLLECTION).document(userId)
            .set(
                mapOf(
                    "points" to points,
                    "eventsPublishedCount" to eventsPublishedCount,
                    "validationsMadeCount" to validationsMadeCount,
                    "lastUpdated" to lastUpdated
                ),
                SetOptions.merge()
            )
            .await()
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
    suspend fun deleteUserAndEventsStrict(userId: String) {
        firestore.collection(USERS_COLLECTION).document(userId).delete().await()
        val events = firestore.collection(EVENTS_COLLECTION).whereEqualTo("publisherId", userId).get().await()
        for (doc in events.documents) {
            firestore.collection(EVENTS_COLLECTION).document(doc.id).delete().await()
        }
    }

    suspend fun deleteUserAndEvents(userId: String) {
        try {
            deleteUserAndEventsStrict(userId)
        } catch (_: FirebaseFirestoreException) {
            // ignore permission or network failures in best-effort cleanup
        }
    }

    suspend fun fetchAllUsers(): List<User> {
        return try {
            firestore.collection(USERS_COLLECTION)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(User::class.java) }
        } catch (_: FirebaseFirestoreException) {
            emptyList()
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
