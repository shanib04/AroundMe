package com.colman.aroundme.data.repository

import android.content.Context
import com.colman.aroundme.data.local.AppLocalDb
import com.colman.aroundme.data.local.dao.UserDao
import com.colman.aroundme.data.model.User
import com.colman.aroundme.data.remote.FirebaseModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class UserRepository private constructor(
    private val userDao: UserDao,
    private val firebase: FirebaseModel
) {

    fun observeAll(): Flow<List<User>> = userDao.observeAll()

    fun getUserById(id: String): Flow<User?> = userDao.getUserById(id)

    suspend fun getUserByUsername(username: String): User? = userDao.getUserByUsername(username)

    // Fetch a single user from Firebase and upsert locally (best-effort, non-blocking)
    fun refreshUserFromRemote(id: String) {
        CoroutineScope(Dispatchers.IO).launch {
            refreshUserFromRemoteNow(id)
        }
    }

    suspend fun refreshUserFromRemoteNow(id: String): User? {
        val normalizedUserId = id.trim()
        if (normalizedUserId.isBlank()) return null

        return try {
            val remoteUser = firebase.fetchUserById(normalizedUserId)?.normalizedForDisplay()
            val localUser = userDao.getUserById(normalizedUserId).first()
            when {
                remoteUser == null -> {
                    if (localUser != null) {
                        userDao.deleteById(normalizedUserId)
                    }
                    null
                }

                localUser != remoteUser -> {
                    if (localUser != null) {
                        userDao.deleteById(normalizedUserId)
                    }
                    userDao.insert(remoteUser)
                    remoteUser
                }

                else -> remoteUser
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun refreshUsersFromRemoteNow(ids: Collection<String>) {
        ids.map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .forEach { id -> refreshUserFromRemoteNow(id) }
    }

    suspend fun upsertUser(user: User, pushToRemote: Boolean = true) {
        val normalized = user.normalizedForDisplay()
        userDao.insert(normalized)
        if (pushToRemote) {
            firebase.pushUser(normalized)
        }
    }

    suspend fun updateUserProfile(user: User, pushToRemote: Boolean = true) {
        val normalized = user.normalizedForDisplay()
        userDao.insert(normalized)
        if (pushToRemote) {
            firebase.updateUserProfile(normalized)
        }
    }

    suspend fun updateDerivedStats(
        userId: String,
        eventsPublishedCount: Int,
        validationsMadeCount: Int,
        points: Int
    ) {
        val normalizedUserId = normalizeUserStatsId(userId)
        if (normalizedUserId.isBlank()) return

        val now = System.currentTimeMillis()
        val current = userDao.getUserById(normalizedUserId).first() ?: User(id = normalizedUserId)
        val updated = current.copy(
            points = points,
            eventsPublishedCount = eventsPublishedCount,
            validationsMadeCount = validationsMadeCount,
            lastUpdated = now
        ).normalizedForDisplay()

        userDao.insert(updated)
        runCatching {
            firebase.updateUserDerivedStats(
                userId = normalizedUserId,
                points = points,
                eventsPublishedCount = eventsPublishedCount,
                validationsMadeCount = validationsMadeCount,
                lastUpdated = now
            )
        }
    }

    private fun normalizeUserStatsId(userId: String): String {
        val trimmedId = userId.trim()
        return when {
            trimmedId.startsWith("user:") -> trimmedId.removePrefix("user:")
            else -> trimmedId
        }
    }

    suspend fun isUsernameTakenRemote(username: String, excludingUserId: String? = null): Boolean {
        return try {
            firebase.isUsernameTaken(username, excludingUserId)
        } catch (_: Exception) {
            true
        }
    }

    suspend fun deleteUser(id: String) {
        userDao.deleteById(id)
    }

    suspend fun clearAllLocal() {
        userDao.deleteAll()
    }

    suspend fun syncFromRemoteNow(since: Long = 0L) {
        try {
            val remote = firebase.fetchUsersSince(since)
            for (user in remote) {
                userDao.insert(user.normalizedForDisplay())
            }
        } catch (_: Exception) {
            // Ignore sync errors
        }
    }

    fun syncFromRemote(since: Long = 0L) {
        CoroutineScope(Dispatchers.IO).launch {
            syncFromRemoteNow(since)
        }
    }

    private fun User.normalizedForDisplay(): User {
        val safeDisplayName = displayName
            .takeIf { it.isNotBlank() }
            ?: username.takeIf { it.isNotBlank() }
            ?: com.colman.aroundme.features.feed.EventTextFormatter.unknownPublisherText()
        return copy(displayName = safeDisplayName)
    }

    suspend fun ensureUsersLoaded(ids: Collection<String>) {
        ids.map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .forEach { id ->
                val existing = userDao.getUserById(id).first()
                if (existing == null || existing.displayName.isBlank() || existing.username.isBlank()) {
                    refreshUserFromRemoteNow(id)
                }
            }
    }

    companion object {
        const val EVENT_CREATED_POINTS_AWARD = 10
        const val VALIDATION_POINTS_AWARD = 2

        @Volatile
        private var INSTANCE: UserRepository? = null

        fun getInstance(context: Context): UserRepository = INSTANCE ?: synchronized(this) {
            val db = AppLocalDb.getInstance(context)
            val repo = UserRepository(db.userDao(), FirebaseModel.getInstance())
            INSTANCE = repo
            repo
        }
    }
}
