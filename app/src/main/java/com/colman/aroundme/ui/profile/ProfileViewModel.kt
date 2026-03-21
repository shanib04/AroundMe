package com.colman.aroundme.ui.profile

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.colman.aroundme.data.model.User
import com.colman.aroundme.data.remote.FirebaseModel
import com.colman.aroundme.data.repository.UserRepository
import com.colman.aroundme.data.repository.AuthRepository
import com.colman.aroundme.data.repository.EventRepository
import androidx.lifecycle.MediatorLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.util.Locale

// ViewModel for the Profile screen
class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val userRepo = UserRepository.getInstance(application)
    private val eventRepo = EventRepository.getInstance(application)
    private val authRepo = AuthRepository()
    private val firebase by lazy {
        runCatching { FirebaseModel.getInstance() }.getOrNull()
    }

    private val _name = MutableLiveData<String>("")
    val name: LiveData<String> = _name

    private val _email = MutableLiveData<String>("")
    val email: LiveData<String> = _email

    private val _imageUri = MutableLiveData<Uri?>(null)
    val imageUri: LiveData<Uri?> = _imageUri

    // Display-only fields
    private val _handle = MutableLiveData<String>("@alex_r")
    val handle: LiveData<String> = _handle

    private val _username = MutableLiveData<String>("")
    val username: LiveData<String> = _username

    private val _displayName = MutableLiveData<String>("")
    val displayName: LiveData<String> = _displayName

    private val _bio = MutableLiveData<String>("")
    val bio: LiveData<String> = _bio

    private val _isValidator = MutableLiveData<Boolean>(true)
    val isValidator: LiveData<Boolean> = _isValidator

    private val _radiusKm = MutableLiveData<Int>(15)
    val radiusKm: LiveData<Int> = _radiusKm

    // New computed fields based on user's events
    private val _userDegree = MutableLiveData<String>("Local Scout")
    val userDegree: LiveData<String> = _userDegree

    private val _influenceScore = MutableLiveData<String>("0.0")
    val influenceScore: LiveData<String> = _influenceScore

    private val _totalValidations = MutableLiveData<Int>(0)
    val totalValidations: LiveData<Int> = _totalValidations

    private val _eventsCreated = MutableLiveData<Int>(0)
    val eventsCreated: LiveData<Int> = _eventsCreated

    private val _calculatedPoints = MutableLiveData<Int>(0)
    val calculatedPoints: LiveData<Int> = _calculatedPoints

    // Computed percentage 0..1
    private val _progressPercent = MutableLiveData<Float>(0.83f)
    val progressPercent: LiveData<Float> = _progressPercent

    // UI helpers expected by fragment
    private val _levelLabel = MutableLiveData<String>("LEVEL 1")
    val levelLabel: LiveData<String> = _levelLabel

    private val _progressText = MutableLiveData<String>("")
    val progressText: LiveData<String> = _progressText

    private val _achievements = MutableLiveData<List<String>>(listOf("Early Bird", "Market Hunter", "Night Owl"))
    val achievements: LiveData<List<String>> = _achievements

    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> = _loading

    private val _uploadProgress = MutableLiveData<Int>(0)
    val uploadProgress: LiveData<Int> = _uploadProgress

    private val numberFormat = NumberFormat.getIntegerInstance(Locale.getDefault())

    fun loadCurrentUser(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // load basic fields from local DB if available
                val user = runCatching { userRepo.getUserById(userId).first() }.getOrNull()
                user?.let {
                    _name.postValue(it.name)
                    _email.postValue(it.email)
                    _username.postValue(it.username)
                    _displayName.postValue(it.displayName)
                    _bio.postValue(it.bio)
                    if (!it.profileImageUrl.isNullOrBlank()) {
                        runCatching { _imageUri.postValue(Uri.parse(it.profileImageUrl)) }
                    }
                }
                // populate the rest with defaults (UI spec)
                _handle.postValue(user?.id?.let { "@${it.take(6)}" } ?: _handle.value)

                // progress percent will be computed from calculatedPoints once events are observed
                _progressPercent.postValue(0f)
                _progressText.postValue("")

                // Observe events for this user and compute stats
                val eventsLive = eventRepo.observeEventsByPublisher(userId)
                // Attach a MediatorLiveData to observe the events LiveData and compute stats on changes
                eventsLive?.let { ld ->
                    // Post initial snapshot if available
                    val initial = ld.value ?: emptyList()
                    computeStatsAndPost(initial)
                    // Observe future changes via addSource on main thread
                    // Use viewModelScope to add an observer on the main thread
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val mediator = MediatorLiveData<List<com.colman.aroundme.data.model.Event>>()
                        mediator.addSource(ld) { list ->
                            computeStatsAndPost(list ?: emptyList())
                        }
                    }
                }
            } catch (ex: Exception) {
                // log and post safe defaults to avoid crashing the UI
                Log.e("ProfileViewModel", "Failed to load current user", ex)
                _name.postValue("")
                _email.postValue("")
                _imageUri.postValue(null)
                _handle.postValue("@user")
                _progressPercent.postValue(0f)
                _progressText.postValue("")
            }
        }
    }

    // helpers
    fun setRadiusKm(value: Int) { _radiusKm.value = value }

    suspend fun isUsernameUnique(candidate: String, currentUserId: String): Boolean {
        // check local DB first
        val found = runCatching { userRepo.getUserByUsername(candidate) }.getOrNull()
        return (found == null) || (found.id == currentUserId)
    }

    suspend fun isUsernameTakenRemotely(candidate: String, currentUserId: String): Boolean {
        return userRepo.isUsernameTakenRemote(candidate, currentUserId)
    }

    // Setters for edit screen
    fun setName(value: String) { _name.value = value }
    fun setEmail(value: String) { _email.value = value }
    fun setUsername(value: String) { _username.value = value }
    fun setDisplayName(value: String) { _displayName.value = value }
    fun setBio(value: String) { _bio.value = value }

    // Save/logout functions used by edit screen
    fun saveProfile(userId: String, tempImageUri: Uri?, onComplete: (Boolean, String?) -> Unit) {
        // kept intentionally - not used by display screen
        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            try {
                var imageUrl = tempImageUri?.toString().orEmpty()
                if (tempImageUri != null && tempImageUri.scheme != "http" && tempImageUri.scheme != "https") {
                    // upload to firebase storage if available
                    firebase?.let { f ->
                        imageUrl = f.uploadImage(tempImageUri, "profile_images/$userId.jpg") { progress ->
                            _uploadProgress.postValue(progress)
                        }
                    }
                }

                val updated = User(
                    id = userId,
                    name = _name.value.orEmpty(),
                    username = _username.value.orEmpty(),
                    displayName = _displayName.value.orEmpty(),
                    profileImageUrl = imageUrl,
                    email = _email.value.orEmpty(),
                    bio = _bio.value.orEmpty()
                )

                // Check remote username uniqueness before pushing
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

                // update firebase auth profile if needed (best-effort)
                runCatching {
                    authRepo.getCurrentUser()?.let { fbUser ->
                        // only update if something changed
                        if (fbUser.displayName != updated.name || fbUser.photoUrl?.toString() != imageUrl) {
                            // ignore result
                            runCatching {
                                val req = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                    .setDisplayName(updated.name)
                                    .setPhotoUri(if (imageUrl.isNotBlank()) Uri.parse(imageUrl) else null)
                                    .build()
                                fbUser.updateProfile(req).await()
                            }
                        }
                    }
                }

                // update local LiveData values from saved user
                _name.postValue(updated.name)
                _email.postValue(updated.email)
                _imageUri.postValue(if (imageUrl.isNotBlank()) Uri.parse(imageUrl) else null)

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
                // firebase sign out
                runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().signOut() }

                // clear local DB
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
                // delete local events and remote ones
                eventRepo.deleteEventsByPublisher(userId, removeRemote = true)
                // delete local user
                userRepo.deleteUser(userId)

                // attempt to delete remote user doc and events as well (safe-call)
                runCatching { firebase?.deleteUserAndEvents(userId) }

                // sign out
                runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().signOut() }

                onComplete(true, null)
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "deleteProfile failed", e)
                onComplete(false, e.localizedMessage ?: "Failed to delete profile")
            }
        }
    }

    private fun computeStatsAndPost(list: List<com.colman.aroundme.data.model.Event>) {
        val count = list.size
        val sumActive = list.sumOf { it.activeVotes }
        val sumInactive = list.sumOf { it.inactiveVotes }
        val points = (count * 10) + (sumActive * 5) - (sumInactive * 2)
        val influence = if (count > 0) (sumActive.toDouble() / count.toDouble()) else 0.0

        _eventsCreated.postValue(count)
        _totalValidations.postValue(sumActive)
        _calculatedPoints.postValue(points)
        _influenceScore.postValue(String.format(Locale.getDefault(), "%.1f", influence))

        // Update progress percent: assume next level at +250 points for UI progress visualization
        val next = 250
        val percent = if (points + next > 0) points.toFloat() / (points + next).toFloat() else 0f
        _progressPercent.postValue(percent.coerceIn(0f, 1f))
        _progressText.postValue("${next} pts to Level 6")

        // Update level label heuristically
        _levelLabel.postValue("LEVEL ${1 + (points / 250)}")

        val degree = when {
            points <= 50 -> "Local Scout"
            points <= 200 -> "Active Explorer"
            else -> "Community Pillar"
        }
        _userDegree.postValue(degree)
    }
}
