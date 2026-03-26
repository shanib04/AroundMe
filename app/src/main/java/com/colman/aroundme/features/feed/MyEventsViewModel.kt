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

data class MyEventItem(
    val item: EventCardItem
)

class MyEventsViewModel(
    repository: EventRepository,
    userRepository: UserRepository,
    auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val currentUserId = auth.currentUser?.uid.orEmpty()
    private val source = repository.observeEventsByPublisher(currentUserId)
    private val allUsers = userRepository.observeAll().asLiveData()
    private val _events = MediatorLiveData<List<MyEventItem>>()
    val events: LiveData<List<MyEventItem>> = _events

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
        _events.value = currentEvents.map { event ->
            val user = usersById[event.publisherId]
            MyEventItem(
                item = EventCardItemMapper.fromEvent(
                    event = event,
                    user = user,
                    statusText = if (event.isEnded) "Ended" else "Live",
                    postedText = if (event.isEnded) "RECREATE EVENT" else "EDIT EVENT"
                )
            )
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
}
