package com.colman.aroundme.data.repository

import android.content.Context
import com.colman.aroundme.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserSessionManager private constructor(
    private val userRepository: UserRepository,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val uid = firebaseAuth.currentUser?.uid
        if (uid != null) {
            startUserListener(uid)
        } else {
            stopUserListener()
        }
    }

    fun start() {
        auth.addAuthStateListener(authListener)
        auth.currentUser?.uid?.let { startUserListener(it) }
    }

    fun stop() {
        auth.removeAuthStateListener(authListener)
        stopUserListener()
    }

    private fun startUserListener(uid: String) {
        listenerRegistration?.remove()
        listenerRegistration = firestore.collection("Users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val user = snapshot.toObject(User::class.java) ?: return@addSnapshotListener
                _currentUser.value = user
                scope.launch {
                    userRepository.upsertUser(user, pushToRemote = false)
                }
            }
    }

    private fun stopUserListener() {
        listenerRegistration?.remove()
        listenerRegistration = null
        _currentUser.value = null
    }

    companion object {
        @Volatile
        private var INSTANCE: UserSessionManager? = null

        fun getInstance(context: Context): UserSessionManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserSessionManager(
                    UserRepository.getInstance(context),
                    FirebaseFirestore.getInstance(),
                    FirebaseAuth.getInstance()
                ).also { INSTANCE = it }
            }
    }
}
