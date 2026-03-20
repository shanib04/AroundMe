package com.colman.aroundme.ui.event

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.repository.EventRepository

class EventDetailsViewModel(
    repository: EventRepository,
    eventId: String
) : ViewModel() {

    val event: LiveData<Event?> = repository.getEventById(eventId).asLiveData()

    class Factory(
        private val repository: EventRepository,
        private val eventId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EventDetailsViewModel(repository, eventId) as T
        }
    }
}