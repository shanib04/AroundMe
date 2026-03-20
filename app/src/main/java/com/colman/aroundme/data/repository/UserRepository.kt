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

    suspend fun upsertUser(user: User, pushToRemote: Boolean = true) {
        userDao.insert(user)
        if (pushToRemote) {
            firebase.pushUser(user)
        }
    }

    suspend fun deleteUser(id: String) {
        userDao.deleteById(id)
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
