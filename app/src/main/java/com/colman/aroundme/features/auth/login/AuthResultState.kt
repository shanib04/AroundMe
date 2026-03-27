package com.colman.aroundme.features.auth.login

import com.google.firebase.auth.FirebaseUser

// Represents the UI state for authentication requests
sealed class AuthResultState {
    data object Idle : AuthResultState()
    data object Loading : AuthResultState()
    data class Success(val user: FirebaseUser) : AuthResultState()
    data class Error(val message: String) : AuthResultState()
}