package com.colman.aroundme.features.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.User
import com.colman.aroundme.utils.MapCoordinate
import com.colman.aroundme.utils.distanceKm
import com.colman.aroundme.data.repository.EventRepository
import com.colman.aroundme.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val DEFAULT_FEED_LOCATION_LABEL = "Kefar Sava"

data class FeedEventItem(
    val item: EventCardItem
)

data class FeedUiState(
    val items: List<FeedEventItem> = emptyList(),
    val sortType: SortType = SortType.Distance,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val emptyMessage: String = "",
    val userLocationLabel: String = DEFAULT_FEED_LOCATION_LABEL,
    val scrollToTopToken: Long = 0L
)

enum class SortType(val label: String) {
    Distance("Distance"),
    Date("Date"),
    Rating("Rating")
}

private data class PaginationState(val pageSize: Int, val isLoadingMore: Boolean)

class FeedViewModel(
    private val repository: EventRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _sortType = MutableStateFlow<SortType>(SortType.Distance)
    private val _userLocation = MutableStateFlow(DEFAULT_USER_LOCATION)
    private val _pagination = MutableStateFlow(PaginationState(PAGE_SIZE, false))
    private val _initialFetchDone = MutableStateFlow(false)

    private var userLocationLabel = DEFAULT_FEED_LOCATION_LABEL
    private var noMoreRemotePages = false
    private var scrollToTopToken: Long = 0L
    private var resolvingPublisherIds: Set<String> = emptySet()

    val uiState: StateFlow<FeedUiState> = combine(
        repository.observeAll(),
        userRepository.observeAll(),
        _sortType,
        _userLocation,
        _pagination,
    ) { events, users, sortType, location, pagination ->
        val activeEvents = events.filterNot { it.isEnded }
        val usersById = users.associateBy { it.id }
        val sorted = sortEvents(activeEvents, sortType, location)
        val visibleCount = pagination.pageSize.coerceAtMost(sorted.size.coerceAtLeast(PAGE_SIZE))
        val visible = sorted.take(visibleCount)

        prefetchUnknownPublishers(visible, usersById)

        val items = visible.map { event ->
            FeedEventItem(
                item = EventCardItemMapper.fromEvent(
                    event = event,
                    user = usersById[event.publisherId],
                    distanceLabelText = EventCardItemMapper.distanceLabelText(event, location),
                    statusText = EventTextFormatter.statusText(event),
                    postedText = EventTextFormatter.postedTimeText(event.publishTime)
                )
            )
        }

        FeedUiState(
            items = items,
            sortType = sortType,
            isInitialLoading = false,
            isLoadingMore = pagination.isLoadingMore,
            hasMore = visibleCount < sorted.size || !noMoreRemotePages,
            emptyMessage = if (sorted.isEmpty()) "No events to show yet." else "",
            userLocationLabel = userLocationLabel,
            scrollToTopToken = scrollToTopToken
        )
    }.combine(_initialFetchDone) { state, done ->
        if (!done && state.items.isEmpty()) {
            state.copy(isInitialLoading = true, emptyMessage = "")
        } else {
            state
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FeedUiState())

    init {
        repository.startEventsRealtimeSync()
        viewModelScope.launch {
            val fetched = repository.fetchNextPage(PAGE_SIZE)
            if (fetched < PAGE_SIZE) noMoreRemotePages = true
            _initialFetchDone.value = true
        }
    }

    fun setSortType(sortType: SortType) {
        scrollToTopToken = System.currentTimeMillis()
        _pagination.value = PaginationState(PAGE_SIZE, false)
        _sortType.value = sortType
    }

    fun loadMoreEvents() {
        val current = _pagination.value
        if (current.isLoadingMore) return

        _pagination.value = PaginationState(current.pageSize + PAGE_SIZE, true)

        viewModelScope.launch {
            val oldestTimestamp = repository.observeAll().first()
                .minOfOrNull { it.lastUpdated }
            val fetched = repository.fetchNextPage(PAGE_SIZE, oldestTimestamp)
            if (fetched == 0) noMoreRemotePages = true
            _pagination.value = _pagination.value.copy(isLoadingMore = false)
        }
    }

    fun updateUserLocation(location: MapCoordinate, label: String = DEFAULT_FEED_LOCATION_LABEL) {
        userLocationLabel = label.ifBlank { DEFAULT_FEED_LOCATION_LABEL }
        _userLocation.value = location
    }

    private fun sortEvents(events: List<Event>, sortType: SortType, location: MapCoordinate): List<Event> {
        return when (sortType) {
            SortType.Distance -> events.sortedWith(
                compareBy<Event> { distanceKm(location, MapCoordinate(it.latitude, it.longitude)) }
                    .thenByDescending { it.publishTime }
                    .thenByDescending { it.id }
            )
            SortType.Date -> events.sortedWith(
                compareByDescending<Event> { it.publishTime }
                    .thenByDescending { it.lastUpdated }
                    .thenByDescending { it.id }
            )
            SortType.Rating -> events.sortedWith(
                compareByDescending<Event> { it.averageRating }
                    .thenByDescending { it.ratingCount }
                    .thenByDescending { it.publishTime }
            )
        }
    }

    private fun prefetchUnknownPublishers(
        events: List<Event>,
        usersById: Map<String, User>
    ) {
        val unknownIds = events
            .map(Event::publisherId)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .filter { id ->
                val user = usersById[id]
                user == null || (user.displayName.isBlank() && user.username.isBlank())
            }
            .toSet()

        if (unknownIds.isEmpty() || unknownIds == resolvingPublisherIds) return
        resolvingPublisherIds = unknownIds

        viewModelScope.launch {
            userRepository.ensureUsersLoaded(unknownIds)
            userRepository.refreshUsersFromRemoteNow(unknownIds)
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
