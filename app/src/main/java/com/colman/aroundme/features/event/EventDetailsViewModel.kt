package com.colman.aroundme.features.event

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.EventInteraction
import com.colman.aroundme.data.model.EventVoteType
import com.colman.aroundme.data.repository.EventRepository
import kotlinx.coroutines.launch

class EventDetailsViewModel(
    private val repository: EventRepository,
    private val eventId: String
) : ViewModel() {

    val event: LiveData<Event?> = repository.getEventById(eventId).asLiveData()
    val interaction: LiveData<EventInteraction?> = repository.observeInteraction(eventId).asLiveData()
    private val _isSubmittingVote = MutableLiveData(false)
    val isSubmittingVote: LiveData<Boolean> = _isSubmittingVote
    private val _selectedRating = MediatorLiveData<Int>().apply {
        addSource(interaction) { value = it?.rating ?: 0 }
    }
    val selectedRating: LiveData<Int> = _selectedRating
    private val _selectedVoteType = MediatorLiveData<EventVoteType?>().apply {
        addSource(interaction) { value = it?.voteType }
    }
    val selectedVoteType: LiveData<EventVoteType?> = _selectedVoteType

    fun submitVote(voteType: EventVoteType) {
        if (_isSubmittingVote.value == true) return
        _selectedVoteType.value = voteType
        viewModelScope.launch {
            _isSubmittingVote.value = true
            try {
                repository.submitVote(eventId, voteType)
            } finally {
                _isSubmittingVote.value = false
            }
        }
    }

    fun submitRating(rating: Int) {
        val normalized = rating.coerceIn(1, 5)
        _selectedRating.value = normalized
        viewModelScope.launch {
            repository.submitRating(eventId, normalized)
        }
    }

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