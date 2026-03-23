package com.colman.aroundme.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class Event(
    @PrimaryKey val id: String = "",
    val publisherId: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val tags: List<String> = listOf(),
    val imageUrl: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val geohash: String = "",
    val locationName: String = "",
    val publishTime: Long = System.currentTimeMillis(),
    val expirationTime: Long = 0L,
    val timeRemaining: String = "",
    val activeVotes: Int = 0,
    val inactiveVotes: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
