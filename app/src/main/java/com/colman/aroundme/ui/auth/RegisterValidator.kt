package com.colman.aroundme.ui.auth

// Legacy validator no longer used. Kept only to satisfy older tests; delegates to ViewModel-style rules.

import android.net.Uri

class RegisterValidator(
    private val strings: RegisterStrings,
    private val emailMatcher: (String) -> Boolean
) {
    fun validate(
        fullName: String,
        email: String,
        password: String,
        confirmPassword: String,
        imageUri: Uri?
    ): RegisterValidationResult {
        // Minimal shim: treat fullName as displayName and synthesize a username
        val displayName = fullName.trim()
        val username = displayName.lowercase().replace("[^a-z0-9_]".toRegex(), "_").take(15)

        val vm = RegisterViewModelShim(strings, emailMatcher)
        return vm.validateInputs(displayName, username, email, password, confirmPassword, imageUri)
    }
}

// Small shim that reuses the same validation logic as RegisterViewModel
private class RegisterViewModelShim(
    private val strings: RegisterStrings,
    private val emailMatcher: (String) -> Boolean
) {
    fun validateInputs(
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

        emailError = when {
            trimmedEmail.isBlank() -> strings.emailRequired
            !emailMatcher(trimmedEmail) -> strings.invalidEmail
            else -> null
        }

        passwordError = when {
            password.isBlank() -> strings.passwordRequired
            password.length < 6 -> strings.passwordTooShort
            else -> null
        }

        confirmPasswordError = when {
            confirmPassword.isBlank() -> strings.confirmPasswordRequired
            password != confirmPassword -> strings.passwordMismatch
            else -> null
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
}
