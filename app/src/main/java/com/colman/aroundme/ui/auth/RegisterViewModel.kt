package com.colman.aroundme.ui.auth

import android.app.Application
import android.net.Uri
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.colman.aroundme.R
import com.colman.aroundme.data.repository.AuthRepository
import kotlinx.coroutines.launch

// ViewModel for the register screen.
class RegisterViewModel(
    application: Application,
    private val authRepository: AuthRepository
) : AndroidViewModel(application) {

    private val strings = RegisterStrings(
        fullNameRequired = getString(R.string.error_full_name_required),
        emailRequired = getString(R.string.error_email_required),
        invalidEmail = getString(R.string.invalid_email),
        passwordRequired = getString(R.string.error_password_required),
        confirmPasswordRequired = getString(R.string.error_confirm_password_required),
        passwordTooShort = getString(R.string.error_password_length),
        passwordMismatch = getString(R.string.error_password_mismatch),
        profilePictureRequired = getString(R.string.error_profile_picture_required),
        googleTokenMissing = getString(R.string.google_sign_in_token_missing),
        googleSignUpFailed = getString(R.string.google_sign_up_failed),
        googleAccountMissing = getString(R.string.error_google_account_missing)
    )

    private val _registerState = MutableLiveData<RegisterUiState>(RegisterUiState.Idle)
    val registerState: LiveData<RegisterUiState> = _registerState

    private val _formState = MutableLiveData(RegisterFormState())
    val formState: LiveData<RegisterFormState> = _formState

    fun register(
        displayName: String,
        username: String,
        email: String,
        password: String,
        confirmPassword: String,
        imageUri: Uri?
    ) {
        val validationResult = validateInputs(displayName, username, email, password, confirmPassword, imageUri)
        _formState.value = validationResult.formState
        if (!validationResult.formState.isValid) {
            return
        }

        _registerState.value = RegisterUiState.Loading
        viewModelScope.launch {
            authRepository.registerWithEmailAndPassword(
                validationResult.sanitizedDisplayName,
                validationResult.sanitizedUsername,
                validationResult.sanitizedEmail,
                validationResult.sanitizedPassword,
                imageUri
            ).onSuccess { profile ->
                _registerState.value = RegisterUiState.Success(profile)
            }.onFailure { throwable ->
                _registerState.value = RegisterUiState.Error(
                    throwable.localizedMessage ?: strings.googleSignUpFailed
                )
            }
        }
    }

    fun registerWithGoogle(idToken: String) {
        if (idToken.isBlank()) {
            _registerState.value = RegisterUiState.Error(strings.googleTokenMissing)
            return
        }

        _registerState.value = RegisterUiState.Loading
        viewModelScope.launch {
            authRepository.signInWithGoogleAndSyncProfile(idToken)
                .onSuccess { profile ->
                    if (profile.email.isBlank()) {
                        _registerState.value = RegisterUiState.Error(strings.googleAccountMissing)
                    } else {
                        _registerState.value = RegisterUiState.Success(profile)
                    }
                }
                .onFailure { throwable ->
                    _registerState.value = RegisterUiState.Error(
                        throwable.localizedMessage ?: strings.googleSignUpFailed
                    )
                }
        }
    }

    fun clearFieldErrors() {
        _formState.value = RegisterFormState()
    }

    fun resetState() {
        _registerState.value = RegisterUiState.Idle
    }

    internal fun validateInputs(
        displayName: String,
        username: String,
        email: String,
        password: String,
        confirmPassword: String,
        imageUri: Uri?
    ): RegisterValidationResult {
        val trimmedDisplayName = displayName.trim()
        val trimmedUsername = username.trim().lowercase()
        val trimmedEmail = email.trim()

        var displayNameError: String? = null
        var usernameError: String? = null
        var emailError: String? = null
        var passwordError: String? = null
        var confirmPasswordError: String? = null
        var profileImageError: String? = null

        val usernameRegex = Regex("^[a-z0-9_]{3,15}$")
        val displayNameRegex = Regex("^[a-zA-Z0-9_\\- ]{1,20}$")

        if (trimmedDisplayName.isBlank()) {
            displayNameError = "Display name is required."
        } else if (!displayNameRegex.matches(trimmedDisplayName)) {
            displayNameError = "Display name can contain letters, numbers, spaces, '-' and '_' (max 20)."
        }

        if (trimmedUsername.isBlank()) {
            usernameError = "Username is required."
        } else if (!usernameRegex.matches(trimmedUsername)) {
            usernameError = "Username can only contain lowercase letters, numbers, and underscores (3-15 chars)."
        }

        if (trimmedEmail.isBlank()) {
            emailError = strings.emailRequired
        } else if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            emailError = strings.invalidEmail
        }

        if (password.isBlank()) {
            passwordError = strings.passwordRequired
        } else if (password.length < 6) {
            passwordError = strings.passwordTooShort
        }

        if (confirmPassword.isBlank()) {
            confirmPasswordError = strings.confirmPasswordRequired
        } else if (password != confirmPassword) {
            confirmPasswordError = strings.passwordMismatch
        }

        // Profile image is now optional: no error if null
        if (imageUri == null) {
            profileImageError = null
        }

        val formState = RegisterFormState(
            displayNameError = displayNameError,
            usernameError = usernameError,
            emailError = emailError,
            passwordError = passwordError,
            confirmPasswordError = confirmPasswordError,
            profileImageError = profileImageError
        )

        return RegisterValidationResult(
            formState = formState,
            sanitizedDisplayName = trimmedDisplayName,
            sanitizedUsername = trimmedUsername,
            sanitizedEmail = trimmedEmail,
            sanitizedPassword = password
        )
    }

    private fun getString(resId: Int): String = getApplication<Application>().getString(resId)

    class Factory(
        private val application: Application,
        private val authRepository: AuthRepository = AuthRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RegisterViewModel::class.java)) {
                return RegisterViewModel(application, authRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
