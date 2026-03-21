package com.colman.aroundme.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class Event(
    @PrimaryKey val id: String = "", // Firestore Document ID
    val publisherId: String = "",    // Links to User.id
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val tags: List<String> = listOf(), // e.g., ["Food", "Music"]
    val imageUrl: String = "",

    // Location Data (Lecture 11)
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val geohash: String = "",        // Crucial for efficient map querying
    val locationName: String = "",

    val publishTime: Long = System.currentTimeMillis(),
    val expirationTime: Long = 0L,
    val timeRemaining: String = "",

    // Community Validation
    val activeVotes: Int = 0,        // Users who said "Still happening"
    val inactiveVotes: Int = 0,      // Users who said "Ended"

    val lastUpdated: Long = System.currentTimeMillis() // For Room Sync
)