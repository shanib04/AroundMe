package com.colman.aroundme.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * ViewModel for loading the current signed-in user's profile.
 */
class ProfileViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _profileState = MutableLiveData<ProfileUiState>(ProfileUiState.Loading)
    val profileState: LiveData<ProfileUiState> = _profileState

    fun loadProfile() {
        _profileState.value = ProfileUiState.Loading
        viewModelScope.launch {
            authRepository.getCurrentUserProfile()
                .onSuccess { profile ->
                    _profileState.value = ProfileUiState.Success(profile)
                }
                .onFailure { throwable ->
                    _profileState.value = ProfileUiState.Error(
                        throwable.localizedMessage ?: "Could not load profile details."
                    )
                }
        }
    }

    class Factory(
        private val authRepository: AuthRepository = AuthRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
                return ProfileViewModel(authRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

