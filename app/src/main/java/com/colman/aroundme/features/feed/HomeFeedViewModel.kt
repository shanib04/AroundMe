package com.colman.aroundme.features.feed

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.EventVoteType
import com.colman.aroundme.data.repository.EventRepository
import kotlinx.coroutines.launch

class HomeFeedViewModel(
    private val eventRepository: EventRepository
) : ViewModel() {

    /** Real-time events list comes from Room which is kept in sync by addSnapshotListener. */
    val events: LiveData<List<Event>> = eventRepository.observeAll().asLiveData()

    fun onVoteClicked(eventId: String, voteType: EventVoteType) {
        viewModelScope.launch {
            // Uses same toggle vote transaction used by Details screen.
            eventRepository.submitVote(eventId, voteType)
        }
    }

    class Factory(private val eventRepository: EventRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeFeedViewModel(eventRepository) as T
        }
    }
}

