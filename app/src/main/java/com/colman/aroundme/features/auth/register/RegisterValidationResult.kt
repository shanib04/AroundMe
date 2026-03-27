package com.colman.aroundme.features.auth.register

// Validation result for the registration form.
data class RegisterValidationResult(
    val formState: RegisterFormState,
    val sanitizedDisplayName: String,
    val sanitizedUsername: String,
    val sanitizedEmail: String,
    val sanitizedPassword: String
)
