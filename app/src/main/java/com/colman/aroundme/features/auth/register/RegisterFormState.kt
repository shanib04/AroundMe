package com.colman.aroundme.features.auth.register

// Field validation errors for the register form
data class RegisterFormState(
    val displayNameError: String? = null,
    val usernameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val profileImageError: String? = null
) {
    val isValid: Boolean
        get() = displayNameError == null &&
            usernameError == null &&
            emailError == null &&
            passwordError == null &&
            confirmPasswordError == null &&
            profileImageError == null
}
