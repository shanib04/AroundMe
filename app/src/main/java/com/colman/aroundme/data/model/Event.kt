package com.colman.aroundme.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class Event(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val locationName: String,
    val timeRemaining: String = "",
    val tags: List<String> = emptyList(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val category: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
)
