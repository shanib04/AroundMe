package com.colman.aroundme.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.colman.aroundme.data.model.User
import com.colman.aroundme.data.model.UserProfile
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

// Repository responsible for Firebase authentication operations.
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
        ensureGoogleUserProfile(user)
        user
    }

    suspend fun registerWithEmailAndPassword(
        displayName: String,
        username: String,
        email: String,
        password: String,
        imageUri: Uri?
    ): Result<UserProfile> = runCatching {
        val normalizedUsername = username.lowercase()

        // Ensure username is unique in Firestore
        val existing = firestore.collection(USERS_COLLECTION)
            .whereEqualTo("username", normalizedUsername)
            .get()
            .await()
        if (!existing.isEmpty) {
            error("Username is already taken")
        }

        // Create Auth user
        val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        val user = authResult.user ?: error("Registration succeeded, but no user data was returned.")

        // Upload profile image if provided
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

        // Update Firebase Auth profile for convenience
        runCatching {
            updateFirebaseUserProfile(user, displayName, persistedImageSource)
        }

        // Build and persist Firestore/Room user document
        val userDoc = User(
            id = user.uid,
            name = displayName,
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

        // Maintain existing UserProfile shape for rest of app
        val profile = UserProfile(
            userId = user.uid,
            fullName = displayName,
            email = email,
            imageUrl = persistedImageSource
        )

        mergeProfiles(profile, buildFallbackProfile(user.reloadAndReturnCurrent(), persistedImageSource))
    }

    suspend fun loginWithIdentifierAndPassword(
        identifier: String,
        password: String
    ): Result<FirebaseUser> = runCatching {
        val trimmed = identifier.trim()
        if (trimmed.contains("@")) {
            // Treat as email
            val authResult = firebaseAuth.signInWithEmailAndPassword(trimmed, password).await()
            authResult.user ?: error("Login succeeded, but no user data was returned.")
        } else {
            // Treat as username: resolve to email via Firestore
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

    suspend fun signInWithGoogleAndSyncProfile(idToken: String): Result<UserProfile> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = firebaseAuth.signInWithCredential(credential).await()
        val user = authResult.user ?: error("Google sign-up succeeded, but no user data was returned.")
        ensureGoogleUserProfile(user)
    }

    suspend fun getCurrentUserProfile(): Result<UserProfile> = runCatching {
        val user = firebaseAuth.currentUser ?: error("No signed-in user was found.")
        val documentRef = firestore.collection(USERS_COLLECTION).document(user.uid)
        val snapshot = try {
            documentRef.get().await()
        } catch (_: FirebaseFirestoreException) {
            null
        }

        val firestoreProfile = snapshot?.toObject(UserProfile::class.java)
        val fallbackProfile = buildFallbackProfile(user.reloadAndReturnCurrent(), firestoreProfile?.imageUrl)
        val mergedProfile = mergeProfiles(firestoreProfile, fallbackProfile)

        if (firestoreProfile == null || firestoreProfile.fullName.isBlank() || firestoreProfile.imageUrl.isBlank()) {
            runCatching { saveUserProfile(mergedProfile) }
        }

        mergedProfile
    }

    private suspend fun uploadProfileImage(userId: String, imageUri: Uri): String {
        val imageRef = storage.reference.child("images/$userId.jpg")
        imageRef.putFile(imageUri).await()
        return imageRef.downloadUrl.await().toString()
    }

    private suspend fun updateFirebaseUserProfile(user: FirebaseUser, fullName: String, imageUrl: String) {
        val profileBuilder = UserProfileChangeRequest.Builder()
            .setDisplayName(fullName)
        if (imageUrl.isNotBlank()) {
            profileBuilder.setPhotoUri(imageUrl.toUri())
        }
        user.updateProfile(profileBuilder.build()).await()
    }

    private suspend fun saveUserProfile(profile: UserProfile) {
        firestore.collection(USERS_COLLECTION)
            .document(profile.userId)
            .set(profile)
            .await()
    }

    private suspend fun ensureGoogleUserProfile(user: FirebaseUser): UserProfile {
        val documentRef = firestore.collection(USERS_COLLECTION).document(user.uid)
        val snapshot = try {
            documentRef.get().await()
        } catch (exception: FirebaseFirestoreException) {
            if (exception.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return buildFallbackProfile(user)
            }
            throw exception
        }

        if (snapshot.exists()) {
            return mergeProfiles(snapshot.toObject(UserProfile::class.java), buildFallbackProfile(user, snapshot.getString("imageUrl")))
        }

        val profile = buildFallbackProfile(user)
        return try {
            saveUserProfile(profile)
            profile
        } catch (exception: FirebaseFirestoreException) {
            if (exception.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                buildFallbackProfile(user)
            } else {
                throw exception
            }
        }
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

    private fun buildFallbackProfile(user: FirebaseUser, fallbackImageUrl: String? = null): UserProfile =
        UserProfile(
        userId = user.uid,
        fullName = user.displayName.orEmpty(),
        email = user.email.orEmpty(),
        imageUrl = user.photoUrl?.toString().orEmpty().ifBlank { fallbackImageUrl.orEmpty() }
    )

    private fun mergeProfiles(primary: UserProfile?, fallback: UserProfile): UserProfile =
        UserProfile(
        userId = primary?.userId?.ifBlank { fallback.userId } ?: fallback.userId,
        fullName = primary?.fullName?.ifBlank { fallback.fullName } ?: fallback.fullName,
        email = primary?.email?.ifBlank { fallback.email } ?: fallback.email,
        imageUrl = primary?.imageUrl?.ifBlank { fallback.imageUrl } ?: fallback.imageUrl
    )

    private fun String.toUri(): Uri = Uri.parse(this)

    private suspend fun FirebaseUser.reloadAndReturnCurrent(): FirebaseUser {
        runCatching { reload().await() }
        return firebaseAuth.currentUser ?: this
    }

    private companion object {
        const val USERS_COLLECTION = "Users"
    }
}