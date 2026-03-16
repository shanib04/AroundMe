package com.colman.aroundme.data

data class Event(
    val id: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val locationName: String,
    val timeRemaining: String,
    val tags: List<String>,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val startsIn: String = "",
    val endsIn: String = ""
)
