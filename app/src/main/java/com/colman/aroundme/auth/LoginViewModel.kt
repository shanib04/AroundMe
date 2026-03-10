package com.colman.aroundme.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * ViewModel for the login screen.
 */
class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _loginState = MutableLiveData<AuthResultState>(AuthResultState.Idle)
    val loginState: LiveData<AuthResultState> = _loginState

    fun loginWithEmailAndPassword(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _loginState.value = AuthResultState.Error("Email and password are required.")
            return
        }

        _loginState.value = AuthResultState.Loading
        viewModelScope.launch {
            authRepository.loginWithEmailAndPassword(email.trim(), password)
                .onSuccess { user ->
                    _loginState.value = AuthResultState.Success(user)
                }
                .onFailure { throwable ->
                    _loginState.value = AuthResultState.Error(
                        throwable.localizedMessage ?: "Unable to sign in with email and password."
                    )
                }
        }
    }

    fun loginWithGoogle(idToken: String) {
        if (idToken.isBlank()) {
            _loginState.value = AuthResultState.Error("Google sign-in failed. Missing ID token.")
            return
        }

        _loginState.value = AuthResultState.Loading
        viewModelScope.launch {
            authRepository.loginWithGoogle(idToken)
                .onSuccess { user ->
                    _loginState.value = AuthResultState.Success(user)
                }
                .onFailure { throwable ->
                    _loginState.value = AuthResultState.Error(
                        throwable.localizedMessage ?: "Unable to sign in with Google."
                    )
                }
        }
    }

    fun resetState() {
        _loginState.value = AuthResultState.Idle
    }

    class Factory(
        private val authRepository: AuthRepository = AuthRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
                return LoginViewModel(authRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
