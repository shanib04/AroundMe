package com.colman.aroundme.data.repository

import android.content.Context
import com.colman.aroundme.data.local.AppLocalDb
import com.colman.aroundme.data.local.dao.EventDao
import com.colman.aroundme.data.remote.FirebaseModel
import com.colman.aroundme.data.model.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

// Repository pattern for Event data
class EventRepository private constructor(
    private val eventDao: EventDao,
    private val firebase: FirebaseModel
) {

    init {
        seedDemoData()
    }

    fun observeAll(): Flow<List<Event>> = eventDao.observeAll()

    // Compatibility method for existing UI code
    fun getEvents(): Flow<List<Event>> = observeAll()

    fun getById(id: String) = eventDao.getById(id)

    // Compatibility method name used by older viewmodels/fragments
    fun getEventById(id: String) = getById(id)

    // Observe events for a specific publisher (LiveData from Room)
    fun observeEventsByPublisher(pubId: String) = eventDao.getEventsByPublisher(pubId)

    suspend fun upsertEvent(event: Event, pushToRemote: Boolean = true) {
        eventDao.insert(event)
        if (pushToRemote) {
            firebase.pushEvent(event)
        }
    }

    suspend fun deleteEvent(id: String) {
        eventDao.deleteById(id)
        // optionally remove from firebase
    }

    suspend fun deleteEventsByPublisher(pubId: String, removeRemote: Boolean = true) {
        eventDao.deleteEventsByPublisher(pubId)
        if (removeRemote) {
            try {
                firebase.deleteUserAndEvents(pubId)
            } catch (_: Exception) {
                // ignore remote failures
            }
        }
    }

    fun syncFromRemote(since: Long = 0L) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val remote = firebase.fetchEventsSince(since)
                for (r in remote) {
                    // insert/replace local record
                    eventDao.insert(r)
                }
            } catch (e: Exception) {
                // log
            }
        }
    }

    private fun seedDemoData() {
        CoroutineScope(Dispatchers.IO).launch {
            val existing = eventDao.observeAll().firstOrNull()
            if (existing.isNullOrEmpty()) {
                val demoEvents = listOf(
                    Event(
                        id = "music_jazz_park",
                        title = "Live Jazz in the Park",
                        description = "A sunset jazz set with local musicians, picnic blankets, and a relaxed crowd right in the city center.",
                        imageUrl = "https://images.unsplash.com/photo-1511192336575-5a79af67a629?auto=format&fit=crop&w=1200&q=80",
                        locationName = "Kefar Sava Park",
                        timeRemaining = "Ends in 2h",
                        tags = listOf("Live", "Music", "Outdoor", "Jazz", "Night"),
                        latitude = 32.1798,
                        longitude = 34.9059,
                        category = "Music"
                    ),
                    Event(
                        id = "food_market_downtown",
                        title = "Downtown Food Market",
                        description = "Street food stalls, coffee carts, and dessert pop-ups with live tastings from local vendors.",
                        imageUrl = "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=1200&q=80",
                        locationName = "Weizman Square",
                        timeRemaining = "Open for 4h",
                        tags = listOf("Food", "Family", "Market", "Dessert", "Street Food"),
                        latitude = 32.1767,
                        longitude = 34.9035,
                        category = "Food"
                    ),
                    Event(
                        id = "art_gallery_night",
                        title = "Open Art Garden",
                        description = "A community art showcase with installations, live sketching, and interactive workshops.",
                        imageUrl = "https://images.unsplash.com/photo-1460661419201-fd4cecdf8a8b?auto=format&fit=crop&w=1200&q=80",
                        locationName = "Municipal Garden",
                        timeRemaining = "Closes in 3h",
                        tags = listOf("Art", "Exhibition", "Creative", "Workshops"),
                        latitude = 32.1811,
                        longitude = 34.9102,
                        category = "Art"
                    ),
                    Event(
                        id = "beer_rooftop_evening",
                        title = "Craft Beer Evening",
                        description = "Small brewery taps, snacks, and an easygoing social vibe with acoustic background music.",
                        imageUrl = "https://images.unsplash.com/photo-1514362545857-3bc16c4c7d1b?auto=format&fit=crop&w=1200&q=80",
                        locationName = "Rooftop Hub",
                        timeRemaining = "Last pours in 90m",
                        tags = listOf("Beer", "Nightlife", "Friends", "Rooftop"),
                        latitude = 32.1749,
                        longitude = 34.9091,
                        category = "Beer"
                    ),
                    Event(
                        id = "sport_morning_run",
                        title = "Community Run Meetup",
                        description = "A casual group run with warm-up stretches and a cool-down coffee stop after the route.",
                        imageUrl = "https://images.unsplash.com/photo-1517836357463-d25dfeac3438?auto=format&fit=crop&w=1200&q=80",
                        locationName = "Kefar Sava Stadium",
                        timeRemaining = "Starts in 25m",
                        tags = listOf("Sport", "Fitness", "Outdoor", "Running"),
                        latitude = 32.1827,
                        longitude = 34.9128,
                        category = "Sport"
                    ),
                    Event(
                        id = "multi_cat_sport_art",
                        title = "Yoga & Painting Retreat",
                        description = "Start with a guided outdoor yoga session, followed by an intuitive canvas painting workshop.",
                        imageUrl = "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?auto=format&fit=crop&w=1200&q=80",
                        locationName = "Kefar Sava Green",
                        timeRemaining = "Starts in 1h",
                        tags = listOf("Workshop", "Sport", "Art", "Wellness"),
                        latitude = 32.1720,
                        longitude = 34.9010,
                        category = "Sport" 
                    ),
                    Event(
                        id = "unknown_gaming_night",
                        title = "Retro Gaming Tournament",
                        description = "Smash Bros, Mario Kart, and retro arcade cabinets. Bring your own controller!",
                        imageUrl = "https://images.unsplash.com/photo-1550745165-9bc0b252726f?auto=format&fit=crop&w=1200&q=80",
                        locationName = "Tech Hub Lounge",
                        timeRemaining = "Ends in 5h",
                        tags = listOf("Gaming", "Retro", "Tournament", "Social"),
                        latitude = 32.1785,
                        longitude = 34.9150,
                        category = "Gaming" 
                    ),
                    Event(
                        id = "unknown_then_music",
                        title = "Silent Disco Reading",
                        description = "Grab a headset, read a book, and when the bell rings, the DJ drops a set.",
                        imageUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=1200&q=80",
                        locationName = "Central Library",
                        timeRemaining = "Starts in 30m",
                        tags = listOf("Reading", "Books", "Music", "Party"),
                        latitude = 32.1840,
                        longitude = 34.9080,
                        category = "Books" 
                    ),
                    Event(
                        id = "jerusalem_market_live",
                        title = "Machane Yehuda Live Market",
                        description = "Live music, food tastings, and evening crowds filling the market with energy.",
                        imageUrl = "https://images.unsplash.com/photo-1482049016688-2d3e1b311543?auto=format&fit=crop&w=1200&q=80",
                        locationName = "Machane Yehuda, Jerusalem",
                        timeRemaining = "Reported 20m ago",
                        tags = listOf("Food", "Live", "Market", "Jerusalem", "Street Food"),
                        latitude = 31.7857,
                        longitude = 35.2137,
                        category = "Food"
                    ),
                    Event(
                        id = "jerusalem_old_city_music",
                        title = "Old City Night Melodies",
                        description = "An intimate live music set with acoustic performances near the Old City walls.",
                        imageUrl = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?auto=format&fit=crop&w=1200&q=80",
                        locationName = "Jaffa Gate, Jerusalem",
                        timeRemaining = "Ends in 3h",
                        tags = listOf("Music", "Live", "Jerusalem", "Night"),
                        latitude = 31.7784,
                        longitude = 35.2271,
                        category = "Music"
                    ),
                    Event(
                        id = "jerusalem_art_station",
                        title = "First Station Art Walk",
                        description = "Pop-up artists, local prints, and creative workshops across the station plaza.",
                        imageUrl = "https://images.unsplash.com/photo-1518998053901-5348d3961a04?auto=format&fit=crop&w=1200&q=80",
                        locationName = "First Station, Jerusalem",
                        timeRemaining = "Open for 5h",
                        tags = listOf("Art", "Creative", "Jerusalem", "Workshops"),
                        latitude = 31.7683,
                        longitude = 35.2256,
                        category = "Art"
                    ),
                    Event(
                        id = "jerusalem_beer_festival",
                        title = "Jerusalem Beer Garden",
                        description = "Craft pours, food trucks, and local DJs at an outdoor beer garden event.",
                        imageUrl = "https://images.unsplash.com/photo-1470337458703-46ad1756a187?auto=format&fit=crop&w=1200&q=80",
                        locationName = "Sacher Park, Jerusalem",
                        timeRemaining = "Last call in 2h",
                        tags = listOf("Beer", "Nightlife", "Jerusalem", "Friends"),
                        latitude = 31.7826,
                        longitude = 35.2104,
                        category = "Beer"
                    ),
                    Event(
                        id = "jerusalem_city_run",
                        title = "Jerusalem Sunrise Run",
                        description = "A scenic community run through the city with hydration points and group warm-up.",
                        imageUrl = "https://images.unsplash.com/photo-1547347298-4074fc3086f0?auto=format&fit=crop&w=1200&q=80",
                        locationName = "Gan Sacher Loop",
                        timeRemaining = "Starts in 40m",
                        tags = listOf("Sport", "Fitness", "Running", "Jerusalem", "Outdoor"),
                        latitude = 31.7833,
                        longitude = 35.2088,
                        category = "Sport"
                    )
                )

                for (event in demoEvents) {
                    eventDao.insert(event)
                }
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: EventRepository? = null

        fun getInstance(context: Context): EventRepository = INSTANCE ?: synchronized(this) {
            val db = AppLocalDb.getInstance(context)
            val repo = EventRepository(db.eventDao(), FirebaseModel.getInstance())
            INSTANCE = repo
            repo
        }
    }
}
