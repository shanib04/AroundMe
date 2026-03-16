package com.colman.aroundme.auth

// Firestore user profile document.
data class UserProfile(
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val imageUrl: String = ""
)

