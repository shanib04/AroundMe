package com.colman.aroundme.features.create

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.remote.ImageUploader
import com.colman.aroundme.data.repository.EventRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID

sealed class CreateEventUiState {
    data object Idle : CreateEventUiState()
    data object Loading : CreateEventUiState()
    data object Success : CreateEventUiState()
    data class Error(val message: String) : CreateEventUiState()
}

class CreateEventViewModel(
    private val repository: EventRepository
) : ViewModel() {

    private val _uiState = MutableLiveData<CreateEventUiState>(CreateEventUiState.Idle)
    val uiState: LiveData<CreateEventUiState> = _uiState

    // State preservation for Fragment recreation/navigation
    private val _tags = MutableLiveData<List<String>>(emptyList())
    val tags: LiveData<List<String>> = _tags

    private val _selectedImageUri = MutableLiveData<Uri?>(null)
    val selectedImageUri: LiveData<Uri?> = _selectedImageUri

    private val _editingEvent = MutableLiveData<Event?>(null)
    val editingEvent: LiveData<Event?> = _editingEvent

    private val imageUploader = ImageUploader()

    fun setTags(tags: List<String>) {
        _tags.value = tags
    }

    fun setImageUri(uri: Uri?) {
        _selectedImageUri.value = uri
    }

    fun loadEvent(eventId: String) {
        if (eventId.isBlank()) return
        viewModelScope.launch {
            _editingEvent.value = repository.getEventById(eventId).firstOrNull()
            _editingEvent.value?.let { event ->
                _tags.value = event.tags
            }
        }
    }

    fun saveEvent(
        mode: String,
        existingEventId: String?,
        title: String,
        description: String,
        locationName: String,
        latitude: Double,
        longitude: Double,
        geohash: String,
        category: String,
        tags: List<String>,
        publishTime: Long,
        expirationTime: Long,
        imageUri: Uri?
    ) {
        if (mode == "edit" && !existingEventId.isNullOrBlank()) {
            updateEvent(
                eventId = existingEventId,
                title = title,
                description = description,
                locationName = locationName,
                latitude = latitude,
                longitude = longitude,
                geohash = geohash,
                category = category,
                tags = tags,
                publishTime = publishTime,
                expirationTime = expirationTime,
                imageUri = imageUri
            )
        } else {
            createEvent(
                title = title,
                description = description,
                locationName = locationName,
                latitude = latitude,
                longitude = longitude,
                geohash = geohash,
                category = category,
                tags = tags,
                publishTime = publishTime,
                expirationTime = expirationTime,
                imageUri = imageUri,
                sourceEvent = if (mode == "recreate") _editingEvent.value else null
            )
        }
    }

    fun createEvent(
        title: String,
        description: String,
        locationName: String,
        latitude: Double,
        longitude: Double,
        geohash: String,
        category: String,
        tags: List<String>,
        publishTime: Long,
        expirationTime: Long,
        imageUri: Uri?,
        sourceEvent: Event? = null
    ) {
        if (title.isBlank() || locationName.isBlank()) {
            _uiState.value = CreateEventUiState.Error("Title and location are required.")
            return
        }

        _uiState.value = CreateEventUiState.Loading
        viewModelScope.launch {
            try {
                val user = FirebaseAuth.getInstance().currentUser
                if (user == null) {
                    _uiState.value = CreateEventUiState.Error("You must be logged in to create events.")
                    return@launch
                }

                val eventId = UUID.randomUUID().toString()
                var imageUrl = sourceEvent?.imageUrl.orEmpty()

                if (imageUri != null) {
                    imageUrl = imageUploader.upload(imageUri, "events/$eventId.jpg")
                }

                val event = Event(
                    id = eventId,
                    publisherId = user.uid,
                    title = title,
                    description = description,
                    category = category,
                    tags = tags,
                    imageUrl = imageUrl,
                    latitude = latitude,
                    longitude = longitude,
                    geohash = geohash,
                    locationName = locationName,
                    publishTime = publishTime,
                    expirationTime = expirationTime,
                    timeRemaining = "New",
                    activeVotes = 0,
                    inactiveVotes = 0,
                    averageRating = 0.0,
                    ratingCount = 0,
                    lastUpdated = System.currentTimeMillis()
                )

                repository.upsertEvent(event, pushToRemote = true)
                _uiState.value = CreateEventUiState.Success
            } catch (e: Exception) {
                _uiState.value = CreateEventUiState.Error(e.message ?: "Failed to create event")
            }
        }
    }

    private fun updateEvent(
        eventId: String,
        title: String,
        description: String,
        locationName: String,
        latitude: Double,
        longitude: Double,
        geohash: String,
        category: String,
        tags: List<String>,
        publishTime: Long,
        expirationTime: Long,
        imageUri: Uri?
    ) {
        _uiState.value = CreateEventUiState.Loading
        viewModelScope.launch {
            try {
                val existing = repository.getEventById(eventId).firstOrNull()
                    ?: throw IllegalStateException("Event not found")
                val updatedImageUrl = if (imageUri != null) {
                    imageUploader.upload(imageUri, "events/$eventId.jpg")
                } else {
                    existing.imageUrl
                }

                repository.upsertEvent(
                    existing.copy(
                        title = title,
                        description = description,
                        locationName = locationName,
                        latitude = latitude,
                        longitude = longitude,
                        geohash = geohash,
                        category = category,
                        tags = tags,
                        imageUrl = updatedImageUrl,
                        publishTime = publishTime,
                        expirationTime = expirationTime,
                        lastUpdated = System.currentTimeMillis()
                    ),
                    pushToRemote = true
                )
                _uiState.value = CreateEventUiState.Success
            } catch (e: Exception) {
                _uiState.value = CreateEventUiState.Error(e.message ?: "Failed to update event")
            }
        }
    }

    class Factory(private val repository: EventRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CreateEventViewModel(repository) as T
        }
    }
}
