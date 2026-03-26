package com.colman.aroundme.features.map

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.MapCoordinate
import com.colman.aroundme.data.repository.EventRepository
import com.colman.aroundme.features.feed.EventTextFormatter
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class MapFeaturedEventItem(
    val event: Event,
    val title: String,
    val locationSummary: String,
    val timeText: String
)

class MapViewModel(
    private val repository: EventRepository,
    initialRadiusKm: Float = DEFAULT_RADIUS_KM
) : ViewModel() {

    private val allEvents = repository.observeAll().asLiveData()

    // Expose a snapshot of all events (unfiltered) for global event search
    fun allEventsSnapshot(): List<Event> = allEvents.value.orEmpty()

    private val _availableFilters = MediatorLiveData<List<String>>()
    val availableFilters: LiveData<List<String>> = _availableFilters

    private val _selectedFilters = MutableLiveData<Set<String>>(emptySet())
    val selectedFilters: LiveData<Set<String>> = _selectedFilters

    private val _radiusKm = MutableLiveData(initialRadiusKm)
    val radiusKm: LiveData<Float> = _radiusKm

    private val _searchCenter = MutableLiveData<MapCoordinate>(DEFAULT_SEARCH_CENTER)
    val searchCenter: LiveData<MapCoordinate> = _searchCenter

    private val _searchLocationLabel = MutableLiveData(DEFAULT_SEARCH_LABEL)
    val searchLocationLabel: LiveData<String> = _searchLocationLabel

    private val _filteredEvents = MediatorLiveData<List<Event>>()
    val filteredEvents: LiveData<List<Event>> = _filteredEvents

    private val _selectedEventId = MutableLiveData<String?>(null)
    private val _selectedEvent = MediatorLiveData<Event?>()
    val selectedEvent: LiveData<Event?> = _selectedEvent

    val selectedEventItem: LiveData<MapFeaturedEventItem?> = selectedEvent.map { event ->
        event?.let {
            val distance = distanceFromCenterKm(it)
            MapFeaturedEventItem(
                event = it,
                title = it.title,
                locationSummary = buildString {
                    append(it.locationName.ifBlank { EventTextFormatter.unknownLocationText() })
                    append(" • ")
                    append(String.format(Locale.US, "%.1fkm away", distance))
                },
                timeText = EventTextFormatter.statusText(it)
            )
        }
    }

    init {
        _availableFilters.addSource(allEvents) { events ->
            _availableFilters.value = events
                .flatMap { event -> listOf(event.category) + event.tags }
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .sortedBy { it.lowercase() }
            updateFilteredState()
        }

        _filteredEvents.addSource(allEvents) { updateFilteredState() }
        _filteredEvents.addSource(_selectedFilters) { updateFilteredState() }
        _filteredEvents.addSource(_radiusKm) { updateFilteredState() }
        _filteredEvents.addSource(_searchCenter) { updateFilteredState() }

        _selectedEvent.addSource(_filteredEvents) { updateSelectedEvent(it) }
        _selectedEvent.addSource(_selectedEventId) { updateSelectedEvent(_filteredEvents.value.orEmpty()) }

        viewModelScope.launch {
            // trigger initial sync from remote
            repository.syncFromRemote(0L)
        }
    }

    fun toggleFilter(filter: String) {
        val updated = _selectedFilters.value.orEmpty().toMutableSet()
        if (!updated.add(filter)) {
            updated.remove(filter)
        }
        _selectedFilters.value = updated
    }

    fun updateRadius(radiusKm: Float) { _radiusKm.value = radiusKm }

    fun updateSearchArea(center: MapCoordinate, locationLabel: String? = null) {
        _searchCenter.value = center
        _searchLocationLabel.value = locationLabel?.takeIf { it.isNotBlank() } ?: formatCoordinate(center)
    }

    fun selectEvent(eventId: String?) { _selectedEventId.value = eventId }

    fun distanceFromCenterKm(event: Event): Double {
        return distanceKm(_searchCenter.value ?: DEFAULT_SEARCH_CENTER,
            MapCoordinate(event.latitude, event.longitude)
        )
    }

    private fun updateFilteredState() {
        val events = allEvents.value.orEmpty()
        val selectedFilters = _selectedFilters.value.orEmpty().map { it.lowercase() }.toSet()
        val center = _searchCenter.value ?: DEFAULT_SEARCH_CENTER
        val radius = _radiusKm.value ?: DEFAULT_RADIUS_KM

        _filteredEvents.value = events.filter { event ->
            val eventFilters = (listOf(event.category) + event.tags)
                .map { it.trim().lowercase() }
                .toSet()
            val matchesFilters = selectedFilters.isEmpty() || selectedFilters.any(eventFilters::contains)
            val withinRadius = distanceKm(center, MapCoordinate(event.latitude, event.longitude)) <= radius
            matchesFilters && withinRadius && !event.isEnded
        }
    }

    private fun updateSelectedEvent(events: List<Event>) {
        val selectedId = _selectedEventId.value
        val selected = events.firstOrNull { it.id == selectedId }
            ?: events.minByOrNull { event ->
                distanceKm(_searchCenter.value ?: DEFAULT_SEARCH_CENTER,
                    MapCoordinate(event.latitude, event.longitude)
                )
            }

        if (_selectedEvent.value != selected) {
            _selectedEvent.value = selected
        }

        if (_selectedEventId.value != selected?.id) {
            _selectedEventId.value = selected?.id
        }
    }

    private fun distanceKm(start: MapCoordinate, end: MapCoordinate): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(end.latitude - start.latitude)
        val dLon = Math.toRadians(end.longitude - start.longitude)
        val lat1 = Math.toRadians(start.latitude)
        val lat2 = Math.toRadians(end.latitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
            sin(dLon / 2) * sin(dLon / 2) * cos(lat1) * cos(lat2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusKm * c
    }

    private fun formatCoordinate(center: MapCoordinate): String {
        return "${"%.4f".format(center.latitude)}, ${"%.4f".format(center.longitude)}"
    }

    class Factory(
        private val repository: EventRepository,
        private val initialRadiusKm: Float = DEFAULT_RADIUS_KM
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MapViewModel(repository, initialRadiusKm) as T
        }
    }

    companion object {
        val KEFAR_SAVA_CENTER = MapCoordinate(32.1782, 34.9076)
        val JERUSALEM_CENTER = MapCoordinate(31.7780, 35.2217)
        val DEFAULT_SEARCH_CENTER = KEFAR_SAVA_CENTER
        const val DEFAULT_SEARCH_LABEL = "Kefar Sava"
        const val DEFAULT_RADIUS_KM = 25f
    }
}