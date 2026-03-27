package com.colman.aroundme.features.profile

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.colman.aroundme.R
import com.colman.aroundme.data.model.Achievement
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.User
import com.colman.aroundme.data.remote.FirebaseModel
import com.colman.aroundme.data.repository.AuthRepository
import com.colman.aroundme.data.repository.EventRepository
import com.colman.aroundme.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val userRepo = UserRepository.getInstance(application)
    private val eventRepo = EventRepository.getInstance(application)
    private val authRepo = AuthRepository()
    private val firebase by lazy {
        runCatching { FirebaseModel.getInstance() }.getOrNull()
    }

    private var currentUserId: String = ""
    private var currentUser: User? = null
    private var currentEvents: List<Event> = emptyList()
    private var userObserverSource: LiveData<User?>? = null
    private var userObserver: Observer<User?>? = null
    private var eventObserverSource: LiveData<List<Event>>? = null
    private var eventObserver: Observer<List<Event>>? = null

    private val _imageUri = MutableLiveData<Uri?>(null)
    val imageUri: LiveData<Uri?> = _imageUri

    private val _username = MutableLiveData("")
    val username: LiveData<String> = _username

    private val _displayName = MutableLiveData("")
    val displayName: LiveData<String> = _displayName

    private val _isValidator = MutableLiveData(false)
    val isValidator: LiveData<Boolean> = _isValidator

    private val _radiusKm = MutableLiveData(15)
    val radiusKm: LiveData<Int> = _radiusKm

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

    private val _achievements = MutableLiveData<List<Achievement>>(emptyList())
    val achievements: LiveData<List<Achievement>> = _achievements

    private val _achievementHistory = MutableLiveData<List<Achievement>>(emptyList())
    val achievementHistory: LiveData<List<Achievement>> = _achievementHistory

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _uploadProgress = MutableLiveData(0)
    val uploadProgress: LiveData<Int> = _uploadProgress

    private val _completionPercentText = MutableLiveData("0% complete")
    val completionPercentText: LiveData<String> = _completionPercentText

    private val _pointsSummaryText = MutableLiveData("0 total points")
    val pointsSummaryText: LiveData<String> = _pointsSummaryText

    private var radiusSaveJob: kotlinx.coroutines.Job? = null

    fun loadCurrentUser() {
        val authUser = authRepo.getCurrentUser()
        val userId = authUser?.uid.orEmpty()
        if (userId.isBlank()) {
            clearObservers()
            postEmptyProfile()
            return
        }
        if (currentUserId == userId && userObserverSource != null && eventObserverSource != null) {
            viewModelScope.launch(Dispatchers.IO) {
                userRepo.refreshUserFromRemote(userId)
            }
            return
        }

        radiusSaveJob?.cancel()
        clearObservers()
        currentUserId = userId

        userObserverSource = userRepo.getUserById(userId).asLiveData().also { source ->
            val observer = Observer<User?> { user ->
                currentUser = user
                postUser(user)
                computeStatsAndPost(currentEvents, user)
            }
            userObserver = observer
            source.observeForever(observer)
        }

        eventObserverSource = eventRepo.observeEventsByPublisher(userId).also { source ->
            val observer = Observer<List<Event>> { events ->
                currentEvents = events
                computeStatsAndPost(currentEvents, currentUser)
            }
            eventObserver = observer
            source.observeForever(observer)
        }

        viewModelScope.launch(Dispatchers.IO) {
            val localUser = runCatching { userRepo.getUserById(userId).first() }.getOrNull()
            userRepo.refreshUserFromRemote(userId)
            authFallbackUser(localUser)?.let { fallback ->
                val hasIdentity = fallback.displayName.isNotBlank() || fallback.username.isNotBlank()
                if (hasIdentity) {
                    userRepo.upsertUser(fallback, pushToRemote = false)
                }
            }
        }
    }

    fun setRadiusKm(value: Int) {
        _radiusKm.value = value
        val userId = currentUserId
        val existing = currentUser
        if (userId.isBlank() || existing == null) return

        val updated = existing.copy(
            discoveryRadiusKm = value,
            lastUpdated = System.currentTimeMillis()
        )
        currentUser = updated

        radiusSaveJob?.cancel()
        radiusSaveJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching { userRepo.upsertUser(updated, pushToRemote = false) }
            delay(250)
            runCatching { userRepo.updateUserProfile(updated, pushToRemote = true) }
        }
    }

    suspend fun isUsernameUnique(candidate: String, currentUserId: String): Boolean {
        val found = runCatching { userRepo.getUserByUsername(candidate) }.getOrNull()
        return (found == null) || (found.id == currentUserId)
    }

    suspend fun isUsernameTakenRemotely(candidate: String, currentUserId: String): Boolean {
        return userRepo.isUsernameTakenRemote(candidate, currentUserId)
    }

    fun saveProfile(userId: String, tempImageUri: Uri?, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            try {
                var imageUrl = tempImageUri?.toString().orEmpty()
                if (tempImageUri != null && tempImageUri.scheme != "http" && tempImageUri.scheme != "https") {
                    firebase?.let { f ->
                        imageUrl = f.uploadImage(
                            tempImageUri,
                            buildProfileImageRemotePath(userId)
                        ) { progress ->
                            _uploadProgress.postValue(progress)
                        }
                    }
                }

                val existing = currentUser
                val updated = User(
                    id = userId,
                    username = _username.value.orEmpty(),
                    displayName = _displayName.value.orEmpty(),
                    profileImageUrl = imageUrl.ifBlank { existing?.profileImageUrl.orEmpty() },
                    email = existing?.email.orEmpty(),
                    discoveryRadiusKm = _radiusKm.value ?: existing?.discoveryRadiusKm ?: 15,
                    points = existing?.points ?: 0,
                    eventsPublishedCount = existing?.eventsPublishedCount ?: 0,
                    validationsMadeCount = existing?.validationsMadeCount ?: 0,
                    lastUpdated = System.currentTimeMillis()
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

                userRepo.updateUserProfile(updated, pushToRemote = true)

                currentUser = updated
                postUser(updated)
                computeStatsAndPost(currentEvents, updated)
                _loading.postValue(false)
                _uploadProgress.postValue(0)
                onComplete(true, null)

                runCatching {
                    authRepo.getCurrentUser()?.let { fbUser ->
                        if (fbUser.displayName != updated.displayName || fbUser.photoUrl?.toString() != updated.profileImageUrl) {
                            val req = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                .setDisplayName(updated.displayName)
                                .setPhotoUri(updated.profileImageUrl.takeIf(String::isNotBlank)?.toUri())
                                .build()
                            fbUser.updateProfile(req).await()
                        }
                    }
                }.onFailure {
                    Log.w("ProfileViewModel", "Firebase Auth profile sync failed after save", it)
                }
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
                clearObservers()
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
                clearObservers()
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

    private fun clearObservers() {
        userObserverSource?.let { source -> userObserver?.let(source::removeObserver) }
        eventObserverSource?.let { source -> eventObserver?.let(source::removeObserver) }
        userObserverSource = null
        userObserver = null
        eventObserverSource = null
        eventObserver = null
        currentEvents = emptyList()
        currentUser = null
    }

    private fun authFallbackUser(baseUser: User? = currentUser): User? {
        val authUser = authRepo.getCurrentUser() ?: return null
        val emailPrefix = authUser.email
            ?.substringBefore('@')
            ?.trim()
            .orEmpty()
        val authDisplayName = authUser.displayName
            ?.trim()
            .orEmpty()
        val fallbackUsername = emailPrefix
            .ifBlank {
                authDisplayName
                    .lowercase()
                    .replace("[^a-z0-9_]+".toRegex(), "_")
                    .trim('_')
            }
            .ifBlank { "user_${authUser.uid.take(6)}" }
        val fallbackDisplayName = authDisplayName
            .ifBlank { fallbackUsername }

        return User(
            id = authUser.uid,
            username = baseUser?.username?.takeIf { it.isNotBlank() } ?: fallbackUsername,
            displayName = baseUser?.displayName?.takeIf { it.isNotBlank() } ?: fallbackDisplayName,
            profileImageUrl = baseUser?.profileImageUrl?.takeIf { it.isNotBlank() }
                ?: authUser.photoUrl?.toString().orEmpty(),
            email = baseUser?.email?.takeIf { it.isNotBlank() } ?: authUser.email.orEmpty(),
            discoveryRadiusKm = baseUser?.discoveryRadiusKm ?: 15,
            points = baseUser?.points ?: 0,
            eventsPublishedCount = baseUser?.eventsPublishedCount ?: 0,
            validationsMadeCount = baseUser?.validationsMadeCount ?: 0,
            lastUpdated = maxOf(baseUser?.lastUpdated ?: 0L, System.currentTimeMillis())
        )
    }

    private fun postUser(user: User?) {
        val resolvedUser = user ?: authFallbackUser()
        val safeUsername = resolvedUser?.username
            ?.trim()
            .orEmpty()
            .ifBlank { authFallbackUser()?.username.orEmpty() }
        val safeDisplayName = resolvedUser?.displayName
            ?.takeIf { it.isNotBlank() }
            ?: authFallbackUser()?.displayName?.takeIf { it.isNotBlank() }
            ?: safeUsername

        _username.postValue(safeUsername)
        _displayName.postValue(safeDisplayName)
        _isValidator.postValue((resolvedUser?.validationsMadeCount ?: 0) > 0)

        val profileImageUrl = resolvedUser?.profileImageUrl.orEmpty()
            .ifBlank { authFallbackUser()?.profileImageUrl.orEmpty() }
        if (profileImageUrl.isNotBlank()) {
            _imageUri.postValue(profileImageUrl.toUri())
        } else {
            _imageUri.postValue(null)
        }

        _radiusKm.postValue(resolvedUser?.discoveryRadiusKm ?: 15)
    }

    private fun postEmptyProfile() {
        val fallbackUser = authFallbackUser()
        if (fallbackUser != null) {
            postUser(fallbackUser)
        } else {
            _imageUri.postValue(null)
            _username.postValue("")
            _displayName.postValue("")
            _isValidator.postValue(false)
            _radiusKm.postValue(15)
        }
        _eventsCreated.postValue(0)
        _totalValidations.postValue(0)
        _calculatedPoints.postValue(0)
        _pointsSummaryText.postValue("0 total points")
        _completionPercentText.postValue("0% complete")
        _progressPercent.postValue(0f)
        _levelLabel.postValue("LEVEL 1")
        _progressText.postValue("0 pts to Level 2")
        _achievements.postValue(emptyList())
        _achievementHistory.postValue(emptyList())
    }

    private fun computeStatsAndPost(events: List<Event>, user: User?) {
        val realEventCount = events.size
        val realValidations = user?.validationsMadeCount ?: 0
        val points = maxOf(user?.points ?: 0, (realEventCount * 10) + (realValidations * 2))

        _eventsCreated.postValue(realEventCount)
        _totalValidations.postValue(realValidations)
        _calculatedPoints.postValue(points)
        _pointsSummaryText.postValue("$points total points")

        val completionScore = listOf(
            user?.displayName?.isNotBlank() == true,
            user?.username?.isNotBlank() == true,
            user?.profileImageUrl?.isNotBlank() == true,
            realEventCount > 0,
            realValidations > 0
        ).count { it }
        val completionPercent = ((completionScore / 5f) * 100).toInt()
        _completionPercentText.postValue("$completionPercent% complete")

        val level = when {
            points <= 100 -> 1
            else -> ((points - 1) / 100) + 1
        }
        val levelStart = if (level == 1) 0 else ((level - 1) * 100) + 1
        val levelEnd = level * 100
        val pointsIntoLevel = (points - levelStart).coerceAtLeast(0)
        val levelRange = (levelEnd - levelStart + 1).coerceAtLeast(1)
        val pointsToNext = (levelEnd - points).coerceAtLeast(0)

        _levelLabel.postValue("LEVEL $level")
        _progressPercent.postValue((pointsIntoLevel.toFloat() / levelRange.toFloat()).coerceIn(0f, 1f))
        _progressText.postValue("$pointsToNext pts to Level ${level + 1}")

        val safeUser = (user ?: authFallbackUser()) ?: User()
        val calculatedAchievements = calculateAchievements(
            safeUser.copy(
                points = points,
                eventsPublishedCount = realEventCount,
                validationsMadeCount = realValidations
            ),
            events
        )

        val history = mergeAchievementHistory(
            existingHistory = safeUser.achievementHistory,
            currentAchievements = calculatedAchievements
        )
        _achievementHistory.postValue(history)
        _achievements.postValue(history.take(3))

        if (safeUser.id.isNotBlank() && safeUser.achievementHistory != history) {
            val updatedUser = safeUser.copy(
                points = points,
                eventsPublishedCount = realEventCount,
                validationsMadeCount = realValidations,
                lastUpdated = System.currentTimeMillis()
            ).also { it.achievementHistory = history }
            currentUser = updatedUser
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { userRepo.updateUserProfile(updatedUser, pushToRemote = true) }
            }
        }
    }

    fun calculateAchievements(user: User, userEvents: List<Event>): List<Achievement> {
        val postCount = maxOf(user.eventsPublishedCount, userEvents.size)
        val validations = user.validationsMadeCount
        val totalActiveVotes = userEvents.sumOf { it.activeVotes }
        val totalInactiveVotes = userEvents.sumOf { it.inactiveVotes }
        val averageRating = userEvents
            .filter { it.ratingCount > 0 }
            .map { it.averageRating }
            .average()
            .takeIf { !it.isNaN() }
            ?: 0.0
        val leaderboardPoints = maxOf(user.points, (postCount * 10) + (validations * 2))

        val achievements = mutableListOf<Achievement>()

        achievements += when {
            postCount >= 10 -> achievement(
                app.getString(R.string.achievement_community_legend),
                "👑",
                app.getString(R.string.achievement_community_legend_description)
            )
            postCount >= 5 -> achievement(
                app.getString(R.string.achievement_rising_star),
                "⭐",
                app.getString(R.string.achievement_rising_star_description)
            )
            else -> achievement(
                app.getString(R.string.achievement_fresh_face),
                "🐣",
                app.getString(R.string.achievement_fresh_face_description)
            )
        }

        if (validations >= 100) {
            achievements += achievement(
                app.getString(R.string.achievement_oracle),
                "🔮",
                app.getString(R.string.achievement_oracle_description)
            )
        } else if (validations >= 20) {
            achievements += achievement(
                app.getString(R.string.achievement_trustworthy),
                "🛡️",
                app.getString(R.string.achievement_trustworthy_description)
            )
        } else if (validations >= 1) {
            achievements += achievement(
                app.getString(R.string.achievement_fact_checker),
                "✅",
                app.getString(R.string.achievement_fact_checker_description)
            )
        }

        if (userEvents.size >= 3 && averageRating > 4.5) {
            achievements += achievement(
                app.getString(R.string.achievement_gold_standard),
                "🥇",
                app.getString(R.string.achievement_gold_standard_description)
            )
        }

        if (totalActiveVotes >= 25) {
            achievements += achievement(
                app.getString(R.string.achievement_crowd_pleaser),
                "🔥",
                app.getString(R.string.achievement_crowd_pleaser_description)
            )
        }

        if (totalActiveVotes > totalInactiveVotes && totalActiveVotes > 0) {
            achievements += achievement(
                app.getString(R.string.achievement_verified_source),
                "📡",
                app.getString(R.string.achievement_verified_source_description)
            )
        }

        if (leaderboardPoints >= 500) {
            achievements += achievement(
                app.getString(R.string.achievement_socialite),
                "🎉",
                app.getString(R.string.achievement_socialite_description)
            )
        }

        if (isTopContributor(user.id, leaderboardPoints)) {
            achievements += achievement(
                app.getString(R.string.achievement_top_contributor),
                "🏆",
                app.getString(R.string.achievement_top_contributor_description)
            )
        }

        return achievements.distinctBy { it.name }
    }

    private fun mergeAchievementHistory(
        existingHistory: List<Achievement>,
        currentAchievements: List<Achievement>
    ): List<Achievement> {
        val now = System.currentTimeMillis()
        val existingByName = existingHistory.associateBy { it.name }
        val currentEntries = currentAchievements.map { achievement ->
            existingByName[achievement.name] ?: achievement.copy(
                unlockedAt = achievement.unlockedAt.takeIf { it > 0L } ?: now
            )
        }
        return (existingHistory + currentEntries)
            .associateBy { it.name }
            .values
            .sortedByDescending { it.unlockedAt }
    }

    private fun isTopContributor(userId: String, currentPoints: Int): Boolean {
        if (userId.isBlank()) return false
        val allKnownUsers = listOfNotNull(currentUser)
        val maxPoints = allKnownUsers.maxOfOrNull { existingUser ->
            if (existingUser.id == userId) maxOf(existingUser.points, currentPoints) else existingUser.points
        } ?: currentPoints
        return currentPoints >= maxPoints
    }

    private fun achievement(name: String, icon: String, description: String): Achievement =
        Achievement(name = name, icon = icon, description = description)

    private fun buildProfileImageRemotePath(userId: String): String {
        return "profile_images/$userId/${System.currentTimeMillis()}.jpg"
    }

    override fun onCleared() {
        radiusSaveJob?.cancel()
        clearObservers()
        super.onCleared()
    }
}
