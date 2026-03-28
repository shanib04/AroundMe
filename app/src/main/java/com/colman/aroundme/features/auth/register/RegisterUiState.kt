package com.colman.aroundme.features.auth.register

import com.colman.aroundme.data.model.User

// UI state for registration actions
sealed class RegisterUiState {
    data object Idle : RegisterUiState()
    data object Loading : RegisterUiState()
    data class Success(val profile: User) : RegisterUiState()
    data class Error(val message: String) : RegisterUiState()
}
