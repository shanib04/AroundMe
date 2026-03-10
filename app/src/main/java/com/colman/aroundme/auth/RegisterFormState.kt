package com.colman.aroundme.auth

/**
 * Field-level validation errors for the register form.
 */
data class RegisterFormState(
    val fullNameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val profileImageError: String? = null
) {
    val isValid: Boolean
        get() = fullNameError == null &&
            emailError == null &&
            passwordError == null &&
            confirmPasswordError == null &&
            profileImageError == null
}

