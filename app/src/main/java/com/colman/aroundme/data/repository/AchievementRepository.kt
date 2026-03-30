package com.colman.aroundme.data.repository

import android.app.Application
import com.colman.aroundme.R
import com.colman.aroundme.data.model.Achievement
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.User
import com.colman.aroundme.data.remote.FirebaseModel
import kotlinx.coroutines.flow.first

class AchievementRepository private constructor(
    private val application: Application,
    private val userRepository: UserRepository
) {
    private val firebase by lazy { FirebaseModel.getInstance() }

    suspend fun unlockFreshFace(userId: String) {
        val normalizedUserId = userId.trim()
        if (normalizedUserId.isBlank()) return
        val user = loadPersistedUser(normalizedUserId)
        val freshFaceName = application.getString(R.string.achievement_fresh_face)
        if (user.achievementHistory.any { it.name == freshFaceName }) return

        val now = System.currentTimeMillis()
        val updatedUser = user.copy(
            achievementHistory = (user.achievementHistory + Achievement(
                name = freshFaceName,
                icon = "🐣",
                description = application.getString(R.string.achievement_fresh_face_description),
                unlockedAt = now
            )).sortedByDescending { it.unlockedAt },
            lastUpdated = now
        )
        userRepository.updateUserProfile(updatedUser, pushToRemote = true)
    }

    suspend fun unlockForCreatedEvent(userId: String) {
        val normalizedUserId = userId.trim()
        if (normalizedUserId.isBlank()) return
        val user = loadPersistedUser(normalizedUserId)
        persistAchievements(
            user = user,
            eventCount = user.eventsPublishedCount,
            validationCount = user.validationsMadeCount,
            points = user.points,
            eventDrivenAchievements = emptyList(),
            preserveUpdatedCounts = false,
            includeFreshFace = false
        )
    }

    suspend fun unlockForValidation(userId: String) {
        val normalizedUserId = userId.trim()
        if (normalizedUserId.isBlank()) return
        val user = loadPersistedUser(normalizedUserId)
        persistAchievements(
            user = user,
            eventCount = user.eventsPublishedCount,
            validationCount = user.validationsMadeCount,
            points = user.points,
            eventDrivenAchievements = emptyList(),
            preserveUpdatedCounts = false,
            includeFreshFace = false
        )
    }

    suspend fun unlockForPublisherEventState(event: Event) {
        val publisherId = event.publisherId.trim()
        if (publisherId.isBlank()) return
        val user = loadPersistedUser(publisherId)
        val eventDrivenAchievements = buildEventDrivenAchievements(event)
        persistAchievements(
            user = user,
            eventCount = user.eventsPublishedCount,
            validationCount = user.validationsMadeCount,
            points = user.points,
            eventDrivenAchievements = eventDrivenAchievements,
            preserveUpdatedCounts = false,
            includeFreshFace = false
        )
    }

    private suspend fun loadPersistedUser(userId: String): User {
        val localUser = userRepository.getUserById(userId).first()
        if (localUser != null) {
            return localUser
        }

        val remoteUser = runCatching { firebase.fetchUserById(userId) }.getOrNull()
        if (remoteUser != null) {
            userRepository.upsertUser(remoteUser, pushToRemote = false)
            return remoteUser
        }

        return User(id = userId)
    }

    private suspend fun persistAchievements(
        user: User,
        eventCount: Int,
        validationCount: Int,
        points: Int,
        eventDrivenAchievements: List<Achievement>,
        preserveUpdatedCounts: Boolean,
        includeFreshFace: Boolean
    ) {
        val existingHistory = user.achievementHistory
        val existingNames = existingHistory.mapTo(linkedSetOf()) { it.name }
        val unlocked = mutableListOf<Achievement>()

        fun maybeAdd(name: String, icon: String, description: String, condition: Boolean) {
            if (condition && existingNames.add(name)) {
                unlocked += Achievement(name = name, icon = icon, description = description)
            }
        }

        if (includeFreshFace) {
            maybeAdd(
                application.getString(R.string.achievement_fresh_face),
                "🐣",
                application.getString(R.string.achievement_fresh_face_description),
                true
            )
        }
        maybeAdd(
            application.getString(R.string.achievement_first_event),
            "🎈",
            application.getString(R.string.achievement_first_event_description),
            eventCount >= 1
        )
        maybeAdd(
            application.getString(R.string.achievement_making_waves),
            "🌊",
            application.getString(R.string.achievement_making_waves_description),
            eventCount >= 3
        )
        maybeAdd(
            application.getString(R.string.achievement_rising_star),
            "⭐",
            application.getString(R.string.achievement_rising_star_description),
            eventCount >= 5
        )
        maybeAdd(
            application.getString(R.string.achievement_community_legend),
            "👑",
            application.getString(R.string.achievement_community_legend_description),
            eventCount >= 10
        )
        maybeAdd(
            application.getString(R.string.achievement_fact_checker),
            "✅",
            application.getString(R.string.achievement_fact_checker_description),
            validationCount >= 1
        )
        maybeAdd(
            application.getString(R.string.achievement_truth_seeker),
            "🧭",
            application.getString(R.string.achievement_truth_seeker_description),
            validationCount >= 10
        )
        maybeAdd(
            application.getString(R.string.achievement_trustworthy),
            "🛡️",
            application.getString(R.string.achievement_trustworthy_description),
            validationCount >= 20
        )
        maybeAdd(
            application.getString(R.string.achievement_oracle),
            "🔮",
            application.getString(R.string.achievement_oracle_description),
            validationCount >= 50
        )
        maybeAdd(
            application.getString(R.string.achievement_socialite),
            "🎉",
            application.getString(R.string.achievement_socialite_description),
            points >= 500
        )

        eventDrivenAchievements.forEach { achievement ->
            if (existingNames.add(achievement.name)) {
                unlocked += achievement
            }
        }

        if (unlocked.isEmpty() && !preserveUpdatedCounts) return

        val now = System.currentTimeMillis()
        val mergedHistory = (existingHistory + unlocked.map { it.copy(unlockedAt = now) })
            .associateBy { it.name }
            .values
            .sortedByDescending { it.unlockedAt }

        val updatedUser = if (preserveUpdatedCounts) {
            user.copy(
                achievementHistory = mergedHistory,
                points = points,
                eventsPublishedCount = eventCount,
                validationsMadeCount = validationCount,
                lastUpdated = now
            )
        } else {
            user.copy(
                achievementHistory = mergedHistory,
                lastUpdated = now
            )
        }

        userRepository.updateUserProfile(updatedUser, pushToRemote = true)
    }

    private fun buildEventDrivenAchievements(event: Event): List<Achievement> {
        val achievements = mutableListOf<Achievement>()
        if (event.activeVotes >= 10) {
            achievements += Achievement(
                name = application.getString(R.string.achievement_crowd_favorite),
                icon = "🌟",
                description = application.getString(R.string.achievement_crowd_favorite_description)
            )
        }
        if (event.activeVotes >= 25) {
            achievements += Achievement(
                name = application.getString(R.string.achievement_crowd_pleaser),
                icon = "🔥",
                description = application.getString(R.string.achievement_crowd_pleaser_description)
            )
        }
        if (event.activeVotes > event.inactiveVotes && event.activeVotes > 0) {
            achievements += Achievement(
                name = application.getString(R.string.achievement_verified_source),
                icon = "📡",
                description = application.getString(R.string.achievement_verified_source_description)
            )
        }
        if (event.ratingCount >= 3 && event.averageRating > 4.5) {
            achievements += Achievement(
                name = application.getString(R.string.achievement_gold_standard),
                icon = "🥇",
                description = application.getString(R.string.achievement_gold_standard_description)
            )
        }
        return achievements
    }

    companion object {
        @Volatile
        private var INSTANCE: AchievementRepository? = null

        fun getInstance(application: Application): AchievementRepository = INSTANCE ?: synchronized(this) {
            val repo = AchievementRepository(application, UserRepository.getInstance(application))
            INSTANCE = repo
            repo
        }
    }
}
