package com.colman.aroundme.model

data class Event(
    val id: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val locationName: String,
    val timeRemaining: String,
    val tags: String // comma-separated tags
)
