package com.colman.aroundme.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.colman.aroundme.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream

class AuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val appContext: Context = FirebaseAuth.getInstance().app.applicationContext
) {

    fun getCurrentUser(): FirebaseUser? = firebaseAuth.currentUser

    suspend fun loginWithEmailAndPassword(
        email: String,
        password: String
    ): Result<FirebaseUser> = runCatching {
        val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        authResult.user ?: error("Login succeeded, but no user data was returned.")
    }

    suspend fun loginWithGoogle(idToken: String): Result<FirebaseUser> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = firebaseAuth.signInWithCredential(credential).await()
        val user = authResult.user ?: error("Google sign-in succeeded, but no user data was returned.")
        ensureGoogleUser(user)
        user
    }

    suspend fun registerWithEmailAndPassword(
        displayName: String,
        username: String,
        email: String,
        password: String,
        imageUri: Uri?
    ): Result<User> = runCatching {
        val normalizedUsername = username.lowercase()
        val existing = firestore.collection(USERS_COLLECTION)
            .whereEqualTo("username", normalizedUsername)
            .get()
            .await()
        if (!existing.isEmpty) {
            error("Username is already taken")
        }

        val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        val user = authResult.user ?: error("Registration succeeded, but no user data was returned.")

        var persistedImageSource = ""
        if (imageUri != null) {
            runCatching {
                val localImageUri = copyImageToAppStorage(imageUri, user.uid)
                persistedImageSource = localImageUri.toString()
                val uploadedUrl = uploadProfileImage(user.uid, localImageUri)
                if (uploadedUrl.isNotBlank()) {
                    persistedImageSource = uploadedUrl
                }
            }
        }

        runCatching {
            updateFirebaseUserProfile(user, displayName, persistedImageSource)
        }

        val userDoc = User(
            id = user.uid,
            username = normalizedUsername,
            displayName = displayName,
            profileImageUrl = persistedImageSource,
            email = email
        )

        runCatching {
            firestore.collection(USERS_COLLECTION)
                .document(user.uid)
                .set(userDoc)
                .await()
        }

        userDoc
    }

    suspend fun loginWithIdentifierAndPassword(
        identifier: String,
        password: String
    ): Result<FirebaseUser> = runCatching {
        val trimmed = identifier.trim()
        if (trimmed.contains("@")) {
            val authResult = firebaseAuth.signInWithEmailAndPassword(trimmed, password).await()
            authResult.user ?: error("Login succeeded, but no user data was returned.")
        } else {
            val normalizedUsername = trimmed.lowercase()
            val snapshot = firestore.collection(USERS_COLLECTION)
                .whereEqualTo("username", normalizedUsername)
                .get()
                .await()

            if (snapshot.isEmpty) {
                error("Username not found")
            }

            val doc = snapshot.documents.first()
            val email = doc.getString("email").orEmpty()
            if (email.isBlank()) {
                error("Username not found")
            }

            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            authResult.user ?: error("Login succeeded, but no user data was returned.")
        }
    }

    suspend fun signInWithGoogleAndSyncProfile(idToken: String): Result<User> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = firebaseAuth.signInWithCredential(credential).await()
        val user = authResult.user ?: error("Google sign-up succeeded, but no user data was returned.")
        ensureGoogleUser(user)
    }

    suspend fun getCurrentUserProfile(): Result<User> = runCatching {
        val user = firebaseAuth.currentUser ?: error("No signed-in user was found.")
        ensureGoogleUser(user.reloadAndReturnCurrent())
    }

    private suspend fun uploadProfileImage(userId: String, imageUri: Uri): String {
        val imageRef = storage.reference.child("images/$userId.jpg")
        imageRef.putFile(imageUri).await()
        return imageRef.downloadUrl.await().toString()
    }

    private suspend fun updateFirebaseUserProfile(user: FirebaseUser, displayName: String, imageUrl: String) {
        val profileBuilder = UserProfileChangeRequest.Builder()
            .setDisplayName(displayName)
        if (imageUrl.isNotBlank()) {
            profileBuilder.setPhotoUri(imageUrl.toUri())
        }
        user.updateProfile(profileBuilder.build()).await()
    }

    private suspend fun ensureGoogleUser(user: FirebaseUser): User {
        val documentRef = firestore.collection(USERS_COLLECTION).document(user.uid)
        val snapshot = try {
            documentRef.get().await()
        } catch (exception: FirebaseFirestoreException) {
            if (exception.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return buildFallbackUser(user)
            }
            throw exception
        }

        if (snapshot.exists()) {
            return mergeUsers(snapshot.toObject(User::class.java), buildFallbackUser(user, snapshot.getString("profileImageUrl")))
        }

        val profile = buildFallbackUser(user)
        return try {
            firestore.collection(USERS_COLLECTION)
                .document(user.uid)
                .set(profile)
                .await()
            profile
        } catch (exception: FirebaseFirestoreException) {
            if (exception.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                buildFallbackUser(user)
            } else {
                throw exception
            }
        }
    }

    private suspend fun FirebaseUser.reloadAndReturnCurrent(): FirebaseUser {
        runCatching { reload().await() }
        return firebaseAuth.currentUser ?: this
    }

    private fun copyImageToAppStorage(sourceUri: Uri, userId: String): Uri {
        if (sourceUri.scheme == ContentResolver.SCHEME_FILE) {
            return sourceUri
        }

        val inputStream = appContext.contentResolver.openInputStream(sourceUri)
            ?: error("Unable to open the selected profile image.")
        val imageFile = File(appContext.cacheDir, "profile_upload_$userId.jpg")
        inputStream.use { input ->
            FileOutputStream(imageFile).use { output ->
                input.copyTo(output)
                output.flush()
            }
        }
        return Uri.fromFile(imageFile)
    }

    private fun buildFallbackUser(user: FirebaseUser, fallbackImageUrl: String? = null): User =
        User(
            id = user.uid,
            username = user.email?.substringBefore('@')?.lowercase().orEmpty(),
            displayName = user.displayName.orEmpty(),
            email = user.email.orEmpty(),
            profileImageUrl = user.photoUrl?.toString().orEmpty().ifBlank { fallbackImageUrl.orEmpty() },
            discoveryRadiusKm = 15
        )

    private fun mergeUsers(primary: User?, fallback: User): User =
        User(
            id = primary?.id?.ifBlank { fallback.id } ?: fallback.id,
            username = primary?.username?.ifBlank { fallback.username } ?: fallback.username,
            displayName = primary?.displayName?.ifBlank { fallback.displayName } ?: fallback.displayName,
            profileImageUrl = primary?.profileImageUrl?.ifBlank { fallback.profileImageUrl } ?: fallback.profileImageUrl,
            email = primary?.email?.ifBlank { fallback.email } ?: fallback.email,
            discoveryRadiusKm = primary?.discoveryRadiusKm ?: fallback.discoveryRadiusKm,
            points = primary?.points ?: fallback.points,
            eventsPublishedCount = primary?.eventsPublishedCount ?: fallback.eventsPublishedCount,
            validationsMadeCount = primary?.validationsMadeCount ?: fallback.validationsMadeCount,
            rankTitle = primary?.rankTitle?.ifBlank { fallback.rankTitle } ?: fallback.rankTitle,
            lastUpdated = maxOf(primary?.lastUpdated ?: 0L, fallback.lastUpdated)
        )

    private fun String.toUri(): Uri = Uri.parse(this)


    private companion object {
        const val USERS_COLLECTION = "Users"
    }
}