package com.colman.aroundme.data.repository

import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.EventVoteType
import com.colman.aroundme.data.model.User
import com.colman.aroundme.data.remote.FirebaseModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// Repository dedicated to the Event Details screen (Firestore is the source of truth).
class EventDetailsRepository(
    private val firestore: FirebaseFirestore,
    private val firebaseModel: FirebaseModel
) {

    suspend fun fetchEvent(eventId: String): Event? {
        return try {
            firestore.collection(EVENTS_COLLECTION)
                .document(eventId)
                .get()
                .await()
                .toObject(Event::class.java)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchPublisher(publisherId: String): User? {
        return firebaseModel.fetchUserById(publisherId)
    }

    // Atomically increments vote counters in Firestore.
    suspend fun submitVote(eventId: String, voteType: EventVoteType) {
        val field = when (voteType) {
            EventVoteType.ACTIVE -> "activeVotes"
            EventVoteType.INACTIVE -> "inactiveVotes"
        }

        firestore.collection(EVENTS_COLLECTION)
            .document(eventId)
            .update(mapOf(field to FieldValue.increment(1)))
            .await()

        // Best-effort: track that current user interacted (optional)
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isNotBlank()) {
            firestore.collection(INTERACTIONS_COLLECTION)
                .document(uid)
                .collection("events")
                .document(eventId)
                .set(
                    mapOf(
                        "lastVote" to voteType.name,
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()
        }
    }

    // Reads current user's rating for this event (1..5) if exists.
    suspend fun fetchMyRating(eventId: String): Int? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        return try {
            val snap = firestore.collection(EVENTS_COLLECTION)
                .document(eventId)
                .collection(RATINGS_SUBCOLLECTION)
                .document(uid)
                .get()
                .await()
            if (!snap.exists()) return null
            (snap.getLong("rating") ?: return null).toInt()
        } catch (_: Exception) {
            null
        }
    }

    // Submits/updates user's rating (1..5) and updates aggregates.
    suspend fun submitRating(eventId: String, rating: Int) {
        require(rating in 1..5)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("User must be logged in to rate")

        val eventRef = firestore.collection(EVENTS_COLLECTION).document(eventId)
        val ratingRef = eventRef.collection(RATINGS_SUBCOLLECTION).document(uid)

        firestore.runTransaction { tx ->
            val eventSnap = tx.get(eventRef)
            val currentAvg = eventSnap.getDouble("averageRating") ?: 0.0
            val currentCount = (eventSnap.getLong("ratingCount") ?: 0L).toInt()

            val existingSnap = tx.get(ratingRef)
            val previousRating = if (existingSnap.exists()) {
                (existingSnap.getLong("rating") ?: 0L).toInt().coerceIn(0, 5)
            } else {
                0
            }

            val newCount = if (previousRating == 0) currentCount + 1 else currentCount
            val totalSum = currentAvg * currentCount
            val newSum = if (previousRating == 0) totalSum + rating else totalSum - previousRating + rating
            val newAvg = if (newCount == 0) 0.0 else newSum / newCount

            tx.set(
                ratingRef,
                mapOf(
                    "rating" to rating,
                    "updatedAt" to System.currentTimeMillis()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            tx.update(eventRef, mapOf("averageRating" to newAvg, "ratingCount" to newCount))
        }.await()
    }

    companion object {
        private const val EVENTS_COLLECTION = "events"
        private const val INTERACTIONS_COLLECTION = "eventInteractions"
        private const val RATINGS_SUBCOLLECTION = "ratings"

        @Volatile private var INSTANCE: EventDetailsRepository? = null

        fun getInstance(): EventDetailsRepository = INSTANCE ?: synchronized(this) {
            val repo = EventDetailsRepository(FirebaseFirestore.getInstance(), FirebaseModel.getInstance())
            INSTANCE = repo
            repo
        }
    }
}
