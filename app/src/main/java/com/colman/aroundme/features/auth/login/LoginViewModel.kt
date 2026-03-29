package com.colman.aroundme.features.auth.login

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.colman.aroundme.R
import com.colman.aroundme.data.repository.AuthRepository
import kotlinx.coroutines.launch

class LoginViewModel(
    application: Application,
    private val authRepository: AuthRepository
) : AndroidViewModel(application) {

    private val credentialsRequiredMessage = getString(R.string.error_login_credentials_required)
    private val loginFailedMessage = getString(R.string.error_login_failed)
    private val googleTokenMissingMessage = getString(R.string.error_login_google_token_missing)
    private val googleLoginFailedMessage = getString(R.string.error_login_google_failed)

    private val _loginState = MutableLiveData<AuthResultState>(AuthResultState.Idle)
    val loginState: LiveData<AuthResultState> = _loginState

    fun loginWithIdentifierAndPassword(identifier: String, password: String) {
        if (identifier.isBlank() || password.isBlank()) {
            _loginState.value = AuthResultState.Error(credentialsRequiredMessage)
            return
        }

        _loginState.value = AuthResultState.Loading
        viewModelScope.launch {
            authRepository.loginWithIdentifierAndPassword(identifier.trim(), password)
                .onSuccess { user ->
                    _loginState.value = AuthResultState.Success(user)
                }
                .onFailure { throwable ->
                    _loginState.value = AuthResultState.Error(
                        throwable.localizedMessage ?: loginFailedMessage
                    )
                }
        }
    }

    fun loginWithGoogle(idToken: String) {
        if (idToken.isBlank()) {
            _loginState.value = AuthResultState.Error(googleTokenMissingMessage)
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
                        throwable.localizedMessage ?: googleLoginFailedMessage
                    )
                }
        }
    }

    fun resetState() {
        _loginState.value = AuthResultState.Idle
    }

    private fun getString(resId: Int): String = getApplication<Application>().getString(resId)

    class Factory(
        private val application: Application,
        private val authRepository: AuthRepository = AuthRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
                return LoginViewModel(application, authRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
