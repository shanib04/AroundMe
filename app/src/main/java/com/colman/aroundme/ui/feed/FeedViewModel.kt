package com.colman.aroundme.ui.feed

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.repository.EventRepository
import com.colman.aroundme.ui.feed.components.EventCardUiModel
import com.colman.aroundme.ui.feed.components.toEventCardUiModel

class FeedViewModel(
    repository: EventRepository
) : ViewModel() {

    val featuredEvent: LiveData<Event?> = repository.observeAll()
        .asLiveData()
        .map { events -> events.firstOrNull() }

    val featuredEventCard: LiveData<EventCardUiModel?> = featuredEvent.map { event ->
        event?.toEventCardUiModel()
    }

    class Factory(
        private val repository: EventRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FeedViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return FeedViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

