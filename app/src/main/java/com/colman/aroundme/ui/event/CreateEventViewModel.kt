package com.colman.aroundme.ui.event

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.repository.EventRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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

    fun setTags(tags: List<String>) {
        _tags.value = tags
    }

    fun setImageUri(uri: Uri?) {
        _selectedImageUri.value = uri
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
        imageUri: Uri?
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
                var imageUrl = ""

                // Upload image if selected
                if (imageUri != null) {
                    // Fix: Ensure we use a clean path and check permissions if needed, 
                    // but usually 403 in Firebase Storage means rules are too strict.
                    val storageRef = FirebaseStorage.getInstance().reference.child("events/$eventId.jpg")
                    storageRef.putFile(imageUri).await()
                    imageUrl = storageRef.downloadUrl.await().toString()
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
                    lastUpdated = System.currentTimeMillis()
                )

                repository.upsertEvent(event, pushToRemote = true)
                _uiState.value = CreateEventUiState.Success
            } catch (e: Exception) {
                _uiState.value = CreateEventUiState.Error(e.message ?: "Failed to create event")
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
