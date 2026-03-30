package com.colman.aroundme.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume

class IdentityRepository(
    context: Context,
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getUserId(): String {
        val userId = firebaseAuth.currentUser?.uid
        if (!userId.isNullOrBlank()) return userId

        val existing = preferences.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val generated = "device:${UUID.randomUUID()}"
        preferences.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    fun getAuthenticatedUserIdOrNull(): String? {
        return firebaseAuth.currentUser?.uid?.takeIf { it.isNotBlank() }
    }

    suspend fun awaitAuthenticatedUserId(timeoutMs: Long = 1500L): String? {
        getAuthenticatedUserIdOrNull()?.let { return it }

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                lateinit var listener: FirebaseAuth.AuthStateListener
                listener = FirebaseAuth.AuthStateListener { auth ->
                    val authenticatedUserId = auth.currentUser?.uid?.takeIf { it.isNotBlank() }
                    if (authenticatedUserId != null && continuation.isActive) {
                        firebaseAuth.removeAuthStateListener(listener)
                        continuation.resume(authenticatedUserId)
                    }
                }

                firebaseAuth.addAuthStateListener(listener)
                continuation.invokeOnCancellation {
                    firebaseAuth.removeAuthStateListener(listener)
                }
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "aroundme_identity"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
