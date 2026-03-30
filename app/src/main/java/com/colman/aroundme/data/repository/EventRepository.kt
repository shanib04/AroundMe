package com.colman.aroundme.data.repository

import android.app.Application
import android.content.Context
import android.util.Log
import com.colman.aroundme.data.local.AppLocalDb
import com.colman.aroundme.data.local.dao.EventDao
import com.colman.aroundme.data.local.dao.EventInteractionDao
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.EventInteraction
import com.colman.aroundme.data.model.EventVoteType
import com.colman.aroundme.data.remote.FirebaseModel
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Repository pattern for Event data
class EventRepository private constructor(
    private val eventDao: EventDao,
    private val eventInteractionDao: EventInteractionDao,
    private val firebase: FirebaseModel,
    private val identityRepository: IdentityRepository,
    private val firestore: FirebaseFirestore,
    private val userRepository: UserRepository,
    private val achievementRepository: AchievementRepository
) {

    init {}

    // Primary events feed: sourced from Firebase (Firestore). Room is a best-effort cache.
    fun observeAll(): Flow<List<Event>> = callbackFlow {
        val reg: ListenerRegistration = firestore.collection(EVENTS_COLLECTION)
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    Log.e(TAG, "observeAll listener error", err)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val events = snaps?.documents?.mapNotNull { it.toObject(Event::class.java) }
                    ?.sortedByDescending { it.lastUpdated }
                    .orEmpty()

                // update local cache (best-effort)
                CoroutineScope(Dispatchers.IO).launch {
                    events.forEach { eventDao.insert(it) }
                }

                trySend(events)
            }

        awaitClose { reg.remove() }
    }

    // Compatibility method for existing UI code
    fun getEvents(): Flow<List<Event>> = observeAll()

    // For places where we need a one-shot fetch (e.g., background sync)
    suspend fun fetchAllOnce(): List<Event> = firebase.fetchEventsSince(0L)

    fun getById(id: String) = eventDao.getById(id)

    // Compatibility method name used by older viewmodels/fragments
    fun getEventById(id: String) = getById(id)

    // Observe events for a specific publisher (LiveData from Room)
    fun observeEventsByPublisher(pubId: String) = eventDao.getEventsByPublisher(pubId)

    // Real-time event details from Firestore.
    fun getEventDetails(eventId: String): Flow<Event?> = callbackFlow {
        val reg: ListenerRegistration = firestore.collection(EVENTS_COLLECTION)
            .document(eventId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "getEventDetails listener error", err)
                    trySend(null)
                    return@addSnapshotListener
                }
                val event = snap?.toObject(Event::class.java)
                trySend(event)
                // Keep local cache warm for other screens
                if (event != null) {
                    CoroutineScope(Dispatchers.IO).launch { eventDao.insert(event) }
                }
            }

        awaitClose { reg.remove() }
    }

    // Real-time event list synchronization from Firestore into Room.
    fun startEventsRealtimeSync() {
        if (eventsListListener != null) return

        eventsListListener = firestore.collection(EVENTS_COLLECTION)
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    Log.e(TAG, "events list listener error", err)
                    return@addSnapshotListener
                }
                val events = snaps?.documents?.mapNotNull { it.toObject(Event::class.java) }.orEmpty()
                CoroutineScope(Dispatchers.IO).launch {
                    events.forEach { eventDao.insert(it) }
                }
            }
    }

    fun stopEventsRealtimeSync() {
        eventsListListener?.remove()
        eventsListListener = null
    }

    suspend fun upsertEvent(event: Event, pushToRemote: Boolean = true) {
        val existingEvent = eventDao.getByIdNow(event.id)
        eventDao.insert(event)
        if (pushToRemote) {
            firebase.pushEvent(event)
        }
        if (existingEvent == null) {
            userRepository.awardEventCreated(event.publisherId)
            achievementRepository.unlockForCreatedEvent(event.publisherId)
        }
    }

    suspend fun deleteEvent(id: String) {
        eventDao.deleteById(id)
        // optionally remove from firebase
    }

    suspend fun deleteEventsByPublisher(pubId: String, removeRemote: Boolean = true) {
        eventDao.deleteEventsByPublisher(pubId)
        // Remote deletion is orchestrated by AuthRepository for account deletion flows.
    }

    // Voting / Rating
    suspend fun submitVote(eventId: String, voteType: EventVoteType): EventVoteType? {
        val userId = identityRepository.awaitAuthenticatedUserId()
            ?: throw IllegalStateException("You must be logged in to report an event.")

        val eventRef = firestore.collection(EVENTS_COLLECTION).document(eventId)
        val voteRef = eventRef.collection(VOTES_SUBCOLLECTION).document(userId)
        val userRef = firestore.collection(USERS_COLLECTION).document(userId)
        val localEvent = eventDao.getByIdNow(eventId)
        val existingInteraction = eventInteractionDao.getInteraction(eventId, userId)
        val voteResult = firestore.runTransaction { tx ->
            val now = System.currentTimeMillis()
            val eventSnap = tx.get(eventRef)
            val currentEvent = eventSnap.toObject(Event::class.java)
                ?: localEvent
                ?: return@runTransaction VoteSubmissionResult(null, null, null, now)

            val existingVote = tx.get(voteRef)
                .getString(VOTE_TYPE_FIELD)
                ?.let(::toVoteTypeOrNull)
                ?: existingInteraction?.voteType
            val updatedVoteType = if (existingVote == voteType) null else voteType
            val shouldAwardValidation = existingVote == null && updatedVoteType != null
            val userSnapshot = if (shouldAwardValidation) tx.get(userRef) else null

            val nextActiveVotes = currentEvent.activeVotes + voteDelta(existingVote, updatedVoteType, EventVoteType.ACTIVE)
            val nextInactiveVotes = currentEvent.inactiveVotes + voteDelta(existingVote, updatedVoteType, EventVoteType.INACTIVE)
            val updatedEvent = currentEvent.copy(
                activeVotes = nextActiveVotes.coerceAtLeast(0),
                inactiveVotes = nextInactiveVotes.coerceAtLeast(0),
                lastUpdated = now
            )
            val currentPoints = (userSnapshot?.getLong(USER_POINTS_FIELD) ?: 0L).toInt()
            val currentValidations = (userSnapshot?.getLong(USER_VALIDATIONS_FIELD) ?: 0L).toInt()

            tx.set(
                eventRef,
                updatedEvent,
                SetOptions.merge()
            )

            if (updatedVoteType == null) {
                tx.delete(voteRef)
            } else {
                tx.set(
                    voteRef,
                    mapOf(
                        VOTE_TYPE_FIELD to updatedVoteType.storageValue,
                        UPDATED_AT_FIELD to now
                    ),
                    SetOptions.merge()
                )
            }

            if (shouldAwardValidation) {
                tx.set(
                    userRef,
                    mapOf(
                        USER_POINTS_FIELD to currentPoints + VALIDATION_POINTS_AWARD,
                        USER_VALIDATIONS_FIELD to currentValidations + 1,
                        USER_LAST_UPDATED_FIELD to now
                    ),
                    SetOptions.merge()
                )
            }

            VoteSubmissionResult(
                updatedVoteType = updatedVoteType,
                previousVoteType = existingVote,
                updatedEvent = updatedEvent,
                lastUpdated = now
            )
        }.await()

        val updatedVoteType = voteResult.updatedVoteType
        val previousVoteType = voteResult.previousVoteType
        val updatedEvent = voteResult.updatedEvent ?: return null

        val updatedInteraction = EventInteraction(
            eventId = eventId,
            userId = userId,
            voteType = updatedVoteType,
            rating = existingInteraction?.rating ?: 0,
            lastUpdated = voteResult.lastUpdated
        )
        eventInteractionDao.upsert(updatedInteraction)

        if (previousVoteType == null && updatedVoteType != null) {
            userRepository.awardValidation(userId, pushToRemote = false)
            runCatching { achievementRepository.unlockForValidation(userId) }
                .onFailure { error ->
                    Log.w(TAG, "unlockForValidation failed after successful vote", error)
                }
        }

        eventDao.insert(updatedEvent)
        runCatching { achievementRepository.unlockForPublisherEventState(updatedEvent) }
            .onFailure { error ->
                Log.w(TAG, "unlockForPublisherEventState failed after successful vote", error)
            }

        return updatedVoteType
    }

    suspend fun submitRating(eventId: String, rating: Int): EventInteraction? {
        val normalizedRating = rating.coerceIn(1, 5)
        val userId = identityRepository.awaitAuthenticatedUserId()
            ?: throw IllegalStateException("You must be logged in to rate an event.")

        val currentEvent = eventDao.getByIdNow(eventId)
        val existingInteraction = eventInteractionDao.getInteraction(eventId, userId)
        val lastUpdated = System.currentTimeMillis()
        val updatedInteraction = EventInteraction(
            eventId = eventId,
            userId = userId,
            voteType = existingInteraction?.voteType,
            rating = normalizedRating,
            lastUpdated = lastUpdated
        )
        eventInteractionDao.upsert(updatedInteraction)

        val localUpdatedEvent = currentEvent?.let { event ->
            val interactions = eventInteractionDao.getInteractionsForEvent(eventId)
            val ratings = interactions.map { it.rating }.filter { it > 0 }
            event.copy(
                activeVotes = interactions.count { it.voteType == EventVoteType.ACTIVE },
                inactiveVotes = interactions.count { it.voteType == EventVoteType.INACTIVE },
                averageRating = calculateAverageRating(ratings),
                ratingCount = ratings.size,
                lastUpdated = lastUpdated
            )
        }

        val remoteUpdatedEvent = try {
            val eventRef = firestore.collection(EVENTS_COLLECTION).document(eventId)
            val ratingRef = eventRef.collection(RATINGS_SUBCOLLECTION).document(userId)

            firestore.runTransaction { tx ->
                val eventSnap = tx.get(eventRef)
                val eventFromRemote = eventSnap.toObject(Event::class.java)
                    ?: localUpdatedEvent
                    ?: return@runTransaction null

                val existingSnap = tx.get(ratingRef)
                val previousRating = if (existingSnap.exists()) {
                    (existingSnap.getLong(RATING_FIELD) ?: 0L).toInt().coerceIn(0, 5)
                } else {
                    0
                }

                val newCount = if (previousRating == 0) eventFromRemote.ratingCount + 1 else eventFromRemote.ratingCount
                val totalSum = eventFromRemote.averageRating * eventFromRemote.ratingCount
                val newSum = if (previousRating == 0) {
                    totalSum + normalizedRating
                } else {
                    totalSum - previousRating + normalizedRating
                }
                val updatedEvent = eventFromRemote.copy(
                    averageRating = if (newCount == 0) 0.0 else newSum / newCount,
                    ratingCount = newCount,
                    lastUpdated = lastUpdated
                )

                tx.set(
                    ratingRef,
                    mapOf(
                        RATING_FIELD to normalizedRating,
                        UPDATED_AT_FIELD to lastUpdated
                    ),
                    SetOptions.merge()
                )
                tx.set(
                    eventRef,
                    updatedEvent,
                    SetOptions.merge()
                )

                updatedEvent
            }.await()
        } catch (e: Exception) {
            Log.e(TAG, "submitRating remote aggregate sync failed", e)
            firebase.updateEventRatingAggregate(eventId, userId, normalizedRating)
        }

        val updatedEvent = remoteUpdatedEvent ?: localUpdatedEvent
        if (updatedEvent != null) {
            eventDao.insert(updatedEvent)
            runCatching { achievementRepository.unlockForPublisherEventState(updatedEvent) }
                .onFailure { error ->
                    Log.w(TAG, "unlockForPublisherEventState failed after successful rating", error)
                }
        }

        return updatedInteraction
    }

    fun syncFromRemote(since: Long = 0L) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val remote = firebase.fetchEventsSince(since)
                val userId = identityRepository.getUserId()
                for (r in remote) {
                    eventDao.insert(r)
                    val remoteRating = firebase.fetchEventRating(r.id, userId)
                    if (remoteRating != null) {
                        val existingInteraction = eventInteractionDao.getInteraction(r.id, userId)
                        eventInteractionDao.upsert(
                            EventInteraction(
                                eventId = r.id,
                                userId = userId,
                                voteType = existingInteraction?.voteType,
                                rating = remoteRating,
                                lastUpdated = maxOf(existingInteraction?.lastUpdated ?: 0L, r.lastUpdated)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "syncFromRemote failed", e)
            }
        }
    }

    suspend fun getValidationCountForUser(userId: String): Int {
        return eventInteractionDao.getValidationCountForUser(userId)
    }

    fun observeInteraction(eventId: String) =
        eventInteractionDao.observeInteraction(eventId, identityRepository.getAuthenticatedUserIdOrNull().orEmpty())

    suspend fun refreshMyInteraction(eventId: String): EventInteraction? {
        val userId = identityRepository.awaitAuthenticatedUserId() ?: return null

        val existingInteraction = eventInteractionDao.getInteraction(eventId, userId)
        val voteResult = runCatching {
            firestore.collection(EVENTS_COLLECTION)
                .document(eventId)
                .collection(VOTES_SUBCOLLECTION)
                .document(userId)
                .get()
                .await()
                .getString(VOTE_TYPE_FIELD)
                ?.let(::toVoteTypeOrNull)
        }
        val ratingResult = runCatching {
            firestore.collection(EVENTS_COLLECTION)
                .document(eventId)
                .collection(RATINGS_SUBCOLLECTION)
                .document(userId)
                .get()
                .await()
                .getLong(RATING_FIELD)
                ?.toInt()
                ?.takeIf { it in 1..5 }
        }

        if (voteResult.isFailure && ratingResult.isFailure) {
            return existingInteraction
        }

        val refreshedInteraction = EventInteraction(
            eventId = eventId,
            userId = userId,
            voteType = voteResult.getOrElse { existingInteraction?.voteType },
            rating = ratingResult.getOrElse { existingInteraction?.rating?.takeIf { it in 1..5 } } ?: 0,
            lastUpdated = System.currentTimeMillis()
        )
        eventInteractionDao.upsert(refreshedInteraction)
        return refreshedInteraction
    }

    suspend fun getInteraction(eventId: String): EventInteraction? {
        val userId = identityRepository.getAuthenticatedUserIdOrNull() ?: return null
        return eventInteractionDao.getInteraction(eventId, userId)
    }

    private suspend fun recalculateEventAggregates(
        event: Event,
        pushToRemote: Boolean = true
    ): Event {
        val interactions = eventInteractionDao.getInteractionsForEvent(event.id)
        val activeVotes = interactions.count { it.voteType == EventVoteType.ACTIVE }
        val inactiveVotes = interactions.count { it.voteType == EventVoteType.INACTIVE }
        val ratings = interactions.map { it.rating }.filter { it > 0 }

        val updatedEvent = event.copy(
            activeVotes = activeVotes,
            inactiveVotes = inactiveVotes,
            averageRating = calculateAverageRating(ratings),
            ratingCount = ratings.size,
            lastUpdated = System.currentTimeMillis()
        )

        eventDao.insert(updatedEvent)
        if (pushToRemote) {
            try {
                firebase.pushEvent(updatedEvent)
            } catch (e: Exception) {
                Log.e(TAG, "recalculateEventAggregates remote sync failed", e)
            }
        }
        achievementRepository.unlockForPublisherEventState(updatedEvent)
        return updatedEvent
    }

    internal fun calculateAverageRating(ratings: List<Int>): Double {
        val validRatings = ratings.filter { it in 1..5 }
        return if (validRatings.isEmpty()) 0.0 else validRatings.average()
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

    private data class VoteSubmissionResult(
        val updatedVoteType: EventVoteType?,
        val previousVoteType: EventVoteType?,
        val updatedEvent: Event?,
        val lastUpdated: Long
    )

    companion object {
        private const val TAG = "EventRepository"
        private const val EVENTS_COLLECTION = "events"
        private const val USERS_COLLECTION = "Users"
        private const val VOTES_SUBCOLLECTION = "votes"
        private const val RATINGS_SUBCOLLECTION = "ratings"
        private const val ACTIVE_VOTES_FIELD = "activeVotes"
        private const val INACTIVE_VOTES_FIELD = "inactiveVotes"
        private const val AVERAGE_RATING_FIELD = "averageRating"
        private const val RATING_COUNT_FIELD = "ratingCount"
        private const val RATING_FIELD = "rating"
        private const val UPDATED_AT_FIELD = "lastUpdated"
        private const val VOTE_TYPE_FIELD = "voteType"
        private const val USER_LAST_UPDATED_FIELD = "lastUpdated"
        private const val USER_POINTS_FIELD = "points"
        private const val USER_VALIDATIONS_FIELD = "validationsMadeCount"
        private const val VALIDATION_POINTS_AWARD = 2

        @Volatile
        private var INSTANCE: EventRepository? = null

        @Volatile
        private var eventsListListener: ListenerRegistration? = null

        fun getInstance(context: Context): EventRepository = INSTANCE ?: synchronized(this) {
            val db = AppLocalDb.getInstance(context)
            val repo = EventRepository(
                eventDao = db.eventDao(),
                eventInteractionDao = db.eventInteractionDao(),
                firebase = FirebaseModel.getInstance(),
                identityRepository = IdentityRepository(context),
                firestore = FirebaseFirestore.getInstance(),
                userRepository = UserRepository.getInstance(context),
                achievementRepository = AchievementRepository.getInstance(context.applicationContext as Application)
            )
            INSTANCE = repo
            repo
        }
    }
}
