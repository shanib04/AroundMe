package com.colman.aroundme.features.feed

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.EventVoteType
import com.colman.aroundme.data.model.MapCoordinate
import com.colman.aroundme.data.model.User
import com.colman.aroundme.data.repository.EventRepository
import com.colman.aroundme.data.repository.UserRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val DEFAULT_FEED_LOCATION_LABEL = "Kefar Sava"

data class FeedEventItem(
    val event: Event,
    val hostName: String,
    val hostSubtitle: String,
    val locationText: String,
    val distanceText: String,
    val statusText: String,
    val activeVotesText: String,
    val inactiveVotesText: String,
    val averageRatingText: String,
    val isActiveVoteSelected: Boolean,
    val isInactiveVoteSelected: Boolean,
    val postedText: String,
    val tagLabels: List<String>
)

data class FeedUiState(
    val items: List<FeedEventItem> = emptyList(),
    val sortOption: FeedSortOption = FeedSortOption.NEWEST,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val emptyMessage: String = "",
    val userLocationLabel: String = DEFAULT_FEED_LOCATION_LABEL
)

class FeedViewModel(
    private val repository: EventRepository,
    userRepository: UserRepository
) : ViewModel() {

    private val allEvents = repository.observeAll().asLiveData()
    private val allUsers = userRepository.observeAll().asLiveData()
    private val uiStateSource = MediatorLiveData<FeedUiState>()

    private var sourceEvents: List<Event> = emptyList()
    private var currentSortOption: FeedSortOption = FeedSortOption.NEWEST
    private var currentPageSize: Int = PAGE_SIZE
    private var isRefreshing = false
    private var isLoadingMore = false
    private var userLocation = DEFAULT_USER_LOCATION
    private var userLocationLabel = DEFAULT_FEED_LOCATION_LABEL
    private var interactionCache: Map<String, EventVoteType?> = emptyMap()
    private var interactionSyncVersion: Long = 0L
    private var sourceUsersById: Map<String, User> = emptyMap()

    val uiState: LiveData<FeedUiState> = uiStateSource

    init {
        uiStateSource.value = FeedUiState(emptyMessage = "Pull to refresh to load nearby events.")
        uiStateSource.addSource(allEvents) { events ->
            sourceEvents = events
            viewModelScope.launch {
                syncInteractionCache(events)
            }
        }
        uiStateSource.addSource(allUsers) { users ->
            sourceUsersById = users.associateBy { it.id }
            publishState()
        }
    }

    fun setSortOption(sortOption: FeedSortOption) {
        if (currentSortOption == sortOption) return
        currentSortOption = sortOption
        currentPageSize = PAGE_SIZE
        publishState()
    }

    fun refresh() {
        if (isRefreshing) return
        viewModelScope.launch {
            isRefreshing = true
            publishState()
            delay(500)
            currentPageSize = PAGE_SIZE
            isRefreshing = false
            syncInteractionCache(sourceEvents)
        }
    }

    fun loadMoreEvents() {
        if (isLoadingMore) return
        val sorted = sortedEvents(sourceEvents, currentSortOption)
        if (currentPageSize >= sorted.size) return

        viewModelScope.launch {
            isLoadingMore = true
            publishState()
            delay(450)
            currentPageSize = (currentPageSize + PAGE_SIZE).coerceAtMost(sorted.size)
            isLoadingMore = false
            publishState()
        }
    }

    fun updateUserLocation(location: MapCoordinate, label: String = DEFAULT_FEED_LOCATION_LABEL) {
        userLocation = location
        userLocationLabel = label.ifBlank { DEFAULT_FEED_LOCATION_LABEL }
        publishState()
    }

    fun submitVote(eventId: String, voteType: EventVoteType) {
        interactionCache = interactionCache + (eventId to voteType)
        publishState()
        viewModelScope.launch {
            repository.submitVote(eventId, voteType)
            syncInteractionCache(sourceEvents)
        }
    }

    private suspend fun syncInteractionCache(events: List<Event>) {
        val syncVersion = ++interactionSyncVersion
        val updatedSelections = buildMap {
            events.forEach { event ->
                put(event.id, repository.getInteraction(event.id)?.voteType)
            }
        }
        if (syncVersion != interactionSyncVersion) return
        interactionCache = updatedSelections
        publishState()
    }

    private fun publishState() {
        val sorted = sortedEvents(sourceEvents, currentSortOption)
        val visibleItems = sorted.take(currentPageSize).map { it.toFeedEventItem(userLocation) }
        uiStateSource.value = FeedUiState(
            items = visibleItems,
            sortOption = currentSortOption,
            isRefreshing = isRefreshing,
            isLoadingMore = isLoadingMore,
            hasMore = currentPageSize < sorted.size,
            emptyMessage = if (sorted.isEmpty()) "No events to show yet." else "",
            userLocationLabel = userLocationLabel
        )
    }

    private fun sortedEvents(events: List<Event>, sortOption: FeedSortOption): List<Event> {
        return when (sortOption) {
            FeedSortOption.DISTANCE -> events.sortedBy {
                distanceKm(userLocation, MapCoordinate(it.latitude, it.longitude))
            }
            FeedSortOption.ENDING_SOON -> events.sortedWith(
                compareBy<Event> { endingRank(it) }
                    .thenBy { endingMinutes(it) }
                    .thenBy { distanceKm(userLocation, MapCoordinate(it.latitude, it.longitude)) }
            )
            FeedSortOption.NEWEST -> events.sortedByDescending { it.publishTime }
        }
    }

    private fun endingRank(event: Event): Int {
        return when {
            event.isEnded -> 2
            event.expirationTime > 0L -> 0
            else -> 1
        }
    }

    private fun endingMinutes(event: Event): Long {
        return if (event.expirationTime > 0L) {
            ((event.expirationTime - System.currentTimeMillis()) / 60000L).coerceAtLeast(0L)
        } else {
            Long.MAX_VALUE
        }
    }

    private fun Event.toFeedEventItem(userLocation: MapCoordinate): FeedEventItem {
        val distanceKm = distanceKm(userLocation, MapCoordinate(latitude, longitude))
        val selectedVote = interactionCache[id]
        val hostUser = sourceUsersById[publisherId]
        return FeedEventItem(
            event = this,
            hostName = hostUser.toFeedHostName(),
            hostSubtitle = hostUser.toFeedHostSubtitle(category),
            locationText = locationName.ifBlank { "Unknown location" },
            distanceText = formatDistance(distanceKm),
            statusText = timeRemaining.ifBlank {
                if (isEnded) "Ended" else buildStatusFromExpiration(expirationTime)
            },
            activeVotesText = formatCompactCount(activeVotes),
            inactiveVotesText = formatCompactCount(inactiveVotes),
            averageRatingText = formatAverageRating(averageRating, ratingCount),
            isActiveVoteSelected = selectedVote == EventVoteType.ACTIVE,
            isInactiveVoteSelected = selectedVote == EventVoteType.INACTIVE,
            postedText = formatPostedTime(publishTime),
            tagLabels = tags.filter { it.isNotBlank() }.take(3).map { "#${it.trim().replace(" ", "")}" }
        )
    }

    private fun User?.toFeedHostName(): String {
        return this?.displayName?.takeIf { it.isNotBlank() } ?: "Unknown Publisher"
    }

    private fun User?.toFeedHostSubtitle(fallbackCategory: String): String {
        val user = this
        return user?.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
            ?: fallbackCategory.ifBlank { "Event Host" }
    }

    private fun distanceKm(start: MapCoordinate, end: MapCoordinate): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(end.latitude - start.latitude)
        val dLon = Math.toRadians(end.longitude - start.longitude)
        val lat1 = Math.toRadians(start.latitude)
        val lat2 = Math.toRadians(end.latitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
            sin(dLon / 2) * sin(dLon / 2) * cos(lat1) * cos(lat2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusKm * c
    }

    private fun buildStatusFromExpiration(expirationTime: Long): String {
        if (expirationTime <= 0L) return "Live"
        val remainingMinutes = ((expirationTime - System.currentTimeMillis()) / 60000L).coerceAtLeast(0L)
        return when {
            remainingMinutes < 60L -> "Ends in ${remainingMinutes}m"
            remainingMinutes < 1440L -> "Ends in ${remainingMinutes / 60L}h"
            else -> "Ends in ${remainingMinutes / 1440L}d"
        }
    }

    private fun formatDistance(distanceKm: Double): String {
        return if (distanceKm < 1.0) {
            "${(distanceKm * 1000).toInt()}m"
        } else {
            String.format(Locale.US, "%.1fkm", distanceKm)
        }
    }

    private fun formatCompactCount(value: Int): String {
        return if (value >= 1000) {
            String.format(Locale.US, "%.1fk", value / 1000f)
        } else {
            value.toString()
        }
    }

    private fun formatAverageRating(averageRating: Double, ratingCount: Int): String {
        return if (ratingCount <= 0) {
            "New"
        } else {
            String.format(Locale.US, "%.1f★", averageRating)
        }
    }

    private fun formatPostedTime(timestamp: Long): String {
        val elapsedMinutes = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L)) / 60000L
        return when {
            elapsedMinutes < 1L -> "POSTED JUST NOW"
            elapsedMinutes < 60L -> "POSTED ${elapsedMinutes}M AGO"
            elapsedMinutes < 1440L -> "POSTED ${elapsedMinutes / 60L}H AGO"
            else -> "POSTED ${elapsedMinutes / 1440L}D AGO"
        }
    }

    class Factory(
        private val repository: EventRepository,
        private val userRepository: UserRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FeedViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return FeedViewModel(repository, userRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        private const val PAGE_SIZE = 4
        private val DEFAULT_USER_LOCATION = MapCoordinate(32.1782, 34.9076)
    }
}
