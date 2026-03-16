package com.colman.aroundme.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class EventRepository {

    private val events = listOf(
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

    fun getEvents(): Flow<List<Event>> = flowOf(events)

    fun getEventById(eventId: String): Flow<Event?> = flowOf(events.firstOrNull { it.id == eventId })
}
