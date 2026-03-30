package com.colman.aroundme.features.profile

import com.colman.aroundme.R
import com.colman.aroundme.data.model.Achievement

fun backgroundForAchievement(achievement: Achievement): Int {
    val name = achievement.name.lowercase()
    return when {
        name.contains("rising") ||
            name.contains("legend") ||
            name.contains("fresh face") ||
            name.contains("making waves") -> R.drawable.ach_bg_orange

        name.contains("trustworthy") ||
            name.contains("oracle") ||
            name.contains("fact checker") ||
            name.contains("truth seeker") -> R.drawable.ach_bg_blue

        name.contains("crowd favorite") ||
            name.contains("crowd pleaser") -> R.drawable.ach_bg_orange

        else -> R.drawable.ach_bg_purple
    }
}
