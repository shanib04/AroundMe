package com.colman.aroundme.features.profile

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.colman.aroundme.R
import com.colman.aroundme.data.local.AppLocalDb
import com.colman.aroundme.data.model.User
import com.colman.aroundme.data.model.versionedProfileImageUrl
import com.colman.aroundme.data.remote.FirebaseModel
import com.colman.aroundme.data.remote.ProfileImageStoragePath
import com.colman.aroundme.data.repository.AuthRepository
import com.colman.aroundme.data.repository.EventRepository
import com.colman.aroundme.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

class EditProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val userRepo = UserRepository.getInstance(application)
    private val eventRepo = EventRepository.getInstance(application)
    private val authRepo = AuthRepository()
    private val firebase by lazy { runCatching { FirebaseModel.getInstance() }.getOrNull() }

    private val _email = MutableLiveData("")
    val email: LiveData<String> = _email

    private val _username = MutableLiveData("")
    val username: LiveData<String> = _username

    private val _displayName = MutableLiveData("")
    val displayName: LiveData<String> = _displayName

    private val _imageUri = MutableLiveData<Uri?>(null)
    val imageUri: LiveData<Uri?> = _imageUri

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _uploadProgress = MutableLiveData(0)
    val uploadProgress: LiveData<Int> = _uploadProgress

    private val _saveState = MutableLiveData<SaveState>(SaveState.Idle)
    val saveState: LiveData<SaveState> = _saveState

    private val _deleteState = MutableLiveData<DeleteState>(DeleteState.Idle)
    val deleteState: LiveData<DeleteState> = _deleteState

    private var loadedUser: User? = null

    sealed interface SaveState {
        data object Idle : SaveState
        data object Loading : SaveState
        data class Success(val message: String?) : SaveState
        data class Error(val message: String) : SaveState
    }

    sealed interface DeleteState {
        data object Idle : DeleteState
        data object Loading : DeleteState
        data object Success : DeleteState
        data class Error(val message: String) : DeleteState
    }

    fun currentUserId(): String = authRepo.getCurrentUser()?.uid.orEmpty()

    fun consumeSaveState() {
        _saveState.value = SaveState.Idle
    }

    fun consumeDeleteState() {
        _deleteState.value = DeleteState.Idle
    }

    fun setUsername(v: String) { _username.value = v }
    fun setDisplayName(v: String) { _displayName.value = v }

    fun loadCurrentUser() {
        val userId = currentUserId()
        if (userId.isBlank()) return
        loadUser(userId)
    }

    fun loadUser(userId: String) {
        // Clear stale data from a previous user session immediately
        if (loadedUser != null && loadedUser?.id != userId) {
            loadedUser = null
            _email.postValue("")
            _username.postValue("")
            _displayName.postValue("")
            _imageUri.postValue(null)
        }

        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            val localUser = runCatching { userRepo.getUserById(userId).first() }.getOrNull()
            val refreshedUser = userRepo.refreshUserFromRemoteNow(userId)
            val resolvedUser = refreshedUser ?: localUser ?: authFallbackUser(userId)

            loadedUser = resolvedUser
            resolvedUser?.let {
                _email.postValue(it.email)
                _username.postValue(it.username)
                _displayName.postValue(it.displayName)
                _imageUri.postValue(it.versionedProfileImageUrl().takeIf { value -> value.isNotBlank() }?.toUri())
            }
            _loading.postValue(false)
        }
    }

    suspend fun isUsernameUniqueLocal(candidate: String, currentUserId: String): Boolean {
        val found = runCatching { userRepo.getUserByUsername(candidate) }.getOrNull()
        return (found == null) || (found.id == currentUserId)
    }

    suspend fun isUsernameTakenRemote(candidate: String, currentUserId: String): Boolean {
        return userRepo.isUsernameTakenRemote(candidate, currentUserId)
    }

    fun saveProfile(tempImageUri: Uri?) {
        val userId = currentUserId()
        if (userId.isBlank()) {
            _saveState.value = SaveState.Error("No signed-in user")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            _saveState.postValue(SaveState.Loading)
            try {
                val existing = loadedUser
                    ?: runCatching { userRepo.getUserById(userId).first() }.getOrNull()
                    ?: authFallbackUser(userId)
                var imageUrl = tempImageUri?.toString().orEmpty()
                var imageUploadMessage: String? = null
                if (tempImageUri != null && tempImageUri.scheme != "http" && tempImageUri.scheme != "https") {
                    val uploadResult = runCatching {
                        firebase?.uploadImage(tempImageUri, buildProfileImageRemotePath(userId)) { progress: Int ->
                            _uploadProgress.postValue(progress)
                        }
                    }
                    imageUrl = uploadResult.getOrNull().orEmpty()
                    if (uploadResult.isFailure) {
                        Log.w("EditProfileVM", "Profile image upload failed; saving other profile changes", uploadResult.exceptionOrNull())
                        imageUploadMessage = "Profile details saved, but the new photo couldn't be uploaded."
                    }
                }

                val stableEmail = existing?.email?.takeIf { it.isNotBlank() }
                    ?: _email.value.orEmpty()
                val normalizedUsername = _username.value.orEmpty().trim().lowercase()
                val updated = (existing ?: User(id = userId, email = stableEmail)).copy(
                    username = normalizedUsername,
                    displayName = _displayName.value.orEmpty(),
                    profileImageUrl = imageUrl.ifBlank { existing?.profileImageUrl.orEmpty() },
                    email = stableEmail,
                    lastUpdated = System.currentTimeMillis()
                )

                val candidate = updated.username
                if (candidate.isNotBlank()) {
                    val taken = userRepo.isUsernameTakenRemote(candidate, userId)
                    if (taken) {
                        _loading.postValue(false)
                        _saveState.postValue(SaveState.Error("Username already taken"))
                        return@launch
                    }
                }

                userRepo.updateUserProfile(updated, pushToRemote = true)
                loadedUser = updated
                _email.postValue(stableEmail)
                _username.postValue(updated.username)
                _displayName.postValue(updated.displayName)
                _imageUri.postValue(updated.versionedProfileImageUrl().takeIf { value -> value.isNotBlank() }?.toUri())

                _loading.postValue(false)
                _uploadProgress.postValue(0)
                _saveState.postValue(SaveState.Success(imageUploadMessage))

                runCatching {
                    val fbUser = authRepo.getCurrentUser()
                    fbUser?.let {
                        val req = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(updated.displayName)
                            .setPhotoUri(updated.profileImageUrl.takeIf(String::isNotBlank)?.toUri())
                            .build()
                        it.updateProfile(req).await()
                    }
                }.onFailure {
                    Log.w("EditProfileVM", "Firebase Auth profile sync failed after save", it)
                }

                runCatching { userRepo.refreshUserFromRemote(userId) }
                    .onFailure {
                        Log.w("EditProfileVM", "Remote refresh failed after successful save", it)
                    }
            } catch (e: Exception) {
                Log.e("EditProfileVM", "saveProfile failed", e)
                _loading.postValue(false)
                _uploadProgress.postValue(0)
                _saveState.postValue(SaveState.Error(e.localizedMessage ?: "Failed to save"))
            }
        }
    }

    fun deleteProfile() {
        val userId = currentUserId()
        if (userId.isBlank()) {
            _deleteState.value = DeleteState.Error("No signed-in user")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            _deleteState.postValue(DeleteState.Loading)
            try {
                authRepo.deleteCurrentUserAccountAndData(firebase ?: FirebaseModel.getInstance())
                eventRepo.deleteEventsByPublisher(userId, removeRemote = false)
                AppLocalDb.getInstance(getApplication()).clearAllTables()
                loadedUser = null
                _loading.postValue(false)
                _deleteState.postValue(DeleteState.Success)
            } catch (e: Exception) {
                Log.e("EditProfileVM", "delete failed", e)
                _loading.postValue(false)
                _deleteState.postValue(DeleteState.Error(e.localizedMessage ?: "Failed to delete"))
            }
        }
    }

    private fun authFallbackUser(userId: String): User? {
        val authUser = authRepo.getCurrentUser() ?: return null
        if (authUser.uid != userId) return null

        val fallbackUsername = loadedUser?.username
            ?.takeIf { it.isNotBlank() }
            ?: authUser.email
                ?.substringBefore('@')
                ?.trim()
                ?.lowercase(Locale.US)
                .orEmpty()
                .take(15)
                .trim('_')
                .ifBlank { "explorer_${userId.take(6)}" }

        val fallbackDisplayName = loadedUser?.displayName
            ?.takeIf { it.isNotBlank() }
            ?: authUser.displayName
                ?.trim()
                .orEmpty()
                .ifBlank { humanizeUsername(fallbackUsername) }
                .ifBlank { getApplication<Application>().getString(R.string.default_profile_display_name) }

        return User(
            id = userId,
            username = fallbackUsername,
            displayName = fallbackDisplayName,
            profileImageUrl = loadedUser?.profileImageUrl?.takeIf { it.isNotBlank() }
                ?: authUser.photoUrl?.toString().orEmpty(),
            email = loadedUser?.email?.takeIf { it.isNotBlank() }
                ?: authUser.email.orEmpty(),
            discoveryRadiusKm = loadedUser?.discoveryRadiusKm ?: 15,
            points = loadedUser?.points ?: 0,
            eventsPublishedCount = loadedUser?.eventsPublishedCount ?: 0,
            validationsMadeCount = loadedUser?.validationsMadeCount ?: 0,
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun buildProfileImageRemotePath(userId: String): String {
        return ProfileImageStoragePath.forUser(userId)
    }
}
