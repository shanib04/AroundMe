package com.colman.aroundme.features.feed

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.MapCoordinate
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
    repository: EventRepository,
    private val userRepository: UserRepository,
    auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val currentUserId = auth.currentUser?.uid.orEmpty()
    private val source = repository.observeEventsByPublisher(currentUserId)
    private val allUsers = userRepository.observeAll().asLiveData()
    private val _events = MediatorLiveData<List<MyEventRow>>()
    val events: LiveData<List<MyEventRow>> = _events

    private var currentEvents: List<Event> = emptyList()
    private var usersById: Map<String, User> = emptyMap()

    init {
        _events.addSource(source) { events ->
            currentEvents = events.sortedWith(
                compareBy<Event> { it.isEnded }
                    .thenByDescending { it.publishTime }
            )
            publishItems()
        }
        _events.addSource(allUsers) { users ->
            usersById = users.associateBy { it.id }
            publishItems()
        }
    }

    private fun publishItems() {
        prefetchUsers(currentEvents)
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
        }
        publisherIds.forEach { publisherId ->
            val user = usersById[publisherId]
            if (publisherId.isNotBlank() && (user == null || user.displayName.isBlank())) {
                userRepository.refreshUserFromRemote(publisherId)
            }
        }
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
