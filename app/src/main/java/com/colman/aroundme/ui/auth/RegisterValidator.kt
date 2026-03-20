package com.colman.aroundme.ui.auth

import android.net.Uri

// Pure validator for register form fields.
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
        val trimmedName = fullName.trim()
        val trimmedEmail = email.trim()

        val fullNameError = if (trimmedName.isBlank()) strings.fullNameRequired else null
        val emailError = when {
            trimmedEmail.isBlank() -> strings.emailRequired
            !emailMatcher(trimmedEmail) -> strings.invalidEmail
            else -> null
        }
        val passwordError = when {
            password.isBlank() -> strings.passwordRequired
            password.length < 6 -> strings.passwordTooShort
            else -> null
        }
        val confirmPasswordError = when {
            confirmPassword.isBlank() -> strings.confirmPasswordRequired
            password != confirmPassword -> strings.passwordMismatch
            else -> null
        }

        return RegisterValidationResult(
            formState = RegisterFormState(
                fullNameError = fullNameError,
                emailError = emailError,
                passwordError = passwordError,
                confirmPasswordError = confirmPasswordError,
                profileImageError = null
            ),
            sanitizedFullName = trimmedName,
            sanitizedEmail = trimmedEmail,
            sanitizedPassword = password
        )
    }
}

