package com.colman.aroundme.ui.auth

import com.colman.aroundme.data.model.UserProfile

// UI state for the profile screen.
sealed class ProfileUiState {
    data object Loading : ProfileUiState()
    data class Success(val profile: UserProfile) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

