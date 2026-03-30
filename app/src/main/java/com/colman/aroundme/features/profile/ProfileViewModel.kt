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
import com.colman.aroundme.data.model.Achievement
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.User
import com.colman.aroundme.data.repository.AuthRepository
import com.colman.aroundme.data.repository.EventRepository
import com.colman.aroundme.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val userRepo = UserRepository.getInstance(application)
    private val eventRepo = EventRepository.getInstance(application)
    private val authRepo = AuthRepository()

    private var currentUserId: String = ""
    private var currentUser: User? = null
    private var currentEvents: List<Event> = emptyList()
    private var knownUsers: List<User> = emptyList()
    private var remoteLeaderboardUsers: List<User> = emptyList()
    private var userObserverSource: LiveData<User?>? = null
    private var userObserver: Observer<User?>? = null
    private var eventObserverSource: LiveData<List<Event>>? = null
    private var eventObserver: Observer<List<Event>>? = null
    private var allUsersObserverSource: LiveData<List<User>>? = null
    private var allUsersObserver: Observer<List<User>>? = null

    private val _imageUri = MutableLiveData<Uri?>(null)
    val imageUri: LiveData<Uri?> = _imageUri

    private val _username = MutableLiveData("")
    val username: LiveData<String> = _username

    private val _displayName = MutableLiveData("")
    val displayName: LiveData<String> = _displayName

    private val _isReliableContributor = MutableLiveData(false)
    val isReliableContributor: LiveData<Boolean> = _isReliableContributor

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

    private val _completionPercentText = MutableLiveData("0%")
    val completionPercentText: LiveData<String> = _completionPercentText

    private val _pointsSummaryText = MutableLiveData("0 total points")
    val pointsSummaryText: LiveData<String> = _pointsSummaryText

    private var radiusSaveJob: Job? = null
    private var statsComputationJob: Job? = null

    private val _logoutState = MutableLiveData<LogoutState>(LogoutState.Idle)
    val logoutState: LiveData<LogoutState> = _logoutState

    sealed interface LogoutState {
        data object Idle : LogoutState
        data object Loading : LogoutState
        data object Success : LogoutState
        data class Error(val message: String) : LogoutState
    }

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
                computeStatsAndPost(currentEvents, currentUser)
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
            computeStatsAndPost(currentEvents, currentUser)
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

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            _logoutState.postValue(LogoutState.Loading)
            try {
                clearObserversOnMainThread()
                authRepo.logout()
                userRepo.clearAllLocal()
                _logoutState.postValue(LogoutState.Success)
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "logout failed", e)
                _logoutState.postValue(LogoutState.Error(e.localizedMessage ?: "Logout failed"))
            }
        }
    }

    fun consumeLogoutState() {
        _logoutState.value = LogoutState.Idle
    }

    private suspend fun clearObserversOnMainThread() {
        withContext(Dispatchers.Main.immediate) {
            clearObservers()
        }
    }

    private fun clearObservers() {
        userObserverSource?.let { source -> userObserver?.let(source::removeObserver) }
        eventObserverSource?.let { source -> eventObserver?.let(source::removeObserver) }
        userObserverSource = null
        userObserver = null
        eventObserverSource = null
        eventObserver = null
        allUsersObserverSource = null
        allUsersObserver = null
        currentEvents = emptyList()
        currentUser = null
        knownUsers = emptyList()
        remoteLeaderboardUsers = emptyList()
        statsComputationJob?.cancel()
        statsComputationJob = null
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
            _radiusKm.postValue(15)
        }
        _isReliableContributor.postValue(false)
        _eventsCreated.postValue(0)
        _totalValidations.postValue(0)
        _calculatedPoints.postValue(0)
        _pointsSummaryText.postValue("0 total points")
        _completionPercentText.postValue("0%")
        _progressPercent.postValue(0f)
        _levelLabel.postValue("LEVEL 1")
        _progressText.postValue("100 pts to Level 2")
        _achievements.postValue(emptyList())
        _achievementHistory.postValue(emptyList())
    }

    private fun computeStatsAndPost(events: List<Event>, user: User?) {
        statsComputationJob?.cancel()
        val requestedUserId = currentUserId
        statsComputationJob = viewModelScope.launch(Dispatchers.IO) {
            val userId = authRepo.getCurrentUser()?.uid.orEmpty()
            val derivedValidations = userId.takeIf { it.isNotBlank() }
                ?.let { runCatching { eventRepo.getValidationCountForUser(it) }.getOrDefault(0) }
                ?: 0
            val realEventCount = events.size
            val safeUser = (user ?: authFallbackUser()) ?: User(id = requestedUserId)
            val realValidations = maxOf(safeUser.validationsMadeCount, derivedValidations)
            val expectedPoints = (realEventCount * EVENT_CREATED_POINTS) + (realValidations * VALIDATION_POINTS)
            val points = maxOf(safeUser.points, expectedPoints)
            val totalActiveVotes = events.sumOf { it.activeVotes }
            val totalInactiveVotes = events.sumOf { it.inactiveVotes }
            val isReliableContributor = realEventCount > 0 && totalActiveVotes > 0 && totalInactiveVotes == 0

            if (requestedUserId != currentUserId) return@launch

            val levelProgress = calculateLevelProgress(points)
            val levelPercentText = "${(levelProgress.progressPercent * 100).toInt()}%"
            val history = safeUser.achievementHistory.sortedByDescending { it.unlockedAt }

            _eventsCreated.postValue(realEventCount)
            _totalValidations.postValue(realValidations)
            _calculatedPoints.postValue(points)
            _pointsSummaryText.postValue("$points total points")
            _isReliableContributor.postValue(isReliableContributor)
            _completionPercentText.postValue(levelPercentText)
            _levelLabel.postValue("LEVEL ${levelProgress.level}")
            _progressPercent.postValue(levelProgress.progressPercent)
            _progressText.postValue("${levelProgress.pointsToNextLevel} pts to Level ${levelProgress.nextLevel}")
            _achievementHistory.postValue(history)
            _achievements.postValue(history.take(3))

            val statsChanged = safeUser.eventsPublishedCount != realEventCount ||
                safeUser.validationsMadeCount != realValidations ||
                safeUser.points != points

            if (safeUser.id.isNotBlank() && statsChanged) {
                val updatedUser = safeUser.copy(
                    points = points,
                    eventsPublishedCount = realEventCount,
                    validationsMadeCount = realValidations,
                    lastUpdated = System.currentTimeMillis()
                )
                currentUser = updatedUser
                runCatching {
                    userRepo.updateDerivedStats(
                        userId = updatedUser.id,
                        eventsPublishedCount = realEventCount,
                        validationsMadeCount = realValidations,
                        points = points
                    )
                }
            }
        }
    }

    companion object {
        private const val EVENT_CREATED_POINTS = 10
        private const val VALIDATION_POINTS = 2

        internal data class LevelProgress(
            val level: Int,
            val nextLevel: Int,
            val pointsToNextLevel: Int,
            val progressPercent: Float
        )

        internal fun calculateLevelProgress(points: Int): LevelProgress {
            val safePoints = points.coerceAtLeast(0)
            val completedLevels = safePoints / 100
            val pointsIntoCurrentLevel = safePoints % 100
            return LevelProgress(
                level = completedLevels + 1,
                nextLevel = completedLevels + 2,
                pointsToNextLevel = if (pointsIntoCurrentLevel == 0 && safePoints > 0) 100 else 100 - pointsIntoCurrentLevel,
                progressPercent = pointsIntoCurrentLevel / 100f
            )
        }
    }

    override fun onCleared() {
        radiusSaveJob?.cancel()
        clearObservers()
        super.onCleared()
    }
}
