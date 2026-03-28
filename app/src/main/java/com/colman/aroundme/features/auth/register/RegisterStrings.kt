package com.colman.aroundme.features.auth.register

// String provider for register validation and error states
data class RegisterStrings(
    val displayNameRequired: String,
    val displayNameInvalid: String,
    val usernameRequired: String,
    val usernameInvalid: String,
    val emailRequired: String,
    val invalidEmail: String,
    val passwordRequired: String,
    val confirmPasswordRequired: String,
    val passwordTooShort: String,
    val passwordMismatch: String,
    val profilePictureRequired: String,
    val googleTokenMissing: String,
    val googleSignUpFailed: String,
    val googleAccountMissing: String
)
