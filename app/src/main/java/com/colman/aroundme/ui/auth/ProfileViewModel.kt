package com.colman.aroundme.ui.auth

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.util.Locale

// ViewModel for loading the current signed-in user's profile (display-only fields added).
class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val userRepo = UserRepository.getInstance(application)
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

    private val _isValidator = MutableLiveData<Boolean>(true)
    val isValidator: LiveData<Boolean> = _isValidator

    private val _eventsCount = MutableLiveData<Int>(12)
    val eventsCount: LiveData<Int> = _eventsCount

    private val _validationsCount = MutableLiveData<Int>(45)
    val validationsCount: LiveData<Int> = _validationsCount

    private val _pointsValue = MutableLiveData<String>("1,250")
    val pointsValue: LiveData<String> = _pointsValue

    private val _levelLabel = MutableLiveData<String>("LEVEL 5")
    val levelLabel: LiveData<String> = _levelLabel

    private val _progressText = MutableLiveData<String>("250 pts to Level 6")
    val progressText: LiveData<String> = _progressText

    private val _achievements = MutableLiveData<List<String>>(listOf("Early Bird", "Market Hunter", "Night Owl"))
    val achievements: LiveData<List<String>> = _achievements

    private val _radiusKm = MutableLiveData<Int>(15)
    val radiusKm: LiveData<Int> = _radiusKm

    // New numeric fields for progress
    private val _currentPoints = MutableLiveData<Int>(1250)
    val currentPoints: LiveData<Int> = _currentPoints

    private val _pointsToNext = MutableLiveData<Int>(250)
    val pointsToNext: LiveData<Int> = _pointsToNext

    // Computed percentage 0..1
    private val _progressPercent = MutableLiveData<Float>(0.83f)
    val progressPercent: LiveData<Float> = _progressPercent

    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> = _loading

    private val numberFormat = NumberFormat.getIntegerInstance(Locale.getDefault())

    fun loadCurrentUser(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // load basic fields from local DB if available
                val user = runCatching { userRepo.getUserById(userId).first() }.getOrNull()
                user?.let {
                    _name.postValue(it.fullName)
                    _email.postValue(it.email)
                    if (!it.imageUrl.isNullOrBlank()) {
                        runCatching { _imageUri.postValue(Uri.parse(it.imageUrl)) }
                    }
                }
                // populate the rest with defaults (UI spec)
                _handle.postValue(user?.id?.let { "@${it.take(6)}" } ?: _handle.value)

                // numeric points values (sample)
                _currentPoints.postValue(1250)
                _pointsToNext.postValue(250)

                // compute formatted points and percent
                val cp = _currentPoints.value ?: 0
                val pn = _pointsToNext.value ?: 0
                val total = cp + pn
                val percent = if (total > 0) cp.toFloat() / total.toFloat() else 0f
                _progressPercent.postValue(percent)
                _pointsValue.postValue(numberFormat.format(cp))
                _progressText.postValue("$pn pts to Level 6")
            } catch (ex: Exception) {
                // log and post safe defaults to avoid crashing the UI
                Log.e("ProfileViewModel", "Failed to load current user", ex)
                _name.postValue("")
                _email.postValue("")
                _imageUri.postValue(null)
                _handle.postValue("@user")
                _currentPoints.postValue(0)
                _pointsToNext.postValue(0)
                _progressPercent.postValue(0f)
                _pointsValue.postValue(numberFormat.format(0))
                _progressText.postValue("")
            }
        }
    }

    // Expose helpers in case fragment allows editing later
    fun setRadiusKm(value: Int) { _radiusKm.value = value }

    // Keep save/logout functions for future use, not used by display-only fragment
    fun saveProfile(userId: String, tempImageUri: Uri?, onComplete: (Boolean, String?) -> Unit) {
        // kept intentionally - not used by display screen
        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            try {
                var imageUrl = tempImageUri?.toString().orEmpty()
                if (tempImageUri != null && tempImageUri.scheme != "http" && tempImageUri.scheme != "https") {
                    // upload to firebase storage if available
                    firebase?.let { f ->
                        imageUrl = f.uploadImage(tempImageUri, "profile_images/$userId.jpg")
                    }
                }

                val updated = User(
                    id = userId,
                    fullName = _name.value.orEmpty(),
                    email = _email.value.orEmpty(),
                    imageUrl = imageUrl
                )

                userRepo.upsertUser(updated, pushToRemote = true)

                // update firebase auth profile if needed (best-effort)
                runCatching {
                    authRepo.getCurrentUser()?.let { fbUser ->
                        val req = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(updated.fullName)
                            .setPhotoUri(if (imageUrl.isNotBlank()) Uri.parse(imageUrl) else null)
                            .build()
                        fbUser.updateProfile(req).await()
                    }
                }

                _loading.postValue(false)
                onComplete(true, null)
            } catch (ex: Exception) {
                Log.e("ProfileViewModel", "saveProfile failed", ex)
                _loading.postValue(false)
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

}
