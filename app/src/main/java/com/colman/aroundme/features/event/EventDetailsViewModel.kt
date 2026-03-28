package com.colman.aroundme.features.event

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.EventVoteType
import com.colman.aroundme.data.model.NearbyPlace
import com.colman.aroundme.data.model.User
import com.colman.aroundme.data.remote.FirebaseModel
import com.colman.aroundme.data.remote.places.Place
import com.colman.aroundme.data.repository.EventRepository
import com.colman.aroundme.data.repository.PlacesRepositoryImpl
import com.colman.aroundme.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class EventDetailsViewModel(
    private val eventId: String,
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
    private val placesRepository: PlacesRepositoryImpl,
    private val firebaseModel: FirebaseModel
) : ViewModel() {

    enum class EssentialsType(val placesType: String) {
        PARKING("parking"),
        FOOD("restaurant"),
        GAS("gas_station")
    }

    private val placesCache = mutableMapOf<String, List<NearbyPlace>>()

    private val _event = MutableLiveData<Event?>()
    val event: LiveData<Event?> = _event

    private val _publisher = MutableLiveData<User?>()
    val publisher: LiveData<User?> = _publisher

    private val _isSubmittingVote = MutableLiveData(false)
    val isSubmittingVote: LiveData<Boolean> = _isSubmittingVote

    private val _nearbyLoading = MutableLiveData(false)
    val nearbyLoading: LiveData<Boolean> = _nearbyLoading

    private val _nearbyPlaces = MutableLiveData<List<NearbyPlace>>(emptyList())
    val nearbyPlaces: LiveData<List<NearbyPlace>> = _nearbyPlaces

    private val _nearbyError = MutableLiveData<String?>(null)
    val nearbyError: LiveData<String?> = _nearbyError

    private val _selectedEssentialsType = MutableLiveData(EssentialsType.PARKING)
    val selectedEssentialsType: LiveData<EssentialsType> = _selectedEssentialsType

    private val _myRating = MutableLiveData<Int?>(null)
    val myRating: LiveData<Int?> = _myRating

    private val _selectedVoteType = MutableLiveData<EventVoteType?>(null)
    val selectedVoteType: LiveData<EventVoteType?> = _selectedVoteType

    private val _isSubmittingRating = MutableLiveData(false)
    val isSubmittingRating: LiveData<Boolean> = _isSubmittingRating

    private var eventJob: Job? = null

    init {
        observeEventRealtime()
        observeMyInteraction()
    }

    private fun observeMyInteraction() {
        // Keep myRating in sync with local interactions, so it survives navigation.
        viewModelScope.launch {
            eventRepository.observeInteraction(eventId).collectLatest { interaction ->
                _myRating.value = interaction?.rating?.takeIf { it > 0 }
                _selectedVoteType.value = interaction?.voteType
            }
        }
    }

    private fun observeEventRealtime() {
        eventJob?.cancel()
        eventJob = viewModelScope.launch {
            eventRepository.getEventDetails(eventId).collectLatest { e ->
                _event.value = e
                e?.let { ev ->
                    // Load publisher (Room first, fallback remote)
                    val roomUser = userRepository.getUserById(ev.publisherId).firstOrNull()
                    _publisher.value = roomUser ?: firebaseModel.fetchUserById(ev.publisherId)

                    if (ev.latitude != 0.0 && ev.longitude != 0.0) {
                        loadNearby(_selectedEssentialsType.value ?: EssentialsType.PARKING)
                    }
                }
            }
        }
    }

    fun submitVote(voteType: EventVoteType) {
        if (_isSubmittingVote.value == true) return
        viewModelScope.launch {
            _isSubmittingVote.value = true
            try {
                eventRepository.submitVote(eventId, voteType)
            } finally {
                _isSubmittingVote.value = false
            }
        }
    }

    /** rating passed from UI RatingBar (can be 0.5 steps). */
    fun submitRating(rating: Double) {
        if (_isSubmittingRating.value == true) return
        if (rating !in 1.0..5.0) return

        val normalizedRating = rating.roundToInt().coerceIn(1, 5)

        // optimistic UI
        _myRating.value = normalizedRating

        viewModelScope.launch {
            _isSubmittingRating.value = true
            try {
                eventRepository.submitRating(eventId, normalizedRating)
                // observeMyInteraction() will update _myRating from DB, but keep it consistent here too.
                _myRating.value = normalizedRating
            } finally {
                _isSubmittingRating.value = false
            }
        }
    }

    fun loadNearby(type: EssentialsType) {
        val currentEvent = _event.value ?: return
        if (currentEvent.latitude == 0.0 && currentEvent.longitude == 0.0) return

        _selectedEssentialsType.value = type

        val cached = placesCache[type.placesType]
        if (!cached.isNullOrEmpty()) {
            _nearbyPlaces.value = cached
            _nearbyError.value = null
            _nearbyLoading.value = false
            return
        }

        _nearbyLoading.value = true
        _nearbyError.value = null

        viewModelScope.launch {
            val result = placesRepository.searchNearby(
                latitude = currentEvent.latitude,
                longitude = currentEvent.longitude,
                radiusMeters = 1000,
                type = type.placesType
            )

            result
                .onSuccess { places ->
                    val mappedPlaces = places.mapNotNull(::toNearbyPlace)
                    placesCache[type.placesType] = mappedPlaces
                    _nearbyPlaces.value = mappedPlaces
                }
                .onFailure { e ->
                    _nearbyPlaces.value = emptyList()
                    _nearbyError.value = e.message
                }

            _nearbyLoading.value = false
        }
    }

    private fun toNearbyPlace(place: Place): NearbyPlace? {
        val name = place.name?.trim().orEmpty()
        val vicinity = place.vicinity?.trim().orEmpty()
        if (name.isBlank()) return null

        return NearbyPlace(
            name = name,
            vicinity = vicinity,
            iconUrl = place.icon.orEmpty(),
            photoUrl = null,
            rating = place.rating,
            ratingsTotal = place.userRatingsTotal,
            placeId = place.placeId
        )
    }

    class Factory(
        private val eventId: String,
        private val eventRepository: EventRepository,
        private val userRepository: UserRepository,
        private val placesRepository: PlacesRepositoryImpl,
        private val firebaseModel: FirebaseModel
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EventDetailsViewModel(
                eventId = eventId,
                eventRepository = eventRepository,
                userRepository = userRepository,
                placesRepository = placesRepository,
                firebaseModel = firebaseModel,
            ) as T
        }
    }
}