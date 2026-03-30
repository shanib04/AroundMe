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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class EventRepository private constructor(
    private val eventDao: EventDao,
    private val eventInteractionDao: EventInteractionDao,
    private val firebase: FirebaseModel,
    private val identityRepository: IdentityRepository,
    private val firestore: FirebaseFirestore,
    private val userRepository: UserRepository,
    private val achievementRepository: AchievementRepository
) {

    // Rule 1: Room is the single source of truth. UI observes Room; Firebase syncs into Room.
    fun observeAll(): Flow<List<Event>> = eventDao.observeAll()

    // Compatibility method for existing UI code
    fun getEvents(): Flow<List<Event>> = observeAll()

    fun getById(id: String) = eventDao.getById(id)

    // Compatibility method name used by older viewmodels/fragments
    fun getEventById(id: String) = getById(id)

    fun observeEventsByPublisher(pubId: String) = eventDao.getEventsByPublisher(pubId)

    fun getEventDetails(eventId: String): Flow<Event?> = eventDao.getById(eventId)

    // Fetch a single event from Firestore and upsert into Room.
    suspend fun refreshEventFromRemote(eventId: String) {
        try {
            val remoteEvent = firestore.collection(EVENTS_COLLECTION)
                .document(eventId)
                .get()
                .await()
                .toObject(Event::class.java)
            if (remoteEvent != null) {
                eventDao.insert(remoteEvent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshEventFromRemote failed for $eventId", e)
        }
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
            syncUserDerivedStats(event.publisherId)
            achievementRepository.unlockForCreatedEvent(event.publisherId)
        }
    }

    suspend fun deleteEvent(id: String) {
        val existingEvent = eventDao.getByIdNow(id)
        eventInteractionDao.deleteByEventId(id)
        eventDao.deleteById(id)
        runCatching { firebase.deleteEvent(id) }
            .onFailure { error ->
                Log.w(TAG, "deleteEvent remote delete failed for $id", error)
            }
        existingEvent?.publisherId?.takeIf(String::isNotBlank)?.let { publisherId ->
            syncUserDerivedStats(publisherId)
        }
    }

    suspend fun deleteEventsByPublisher(pubId: String, removeRemote: Boolean = true) {
        eventDao.deleteEventsByPublisher(pubId)
        if (removeRemote) {
            runCatching { firebase.deleteUserAndEvents(pubId) }
                .onFailure { error ->
                    Log.w(TAG, "deleteEventsByPublisher remote cleanup failed for $pubId", error)
                }
        }
                syncUserDerivedStats(pubId)
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
            syncUserDerivedStats(userId)
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

    private suspend fun syncUserDerivedStats(userId: String) {
        val normalizedUserId = userId.trim()
        if (normalizedUserId.isBlank()) return

        val eventCount = eventDao.getCountByPublisher(normalizedUserId)
        val validationCount = eventInteractionDao.getValidationCountForUser(normalizedUserId)
        val points = (eventCount * UserRepository.EVENT_CREATED_POINTS_AWARD) +
            (validationCount * UserRepository.VALIDATION_POINTS_AWARD)

        userRepository.updateDerivedStats(
            userId = normalizedUserId,
            eventsPublishedCount = eventCount,
            validationsMadeCount = validationCount,
            points = points
        )
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
            syncFromRemoteNow(since)
        }
    }

    suspend fun syncFromRemoteNow(since: Long = 0L) {
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

    suspend fun getValidationCountForUser(userId: String): Int {
        return eventInteractionDao.getValidationCountForUser(userId)
    }

    suspend fun fetchNextPage(pageSize: Int, startAfterTimestamp: Long? = null): Int {
        return try {
            val remote = firebase.fetchEventsPaginated(pageSize, startAfterTimestamp)
            for (r in remote) {
                eventDao.insert(r)
            }
            remote.size
        } catch (e: Exception) {
            Log.e(TAG, "fetchNextPage failed", e)
            0
        }
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
