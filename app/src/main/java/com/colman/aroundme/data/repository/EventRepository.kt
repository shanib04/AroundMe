package com.colman.aroundme.data.repository

import android.content.Context
import android.util.Log
import com.colman.aroundme.data.local.AppLocalDb
import com.colman.aroundme.data.local.dao.EventDao
import com.colman.aroundme.data.local.dao.EventInteractionDao
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.EventInteraction
import com.colman.aroundme.data.model.EventVoteType
import com.colman.aroundme.data.remote.FirebaseModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

// Repository pattern for Event data
class EventRepository private constructor(
    private val eventDao: EventDao,
    private val eventInteractionDao: EventInteractionDao,
    private val firebase: FirebaseModel,
    private val identityRepository: IdentityRepository,
    private val firestore: FirebaseFirestore,
    private val userRepository: UserRepository
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
        }
    }

    suspend fun deleteEvent(id: String) {
        eventDao.deleteById(id)
        // optionally remove from firebase
    }

    suspend fun deleteEventsByPublisher(pubId: String, removeRemote: Boolean = true) {
        eventDao.deleteEventsByPublisher(pubId)
        if (removeRemote) {
            try {
                firebase.deleteUserAndEvents(pubId)
            } catch (_: Exception) {
                // ignore remote failures
            }
        }
    }

    // Voting / Rating
    suspend fun submitVote(eventId: String, voteType: EventVoteType): Event? {
        val userId = identityRepository.getUserId()
        val currentEvent = eventDao.getByIdNow(eventId) ?: return null

        val existingInteraction = eventInteractionDao.getInteraction(eventId, userId)
        val updatedVoteType = if (existingInteraction?.voteType == voteType) null else voteType
        val updatedInteraction = EventInteraction(
            eventId = eventId,
            userId = userId,
            voteType = updatedVoteType,
            rating = existingInteraction?.rating ?: 0,
            lastUpdated = System.currentTimeMillis()
        )
        eventInteractionDao.upsert(updatedInteraction)

        // Reward first-time validation/vote
        if (existingInteraction?.voteType == null && updatedVoteType != null) {
            userRepository.awardValidation(userId)
        }

        return recalculateEventAggregates(currentEvent, pushToRemote = true)
    }

    suspend fun submitRating(eventId: String, rating: Int): EventInteraction? {
        val normalizedRating = rating.coerceIn(1, 5)
        val userId = identityRepository.getUserId()
        val currentEvent = eventDao.getByIdNow(eventId) ?: return null

        val existingInteraction = eventInteractionDao.getInteraction(eventId, userId)
        val updatedInteraction = EventInteraction(
            eventId = eventId,
            userId = userId,
            voteType = existingInteraction?.voteType,
            rating = normalizedRating,
            lastUpdated = System.currentTimeMillis()
        )
        eventInteractionDao.upsert(updatedInteraction)

        val localUpdatedEvent = recalculateEventAggregates(currentEvent, pushToRemote = false)

        // Best-effort remote aggregate update (don’t fail the UX on network)
        val remoteUpdatedEvent = try {
            firebase.updateEventRatingAggregate(eventId, userId, normalizedRating)
        } catch (e: Exception) {
            Log.e(TAG, "submitRating remote aggregate sync failed", e)
            null
        }

        eventDao.insert(remoteUpdatedEvent ?: localUpdatedEvent)
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
        eventInteractionDao.observeInteraction(eventId, identityRepository.getUserId())

    suspend fun getInteraction(eventId: String): EventInteraction? {
        return eventInteractionDao.getInteraction(eventId, identityRepository.getUserId())
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
        return updatedEvent
    }

    internal fun calculateAverageRating(ratings: List<Int>): Double {
        val validRatings = ratings.filter { it in 1..5 }
        return if (validRatings.isEmpty()) 0.0 else validRatings.average()
    }

    companion object {
        private const val TAG = "EventRepository"
        private const val EVENTS_COLLECTION = "events"

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
                userRepository = UserRepository.getInstance(context)
            )
            INSTANCE = repo
            repo
        }
    }
}
