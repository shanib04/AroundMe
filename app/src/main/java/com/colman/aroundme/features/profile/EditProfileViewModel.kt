package com.colman.aroundme.features.profile

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.colman.aroundme.data.model.User
import com.colman.aroundme.data.remote.FirebaseModel
import com.colman.aroundme.data.repository.EventRepository
import com.colman.aroundme.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val userRepo = UserRepository.getInstance(application)
    private val eventRepo = EventRepository.getInstance(application)
    private val firebase by lazy { runCatching { FirebaseModel.getInstance() }.getOrNull() }

    private val _name = MutableLiveData<String>("")
    val name: LiveData<String> = _name

    private val _email = MutableLiveData<String>("")
    val email: LiveData<String> = _email

    private val _username = MutableLiveData<String>("")
    val username: LiveData<String> = _username

    private val _displayName = MutableLiveData<String>("")
    val displayName: LiveData<String> = _displayName

    private val _bio = MutableLiveData<String>("")
    val bio: LiveData<String> = _bio

    private val _imageUri = MutableLiveData<Uri?>(null)
    val imageUri: LiveData<Uri?> = _imageUri

    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> = _loading

    private val _uploadProgress = MutableLiveData<Int>(0)
    val uploadProgress: LiveData<Int> = _uploadProgress

    fun setName(v: String) { _name.value = v }
    fun setEmail(v: String) { _email.value = v }
    fun setUsername(v: String) { _username.value = v }
    fun setDisplayName(v: String) { _displayName.value = v }
    fun setBio(v: String) { _bio.value = v }
    fun setImageUri(uri: Uri?) { _imageUri.value = uri }

    fun loadUser(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val user = runCatching { userRepo.getUserById(userId).first() }.getOrNull()
            user?.let {
                _name.postValue(it.name)
                _email.postValue(it.email)
                _username.postValue(it.username)
                _displayName.postValue(it.displayName)
                _bio.postValue(it.bio)
                if (!it.profileImageUrl.isNullOrBlank()) _imageUri.postValue(Uri.parse(it.profileImageUrl))
            }
        }
    }

    suspend fun isUsernameUniqueLocal(candidate: String, currentUserId: String): Boolean {
        val found = runCatching { userRepo.getUserByUsername(candidate) }.getOrNull()
        return (found == null) || (found.id == currentUserId)
    }

    suspend fun isUsernameTakenRemote(candidate: String, currentUserId: String): Boolean {
        return userRepo.isUsernameTakenRemote(candidate, currentUserId)
    }

    fun saveProfile(userId: String, tempImageUri: Uri?, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            try {
                var imageUrl = tempImageUri?.toString().orEmpty()
                if (tempImageUri != null && tempImageUri.scheme != "http" && tempImageUri.scheme != "https") {
                    firebase?.let { f ->
                        imageUrl = f.uploadImage(tempImageUri, "profile_images/$userId.jpg", { progress: Int ->
                            _uploadProgress.postValue(progress)
                        })
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

                // remote uniqueness guard
                val candidate = updated.username
                if (candidate.isNotBlank()) {
                    val taken = userRepo.isUsernameTakenRemote(candidate, userId)
                    if (taken) {
                        _loading.postValue(false)
                        onComplete(false, "Username already taken")
                        return@launch
                    }
                }

                userRepo.upsertUser(updated, pushToRemote = true)

                // best-effort remote auth profile update
                runCatching {
                    val fb = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    fb?.let { fbUser ->
                        val req = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(updated.name)
                            .setPhotoUri(if (imageUrl.isNotBlank()) Uri.parse(imageUrl) else null)
                            .build()
                        // Use withContext to call updateProfile and then await if possible
                        runCatching {
                            fbUser.updateProfile(req)
                        }
                    }
                }

                _loading.postValue(false)
                _uploadProgress.postValue(0)
                onComplete(true, null)
            } catch (e: Exception) {
                Log.e("EditProfileVM", "saveProfile failed", e)
                _loading.postValue(false)
                _uploadProgress.postValue(0)
                onComplete(false, e.localizedMessage ?: "Failed to save")
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
                Log.e("EditProfileVM", "delete failed", e)
                onComplete(false, e.localizedMessage ?: "Failed to delete")
            }
        }
    }
}
