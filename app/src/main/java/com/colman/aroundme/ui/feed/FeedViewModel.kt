package com.colman.aroundme.ui.feed

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.colman.aroundme.data.EventRepository
import com.colman.aroundme.model.Event
import kotlinx.coroutines.launch

class FeedViewModel : ViewModel() {
    val events: LiveData<List<Event>> = EventRepository.getEvents()

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun refresh() {
        // Manage loading state and delegate to repository's suspend refresh
        _isLoading.value = true
        viewModelScope.launch {
            EventRepository.refreshFromRemote()
            _isLoading.value = false
        }
    }
}

