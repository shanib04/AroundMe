package com.colman.aroundme.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.colman.aroundme.model.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Simple in-memory repository that simulates local Room cache and remote Firestore sync.
 * Behavior:
 *  - Immediately exposes the local cached events (simulate Room).
 *  - On refresh(), simulates fetching new remote data (delay) and updates the cache.
 */
object EventRepository {

    // Backing LiveData for events
    private val _events = MutableLiveData<List<Event>>()

    init {
        // Seed local cache immediately
        _events.value = seedLocalEvents()
        // Simulate background delta sync (non-blocking) - not started here; refresh() triggers
    }

    fun getEvents(): LiveData<List<Event>> = _events

    suspend fun refreshFromRemote() {
        // Simulate network delay and fetching delta updates
        withContext(Dispatchers.IO) {
            delay(900) // simulate network latency
            val current = _events.value ?: seedLocalEvents()
            // Simulate a remote update: add a new event or modify one
            val updated = current.toMutableList()
            updated.add(
                Event(
                    id = (current.size + 1).toString(),
                    title = "Evening Farmers Market",
                    description = "Fresh produce and local vendors every weekend.",
                    imageUrl = "",
                    locationName = "Westside Green",
                    timeRemaining = "Today",
                    tags = "market, local, food"
                )
            )
            // Save to 'local' cache
            _events.postValue(updated)
        }
    }

    private fun seedLocalEvents(): List<Event> {
        return listOf(
            Event(
                id = "1",
                title = "Street Food Fair",
                description = "A gathering of the city's best food trucks and vendors. Live music and family-friendly activities.",
                imageUrl = "",
                locationName = "Central Park Plaza",
                timeRemaining = "Ends in 3 hrs",
                tags = "food, family, outdoor"
            ),
            Event(
                id = "2",
                title = "Live Jazz Night",
                description = "Experience local jazz bands with a cozy atmosphere. Limited seats, arrive early.",
                imageUrl = "",
                locationName = "Riverside Bar",
                timeRemaining = "Starts in 5 hrs",
                tags = "music, live, jazz"
            ),
            Event(
                id = "3",
                title = "Community Bike Ride",
                description = "Join a guided bike ride through scenic city routes. Helmets required.",
                imageUrl = "",
                locationName = "Harbor Trail",
                timeRemaining = "Tomorrow",
                tags = "outdoors, fitness, community"
            ),
            Event(
                id = "4",
                title = "Open Air Cinema",
                description = "Enjoy popular films under the stars. Bring a blanket and snacks.",
                imageUrl = "",
                locationName = "Rooftop Gardens",
                timeRemaining = "Ends in 6 hrs",
                tags = "movies, outdoor, family"
            ),
            Event(
                id = "5",
                title = "Pop-up Art Market",
                description = "Local artists display and sell their work. Perfect for unique gifts.",
                imageUrl = "",
                locationName = "Old Town Square",
                timeRemaining = "Today",
                tags = "art, market, local"
            )
        )
    }
}

