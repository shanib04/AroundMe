package com.colman.aroundme.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String = "", // Firebase Auth UID
    val username: String = "",
    val displayName: String = "",
    val profileImageUrl: String = "",
    val email: String = "",
    val achievements: List<String> = emptyList(),
    val discoveryRadiusKm: Int = 15,
    val points: Int = 0,
    val eventsPublishedCount: Int = 0,
    val validationsMadeCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis() // For Room Sync
)