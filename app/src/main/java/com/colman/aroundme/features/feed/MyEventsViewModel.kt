package com.colman.aroundme.features.feed

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.repository.EventRepository
import com.google.firebase.auth.FirebaseAuth

class MyEventsViewModel(
    repository: EventRepository,
    auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val currentUserId = auth.currentUser?.uid.orEmpty()
    private val source = repository.observeEventsByPublisher(currentUserId)
    private val _events = MediatorLiveData<List<Event>>()
    val events: LiveData<List<Event>> = _events

    init {
        _events.addSource(source) { events ->
            _events.value = events.sortedWith(
                compareBy<Event> { it.isEnded }
                    .thenByDescending { it.publishTime }
            )
        }
    }

    class Factory(
        private val repository: EventRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MyEventsViewModel(repository) as T
        }
    }
}
