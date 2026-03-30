package com.colman.aroundme.features.feed

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.User
import com.colman.aroundme.utils.MapCoordinate
import com.colman.aroundme.utils.distanceKm
import com.colman.aroundme.data.repository.EventRepository
import com.colman.aroundme.data.repository.UserRepository
import kotlinx.coroutines.launch

private const val DEFAULT_FEED_LOCATION_LABEL = "Kefar Sava"

data class FeedEventItem(
    val item: EventCardItem
)

data class FeedUiState(
    val items: List<FeedEventItem> = emptyList(),
    val sortOption: FeedSortOption = FeedSortOption.NEWEST,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val emptyMessage: String = "",
    val userLocationLabel: String = DEFAULT_FEED_LOCATION_LABEL,
    val scrollToTopToken: Long = 0L
)

enum class FeedSortOption(val label: String) {
    DISTANCE("Distance"),
    ENDING_SOON("Ending Soon"),
    NEWEST("Newest")
}

class FeedViewModel(
    repository: EventRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val allEvents = repository.observeAll().asLiveData()
    private val allUsers = userRepository.observeAll().asLiveData()
    private val uiStateSource = MediatorLiveData<FeedUiState>()

    private var sourceEvents: List<Event> = emptyList()
    private var currentSortOption: FeedSortOption = FeedSortOption.NEWEST
    private var currentPageSize: Int = PAGE_SIZE
    private var isLoadingMore = false
    private var userLocation = DEFAULT_USER_LOCATION
    private var userLocationLabel = DEFAULT_FEED_LOCATION_LABEL
    private var sourceUsersById: Map<String, User> = emptyMap()
    private var scrollToTopToken: Long = 0L
    private var hasLoadedEvents = false
    private var hasLoadedUsers = false
    private var resolvingPublisherIds: Set<String> = emptySet()

    val uiState: LiveData<FeedUiState> = uiStateSource

    init {
        uiStateSource.value = FeedUiState(emptyMessage = "Pull to refresh to load nearby events.")
        uiStateSource.addSource(allEvents) { events ->
            hasLoadedEvents = true
            sourceEvents = events.filterNot { it.isEnded }
            currentPageSize = currentPageSize.coerceAtMost(sourceEvents.size.coerceAtLeast(PAGE_SIZE))
            publishState()
        }
        uiStateSource.addSource(allUsers) { users ->
            hasLoadedUsers = true
            sourceUsersById = users.associateBy { it.id }
            publishState()
        }
    }

    fun setSortOption(sortOption: FeedSortOption) {
        currentSortOption = sortOption
        currentPageSize = PAGE_SIZE
        scrollToTopToken = System.currentTimeMillis()
        publishState()
    }

    fun loadMoreEvents() {
        if (isLoadingMore) return
        val sorted = sortedEvents(sourceEvents, currentSortOption)
        if (currentPageSize >= sorted.size) return

        isLoadingMore = true
        currentPageSize = (currentPageSize + PAGE_SIZE).coerceAtMost(sorted.size)
        isLoadingMore = false
        publishState()
    }

    fun updateUserLocation(location: MapCoordinate, label: String = DEFAULT_FEED_LOCATION_LABEL) {
        userLocation = location
        userLocationLabel = label.ifBlank { DEFAULT_FEED_LOCATION_LABEL }
        if (currentSortOption == FeedSortOption.DISTANCE || currentSortOption == FeedSortOption.ENDING_SOON) {
            currentPageSize = PAGE_SIZE.coerceAtMost(sourceEvents.size.coerceAtLeast(PAGE_SIZE))
        }
        publishState()
    }

    private fun publishState() {
        val sorted = sortedEvents(sourceEvents, currentSortOption)
        val visibleCount = currentPageSize.coerceAtMost(sorted.size.coerceAtLeast(PAGE_SIZE))
        val visibleEvents = sorted.take(visibleCount)
        val unresolvedPublisherIds = visibleEvents
            .map(Event::publisherId)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .filter(::isPublisherUnresolved)

        prefetchUsers(visibleEvents)
        resolveVisiblePublishers(unresolvedPublisherIds)

        val isInitialLoading = !hasLoadedEvents
        val visibleItems = visibleEvents.map { it.toFeedEventItem(userLocation) }
        uiStateSource.value = FeedUiState(
            items = visibleItems,
            sortOption = currentSortOption,
            isInitialLoading = isInitialLoading,
            isRefreshing = false,
            isLoadingMore = isLoadingMore,
            hasMore = visibleCount < sorted.size,
            emptyMessage = if (!isInitialLoading && sorted.isEmpty()) "No events to show yet." else "",
            userLocationLabel = userLocationLabel,
            scrollToTopToken = scrollToTopToken
        )
    }

    private fun sortedEvents(events: List<Event>, sortOption: FeedSortOption): List<Event> {
        return when (sortOption) {
            FeedSortOption.DISTANCE -> events.sortedWith(
                compareBy<Event> { distanceFromUser(it) }
                    .thenByDescending { it.publishTime }
                    .thenByDescending { it.id }
            )
            FeedSortOption.ENDING_SOON -> events.sortedWith(
                compareBy<Event> { endingSortPriority(it) }
                    .thenBy { endingMinutes(it) }
                    .thenByDescending { it.publishTime }
                    .thenByDescending { it.id }
            )
            FeedSortOption.NEWEST -> events.sortedWith(
                compareByDescending<Event> { it.publishTime }
                    .thenByDescending { it.lastUpdated }
                    .thenByDescending { it.id }
            )
        }
    }

    private fun Event.toFeedEventItem(userLocation: MapCoordinate): FeedEventItem {
        val hostUser = sourceUsersById[publisherId]
        return FeedEventItem(
            item = EventCardItemMapper.fromEvent(
                event = this,
                user = hostUser,
                distanceLabelText = EventCardItemMapper.distanceLabelText(this, userLocation),
                statusText = EventTextFormatter.statusText(this),
                postedText = EventTextFormatter.postedTimeText(publishTime)
            )
        )
    }

    private fun distanceFromUser(event: Event): Double {
        return distanceKm(userLocation, MapCoordinate(event.latitude, event.longitude))
    }

    private fun endingSortPriority(event: Event): Int {
        return when {
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

    private fun prefetchUsers(events: List<Event>) {
        val publisherIds = events.map(Event::publisherId)
        viewModelScope.launch {
            userRepository.ensureUsersLoaded(publisherIds)
        }
    }

    private fun resolveVisiblePublishers(publisherIds: List<String>) {
        if (publisherIds.isEmpty()) {
            resolvingPublisherIds = emptySet()
            return
        }

        val requestedIds = publisherIds.toSet()
        if (requestedIds == resolvingPublisherIds) {
            return
        }

        resolvingPublisherIds = requestedIds
        viewModelScope.launch {
            userRepository.refreshUsersFromRemoteNow(requestedIds)
        }
    }

    private fun isPublisherUnresolved(userId: String): Boolean {
        val user = sourceUsersById[userId] ?: return true
        return user.displayName.isBlank() && user.username.isBlank()
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
