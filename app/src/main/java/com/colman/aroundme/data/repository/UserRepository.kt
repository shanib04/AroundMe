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

// Repository pattern for User data
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
            try {
                firebase.fetchUserById(id)?.let { userDao.insert(it.normalizedForDisplay()) }
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    suspend fun upsertUser(user: User, pushToRemote: Boolean = true) {
        val normalized = user.normalizedForDisplay()
        userDao.insert(normalized)
        if (pushToRemote) {
            firebase.pushUser(normalized)
        }
    }

    // Check remote Firestore if username is taken (best-effort)
    suspend fun isUsernameTakenRemote(username: String, excludingUserId: String? = null): Boolean {
        return try {
            firebase.isUsernameTaken(username, excludingUserId)
        } catch (_: Exception) {
            // On any error, be conservative and report it may be taken to avoid duplicates
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
            // ignore best-effort sync failures
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
                if (existing == null || existing.displayName.isBlank()) {
                    firebase.fetchUserById(id)?.let { userDao.insert(it.normalizedForDisplay()) }
                }
            }
    }

    companion object {
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
