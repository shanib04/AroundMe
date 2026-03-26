package com.colman.aroundme.features.profile

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.User
import com.colman.aroundme.data.remote.FirebaseModel
import com.colman.aroundme.data.repository.AuthRepository
import com.colman.aroundme.data.repository.EventRepository
import com.colman.aroundme.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.util.Locale

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val userRepo = UserRepository.getInstance(application)
    private val eventRepo = EventRepository.getInstance(application)
    private val authRepo = AuthRepository()
    private val firebase by lazy {
        runCatching { FirebaseModel.getInstance() }.getOrNull()
    }

    private val _name = MutableLiveData("")
    val name: LiveData<String> = _name

    private val _email = MutableLiveData("")
    val email: LiveData<String> = _email

    private val _imageUri = MutableLiveData<Uri?>(null)
    val imageUri: LiveData<Uri?> = _imageUri

    private val _handle = MutableLiveData("@")
    val handle: LiveData<String> = _handle

    private val _username = MutableLiveData("")
    val username: LiveData<String> = _username

    private val _displayName = MutableLiveData("")
    val displayName: LiveData<String> = _displayName

    private val _bio = MutableLiveData("")
    val bio: LiveData<String> = _bio

    private val _isValidator = MutableLiveData(false)
    val isValidator: LiveData<Boolean> = _isValidator

    private val _radiusKm = MutableLiveData(15)
    val radiusKm: LiveData<Int> = _radiusKm

    private val _userDegree = MutableLiveData("")
    val userDegree: LiveData<String> = _userDegree

    private val _influenceScore = MutableLiveData("0.0")
    val influenceScore: LiveData<String> = _influenceScore

    private val _totalValidations = MutableLiveData(0)
    val totalValidations: LiveData<Int> = _totalValidations

    private val _eventsCreated = MutableLiveData(0)
    val eventsCreated: LiveData<Int> = _eventsCreated

    private val _calculatedPoints = MutableLiveData(0)
    val calculatedPoints: LiveData<Int> = _calculatedPoints

    private val _progressPercent = MutableLiveData(0f)
    val progressPercent: LiveData<Float> = _progressPercent

    private val _levelLabel = MutableLiveData("LEVEL 1")
    val levelLabel: LiveData<String> = _levelLabel

    private val _progressText = MutableLiveData("")
    val progressText: LiveData<String> = _progressText

    private val _achievements = MutableLiveData<List<String>>(emptyList())
    val achievements: LiveData<List<String>> = _achievements

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _uploadProgress = MutableLiveData(0)
    val uploadProgress: LiveData<Int> = _uploadProgress

    private val numberFormat = NumberFormat.getIntegerInstance(Locale.getDefault())

    fun loadCurrentUser() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val authUser = authRepo.getCurrentUser()
                val userId = authUser?.uid.orEmpty()
                if (userId.isBlank()) {
                    postEmptyProfile()
                    return@launch
                }

                userRepo.refreshUserFromRemote(userId)
                val user = runCatching { userRepo.getUserById(userId).first() }.getOrNull()
                postUser(user, authUser?.email.orEmpty())

                val events = eventRepo.observeEventsByPublisher(userId).value.orEmpty()
                computeStatsAndPost(events, user)
            } catch (ex: Exception) {
                Log.e("ProfileViewModel", "Failed to load current user", ex)
                postEmptyProfile()
            }
        }
    }

    fun setRadiusKm(value: Int) { _radiusKm.value = value }

    suspend fun isUsernameUnique(candidate: String, currentUserId: String): Boolean {
        val found = runCatching { userRepo.getUserByUsername(candidate) }.getOrNull()
        return (found == null) || (found.id == currentUserId)
    }

    suspend fun isUsernameTakenRemotely(candidate: String, currentUserId: String): Boolean {
        return userRepo.isUsernameTakenRemote(candidate, currentUserId)
    }

    fun setName(value: String) { _name.value = value }
    fun setEmail(value: String) { _email.value = value }
    fun setUsername(value: String) { _username.value = value }
    fun setDisplayName(value: String) { _displayName.value = value }
    fun setBio(value: String) { _bio.value = value }

    fun saveProfile(userId: String, tempImageUri: Uri?, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            try {
                var imageUrl = tempImageUri?.toString().orEmpty()
                if (tempImageUri != null && tempImageUri.scheme != "http" && tempImageUri.scheme != "https") {
                    firebase?.let { f ->
                        imageUrl = f.uploadImage(tempImageUri, "profile_images/$userId.jpg") { progress ->
                            _uploadProgress.postValue(progress)
                        }
                    }
                }

                val updated = User(
                    id = userId,
                    username = _username.value.orEmpty(),
                    displayName = _displayName.value.orEmpty(),
                    profileImageUrl = imageUrl,
                    email = _email.value.orEmpty(),
                    bio = _bio.value.orEmpty()
                )

                val candidate = updated.username
                if (candidate.isNotBlank()) {
                    val takenRemote = userRepo.isUsernameTakenRemote(candidate, userId)
                    if (takenRemote) {
                        _loading.postValue(false)
                        onComplete(false, "Username already taken")
                        return@launch
                    }
                }

                userRepo.upsertUser(updated, pushToRemote = true)

                runCatching {
                    authRepo.getCurrentUser()?.let { fbUser ->
                        if (fbUser.displayName != updated.displayName || fbUser.photoUrl?.toString() != imageUrl) {
                            runCatching {
                                val req = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                    .setDisplayName(updated.displayName)
                                    .setPhotoUri(if (imageUrl.isNotBlank()) Uri.parse(imageUrl) else null)
                                    .build()
                                fbUser.updateProfile(req).await()
                            }
                        }
                    }
                }

                postUser(updated, updated.email)
                _loading.postValue(false)
                _uploadProgress.postValue(0)
                onComplete(true, null)
            } catch (ex: Exception) {
                Log.e("ProfileViewModel", "saveProfile failed", ex)
                _loading.postValue(false)
                _uploadProgress.postValue(0)
                onComplete(false, ex.localizedMessage ?: "Failed to save profile")
            }
        }
    }

    fun logout(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().signOut() }
                userRepo.clearAllLocal()
                onComplete(true)
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "logout failed", e)
                onComplete(false)
            }
        }
    }

    fun deleteProfile(userId: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                eventRepo.deleteEventsByPublisher(userId, removeRemote = true)
                userRepo.deleteUser(userId)
                runCatching { firebase?.deleteUserAndEvents(userId) }
                runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().signOut() }
                onComplete(true, null)
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "deleteProfile failed", e)
                onComplete(false, e.localizedMessage ?: "Failed to delete profile")
            }
        }
    }

    private fun postUser(user: User?, fallbackEmail: String) {
        val safeDisplayName = user?.displayName?.takeIf { it.isNotBlank() } ?: ""
        val safeUsername = user?.username?.trim().orEmpty()
        _name.postValue(safeDisplayName)
        _email.postValue(user?.email?.takeIf { it.isNotBlank() } ?: fallbackEmail)
        _username.postValue(safeUsername)
        _displayName.postValue(safeDisplayName)
        _bio.postValue(user?.bio.orEmpty())
        _handle.postValue(if (safeUsername.isNotBlank()) "@$safeUsername" else "")
        _isValidator.postValue((user?.validationsMadeCount ?: 0) > 0)
        if (!user?.profileImageUrl.isNullOrBlank()) {
            runCatching { _imageUri.postValue(Uri.parse(user.profileImageUrl)) }
        } else {
            _imageUri.postValue(null)
        }
    }

    private fun postEmptyProfile() {
        _name.postValue("")
        _email.postValue("")
        _imageUri.postValue(null)
        _handle.postValue("")
        _username.postValue("")
        _displayName.postValue("")
        _bio.postValue("")
        _isValidator.postValue(false)
        _userDegree.postValue("")
        _eventsCreated.postValue(0)
        _totalValidations.postValue(0)
        _influenceScore.postValue("0.0")
        _calculatedPoints.postValue(0)
        _progressPercent.postValue(0f)
        _levelLabel.postValue("LEVEL 1")
        _progressText.postValue("")
        _achievements.postValue(emptyList())
    }

    private fun computeStatsAndPost(events: List<Event>, user: User?) {
        val count = events.size
        val sumActive = events.sumOf { it.activeVotes }
        val sumInactive = events.sumOf { it.inactiveVotes }
        val persistedPoints = user?.points ?: 0
        val points = maxOf(persistedPoints, (count * 10) + (sumActive * 5) - (sumInactive * 2))
        val influence = if (count > 0) (sumActive.toDouble() / count.toDouble()) else 0.0
        val validations = maxOf(user?.validationsMadeCount ?: 0, sumActive)
        val createdCount = maxOf(user?.eventsPublishedCount ?: 0, count)

        _eventsCreated.postValue(createdCount)
        _totalValidations.postValue(validations)
        _calculatedPoints.postValue(points)
        _influenceScore.postValue(String.format(Locale.getDefault(), "%.1f", influence))

        val nextLevelPoints = 250
        val percent = if (nextLevelPoints > 0) (points % nextLevelPoints).toFloat() / nextLevelPoints.toFloat() else 0f
        _progressPercent.postValue(percent.coerceIn(0f, 1f))
        _progressText.postValue("${nextLevelPoints - (points % nextLevelPoints)} pts to Level ${(points / nextLevelPoints) + 2}")
        _levelLabel.postValue("LEVEL ${(points / nextLevelPoints) + 1}")

        val degree = user?.rankTitle?.takeIf { it.isNotBlank() } ?: when {
            points <= 50 -> "Local Scout"
            points <= 200 -> "Active Explorer"
            else -> "Community Pillar"
        }
        _userDegree.postValue(degree)

        val achievements = buildList {
            if (createdCount > 0) add("Event Creator")
            if (validations > 0) add("Community Validator")
            if (points >= 100) add("Rising Local")
        }
        _achievements.postValue(achievements)
    }
}
