package com.colman.aroundme.data.repository

import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.EventVoteType
import com.colman.aroundme.data.model.User
import com.colman.aroundme.data.remote.FirebaseModel
import com.google.firebase.auth.FirebaseAuth
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

    suspend fun fetchMyVote(eventId: String): EventVoteType? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        return try {
            val snap = firestore.collection(EVENTS_COLLECTION)
                .document(eventId)
                .collection(VOTES_SUBCOLLECTION)
                .document(uid)
                .get()
                .await()
            snap.getString(VOTE_TYPE_FIELD)?.let(::toVoteTypeOrNull)
        } catch (_: Exception) {
            null
        }
    }

    // Stores the current user's vote in Firestore and keeps aggregate counters consistent.
    suspend fun submitVote(eventId: String, voteType: EventVoteType): EventVoteType? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank()) return null

        val eventRef = firestore.collection(EVENTS_COLLECTION).document(eventId)
        val voteRef = eventRef.collection(VOTES_SUBCOLLECTION).document(uid)

        return firestore.runTransaction { tx ->
            val eventSnap = tx.get(eventRef)
            val voteSnap = tx.get(voteRef)

            val activeVotes = (eventSnap.getLong(ACTIVE_VOTES_FIELD) ?: 0L).toInt()
            val inactiveVotes = (eventSnap.getLong(INACTIVE_VOTES_FIELD) ?: 0L).toInt()
            val existingVote = voteSnap.getString(VOTE_TYPE_FIELD)?.let(::toVoteTypeOrNull)
            val updatedVote = if (existingVote == voteType) null else voteType

            val nextActiveVotes = activeVotes + voteDelta(existingVote, updatedVote, EventVoteType.ACTIVE)
            val nextInactiveVotes = inactiveVotes + voteDelta(existingVote, updatedVote, EventVoteType.INACTIVE)

            tx.update(
                eventRef,
                mapOf(
                    ACTIVE_VOTES_FIELD to nextActiveVotes.coerceAtLeast(0),
                    INACTIVE_VOTES_FIELD to nextInactiveVotes.coerceAtLeast(0),
                    UPDATED_AT_FIELD to System.currentTimeMillis()
                )
            )

            if (updatedVote == null) {
                tx.delete(voteRef)
            } else {
                tx.set(
                    voteRef,
                    mapOf(
                        VOTE_TYPE_FIELD to updatedVote.storageValue,
                        UPDATED_AT_FIELD to System.currentTimeMillis()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
            }

            updatedVote
        }.await()
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
        private const val RATINGS_SUBCOLLECTION = "ratings"
        private const val VOTES_SUBCOLLECTION = "votes"
        private const val ACTIVE_VOTES_FIELD = "activeVotes"
        private const val INACTIVE_VOTES_FIELD = "inactiveVotes"
        private const val UPDATED_AT_FIELD = "lastUpdated"
        private const val VOTE_TYPE_FIELD = "voteType"

        @Volatile private var INSTANCE: EventDetailsRepository? = null

        fun getInstance(): EventDetailsRepository = INSTANCE ?: synchronized(this) {
            val repo = EventDetailsRepository(FirebaseFirestore.getInstance(), FirebaseModel.getInstance())
            INSTANCE = repo
            repo
        }
    }

    private fun toVoteTypeOrNull(rawValue: String): EventVoteType? {
        return when (rawValue.trim().lowercase()) {
            "active" -> EventVoteType.ACTIVE
            "inactive" -> EventVoteType.INACTIVE
            else -> runCatching { EventVoteType.valueOf(rawValue) }.getOrNull()
        }
    }

    private fun voteDelta(
        existingVote: EventVoteType?,
        updatedVote: EventVoteType?,
        targetVote: EventVoteType
    ): Int {
        val existingContribution = if (existingVote == targetVote) 1 else 0
        val updatedContribution = if (updatedVote == targetVote) 1 else 0
        return updatedContribution - existingContribution
    }

    private val EventVoteType.storageValue: String
        get() = name.lowercase()
}
