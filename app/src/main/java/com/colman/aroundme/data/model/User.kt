package com.colman.aroundme.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String = "", // Firebase Auth UID
    val name: String = "",
    val profileImageUrl: String = "",
    val email: String = "",
    val bio: String = "",

    // Gamification & Achievements Fields
    val points: Int = 0,
    val totalPoints: Int = 0,
    val eventsPublishedCount: Int = 0,
    val validationsMadeCount: Int = 0,
    val rankTitle: String = "Newcomer",

    val lastUpdated: Long = System.currentTimeMillis() // For Room Sync
)