package com.colman.aroundme.features.feed

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.User
import com.colman.aroundme.data.repository.EventRepository
import com.colman.aroundme.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth

sealed interface MyEventRow {
    data class SectionHeader(val title: String) : MyEventRow
    data class EventRow(val item: EventCardItem) : MyEventRow
}

class MyEventsViewModel(
    repository: EventRepository,
    userRepository: UserRepository,
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
        val activeEvents = currentEvents.filterNot { it.isEnded }
        val endedEvents = currentEvents.filter { it.isEnded }
        val rows = mutableListOf<MyEventRow>()

        if (activeEvents.isNotEmpty()) {
            rows += MyEventRow.SectionHeader("Active Events")
            rows += activeEvents.map { event -> eventRowFor(event, "Live", "EDIT EVENT") }
        }
        if (endedEvents.isNotEmpty()) {
            rows += MyEventRow.SectionHeader("Expired Events")
            rows += endedEvents.map { event -> eventRowFor(event, "Ended", "RECREATE EVENT") }
        }
        _events.value = rows
    }

    private fun eventRowFor(event: Event, statusText: String, postedText: String): MyEventRow.EventRow {
        val user = usersById[event.publisherId]
        return MyEventRow.EventRow(
            item = EventCardItemMapper.fromEvent(
                event = event,
                user = user,
                statusText = statusText,
                postedText = postedText
            )
        )
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
}
