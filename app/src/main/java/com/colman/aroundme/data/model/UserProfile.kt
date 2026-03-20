package com.colman.aroundme.data.model

// Firestore user profile document.
data class UserProfile(
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val imageUrl: String = ""
)

