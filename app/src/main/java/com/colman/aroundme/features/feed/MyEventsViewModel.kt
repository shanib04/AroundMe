package com.colman.aroundme.features.feed

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.utils.MapCoordinate
import com.colman.aroundme.data.model.User
import com.colman.aroundme.data.repository.EventRepository
import com.colman.aroundme.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

sealed interface MyEventRow {
    data class SectionHeader(val title: String) : MyEventRow
    data class EventRow(val item: EventCardItem) : MyEventRow
}

class MyEventsViewModel(
    private val repository: EventRepository,
    private val userRepository: UserRepository,
    auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val currentUserId = auth.currentUser?.uid.orEmpty()
    private val source = repository.observeEventsByPublisher(currentUserId)
    private val allUsers = userRepository.observeAll().asLiveData()
    private val _events = MediatorLiveData<List<MyEventRow>>()
    val events: LiveData<List<MyEventRow>> = _events
    private val _loading = MediatorLiveData(true)
    val loading: LiveData<Boolean> = _loading
    private val _deleteErrorMessage = MediatorLiveData<String?>()
    val deleteErrorMessage: LiveData<String?> = _deleteErrorMessage
    private val _deleteSuccessMessage = MediatorLiveData<String?>()
    val deleteSuccessMessage: LiveData<String?> = _deleteSuccessMessage

    private var currentEvents: List<Event> = emptyList()
    private var usersById: Map<String, User> = emptyMap()
    private var hasLoadedEvents = false
    private var hasLoadedUsers = false

    init {
        _events.addSource(source) { events ->
            hasLoadedEvents = true
            currentEvents = events.sortedWith(
                compareBy<Event> { it.isEnded }
                    .thenByDescending { it.publishTime }
            )
            publishItems()
        }
        _events.addSource(allUsers) { users ->
            hasLoadedUsers = true
            usersById = users.associateBy { it.id }
            publishItems()
        }
        // Ensure events are synced from remote on first load
        viewModelScope.launch {
            runCatching { repository.syncFromRemoteNow(0L) }
        }
    }

    private fun publishItems() {
        prefetchUsers(currentEvents)
        if (!hasLoadedEvents || !hasLoadedUsers) {
            _loading.value = true
            _events.value = emptyList()
            return
        }

        val activeEvents = currentEvents.filterNot { it.isEnded }
        val endedEvents = currentEvents.filter { it.isEnded }
        val rows = mutableListOf<MyEventRow>()

        if (activeEvents.isNotEmpty()) {
            rows += MyEventRow.SectionHeader("Active Events")
            rows += activeEvents.map(::eventRowFor)
        }
        if (endedEvents.isNotEmpty()) {
            rows += MyEventRow.SectionHeader("Expired Events")
            rows += endedEvents.map(::eventRowFor)
        }
        _events.value = rows
        _loading.value = false
    }

    private fun eventRowFor(event: Event): MyEventRow.EventRow {
        val user = usersById[event.publisherId]
        return MyEventRow.EventRow(
            item = EventCardItemMapper.fromEvent(
                event = event,
                user = user,
                distanceLabelText = EventCardItemMapper.distanceLabelText(event, DEFAULT_USER_LOCATION),
                statusText = EventTextFormatter.statusText(event),
                postedText = EventTextFormatter.postedTimeText(event.publishTime)
            )
        )
    }

    private fun prefetchUsers(events: List<Event>) {
        val publisherIds = events.map(Event::publisherId)
        viewModelScope.launch {
            userRepository.ensureUsersLoaded(publisherIds)
            userRepository.refreshUsersFromRemoteNow(publisherIds)
        }
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            runCatching {
                repository.deleteEvent(eventId)
            }.onSuccess {
                _deleteSuccessMessage.value = "Event deleted"
            }.onFailure { error ->
                _deleteErrorMessage.value = error.message ?: "Unable to delete event right now."
            }
        }
    }

    fun onDeleteMessageShown() {
        _deleteErrorMessage.value = null
        _deleteSuccessMessage.value = null
    }

    class Factory(
        private val repository: EventRepository,
        private val userRepository: UserRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MyEventsViewModel(repository, userRepository) as T
        }
    }

    private companion object {
        val DEFAULT_USER_LOCATION = MapCoordinate(32.1782, 34.9076)
    }
}
