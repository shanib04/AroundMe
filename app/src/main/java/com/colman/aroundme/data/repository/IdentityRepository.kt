package com.colman.aroundme.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID

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

    companion object {
        private const val PREFS_NAME = "aroundme_identity"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
