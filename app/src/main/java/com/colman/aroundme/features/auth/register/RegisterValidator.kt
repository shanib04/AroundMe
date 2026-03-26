package com.colman.aroundme.features.auth.register


import android.net.Uri

class RegisterValidator(
    private val strings: RegisterStrings,
    private val emailMatcher: (String) -> Boolean
) {
    fun validate(
        displayName: String,
        email: String,
        password: String,
        confirmPassword: String,
        @Suppress("UNUSED_PARAMETER") imageUri: Uri?
    ): RegisterValidationResult {
        val safeDisplayName = displayName.trim()
        val username = safeDisplayName
            .lowercase()
            .replace("[^a-z0-9_]".toRegex(), "_")
            .take(15)

        val helper = RegisterValidationHelper(strings, emailMatcher)
        return helper.validateInputs(safeDisplayName, username, email, password, confirmPassword)
    }
}

// Small helper that reuses the same validation logic as RegisterViewModel
private class RegisterValidationHelper(
    private val strings: RegisterStrings,
    private val emailMatcher: (String) -> Boolean
) {
    fun validateInputs(
        displayName: String,
        username: String,
        email: String,
        password: String,
        confirmPassword: String
    ): RegisterValidationResult {
        val trimmedDisplayName = displayName.trim()
        val trimmedUsername = username.trim().lowercase()
        val trimmedEmail = email.trim()

        var displayNameError: String? = null
        var usernameError: String? = null
        var emailError: String? = null
        var passwordError: String? = null
        var confirmPasswordError: String? = null
        val profileImageError: String? = null

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
