package com.colman.aroundme.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RegisterViewModelTest {

    private lateinit var validator: RegisterValidator

    @Before
    fun setUp() {
        validator = RegisterValidator(
            strings = RegisterStrings(
                fullNameRequired = "Full name is required.",
                emailRequired = "Email is required.",
                invalidEmail = "Please enter a valid email address.",
                passwordRequired = "Password is required.",
                confirmPasswordRequired = "Please confirm your password.",
                passwordTooShort = "Password must be at least 6 characters.",
                passwordMismatch = "Passwords do not match.",
                profilePictureRequired = "Please choose a profile picture.",
                googleTokenMissing = "Google sign-in did not return an ID token.",
                googleSignUpFailed = "Google sign-up failed.",
                googleAccountMissing = "Google account details are missing."
            ),
            emailMatcher = { email -> email.contains("@") && email.contains(".") }
        )
    }

    @Test
    fun `validate returns errors for empty form except optional image`() {
        val result = validator.validate(
            fullName = "",
            email = "",
            password = "",
            confirmPassword = "",
            imageUri = null
        )

        assertEquals("Full name is required.", result.formState.fullNameError)
        assertEquals("Email is required.", result.formState.emailError)
        assertEquals("Password is required.", result.formState.passwordError)
        assertEquals("Please confirm your password.", result.formState.confirmPasswordError)
        assertEquals(null, result.formState.profileImageError)
        assertTrue(!result.formState.isValid)
    }

    @Test
    fun `validate trims values even when image is missing`() {
        val result = validator.validate(
            fullName = "  Jane Doe  ",
            email = "  jane@aroundme.com  ",
            password = "secret1",
            confirmPassword = "secret1",
            imageUri = null
        )

        assertEquals("Jane Doe", result.sanitizedFullName)
        assertEquals("jane@aroundme.com", result.sanitizedEmail)
        assertEquals("secret1", result.sanitizedPassword)
        assertEquals(null, result.formState.profileImageError)
        assertTrue(result.formState.isValid)
    }

    @Test
    fun `validate rejects short password and invalid email`() {
        val result = validator.validate(
            fullName = "Jane",
            email = "bad-email",
            password = "12345",
            confirmPassword = "12345",
            imageUri = null
        )

        assertEquals("Please enter a valid email address.", result.formState.emailError)
        assertEquals("Password must be at least 6 characters.", result.formState.passwordError)
        assertEquals(null, result.formState.profileImageError)
    }

    @Test
    fun `validate rejects mismatched password`() {
        val result = validator.validate(
            fullName = "Jane",
            email = "jane@aroundme.com",
            password = "123456",
            confirmPassword = "654321",
            imageUri = null
        )

        assertEquals("Passwords do not match.", result.formState.confirmPasswordError)
    }
}
