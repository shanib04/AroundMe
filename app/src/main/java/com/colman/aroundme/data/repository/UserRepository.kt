package com.colman.aroundme.data.repository

import android.content.Context
import com.colman.aroundme.data.local.AppLocalDb
import com.colman.aroundme.data.local.dao.UserDao
import com.colman.aroundme.data.remote.FirebaseModel
import com.colman.aroundme.data.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// Repository pattern for User data
class UserRepository private constructor(
    private val userDao: UserDao,
    private val firebase: FirebaseModel
) {

    fun observeAll(): Flow<List<User>> = userDao.observeAll()

    fun getUserById(id: String): Flow<User?> = userDao.getUserById(id)

    // Observe a single user by id (flow from Room)
    fun observeUser(id: String): Flow<User?> = userDao.getUserById(id)

    suspend fun getUserByUsername(username: String): User? = userDao.getUserByUsername(username)

    // Fetch a single user from Firebase and upsert locally (best-effort, non-blocking)
    fun refreshUserFromRemote(id: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val list = firebase.fetchUsersSince(0L)
                val found = list.firstOrNull { it.id == id }
                found?.let { userDao.insert(it) }
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    suspend fun upsertUser(user: User, pushToRemote: Boolean = true) {
        userDao.insert(user)
        if (pushToRemote) {
            firebase.pushUser(user)
        }
    }

    // Check remote Firestore if username is taken (best-effort)
    suspend fun isUsernameTakenRemote(username: String, excludingUserId: String? = null): Boolean {
        return try {
            firebase.isUsernameTaken(username, excludingUserId)
        } catch (e: Exception) {
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

    fun syncFromRemote(since: Long = 0L) {
        // Fire-and-forget background sync; simple implementation
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val remote = firebase.fetchUsersSince(since)
                for (r in remote) {
                    // write remote to local — Room handles update/replace
                    userDao.insert(r)
                }
            } catch (e: Exception) {
                // Log/ignore for now
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
