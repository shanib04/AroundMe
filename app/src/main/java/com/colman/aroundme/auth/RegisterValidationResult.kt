package com.colman.aroundme.auth

// Validation result for the registration form.
data class RegisterValidationResult(
    val formState: RegisterFormState,
    val sanitizedFullName: String,
    val sanitizedEmail: String,
    val sanitizedPassword: String
)

