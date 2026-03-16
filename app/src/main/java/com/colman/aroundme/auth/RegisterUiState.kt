package com.colman.aroundme.auth

// UI state for registration actions.
sealed class RegisterUiState {
    data object Idle : RegisterUiState()
    data object Loading : RegisterUiState()
    data class Success(val profile: UserProfile) : RegisterUiState()
    data class Error(val message: String) : RegisterUiState()
}

