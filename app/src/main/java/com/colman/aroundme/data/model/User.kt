package com.colman.aroundme.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String,
    val fullName: String,
    val email: String,
    val imageUrl: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)
