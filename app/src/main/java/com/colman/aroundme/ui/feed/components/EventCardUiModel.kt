package com.colman.aroundme.ui.feed.components

import java.util.Locale
import com.colman.aroundme.data.model.Event

data class EventCardUiModel(
    val id: String,
    val hostName: String,
    val hostSubtitle: String,
    val distanceText: String,
    val title: String,
    val description: String,
    val primaryTag: String,
    val secondaryTag: String,
    val tertiaryTag: String,
    val likesText: String,
    val commentsText: String,
    val postedText: String,
    val statusText: String,
    val imageUrl: String
)

fun Event.toEventCardUiModel(): EventCardUiModel {
    val normalizedTags = tags.filter { it.isNotBlank() }
    val hostHandle = publisherId.ifBlank { "aroundme" }
    val likes = if (activeVotes > 0) activeVotes else 1200
    val comments = if (inactiveVotes > 0) inactiveVotes else 45

    return EventCardUiModel(
        id = id,
        hostName = "@${hostHandle.takeIf { !it.startsWith("@") }.orEmpty().ifBlank { "AroundMeHost" }}",
        hostSubtitle = category.ifBlank { "Event Host" },
        distanceText = buildDistanceText(locationName),
        title = title.ifBlank { "Untitled Event" },
        description = description.ifBlank { "Something interesting is happening nearby." },
        primaryTag = normalizedTags.getOrNull(0)?.asChipLabel() ?: category.asChipLabel("#AroundMe"),
        secondaryTag = normalizedTags.getOrNull(1)?.asChipLabel() ?: "#StreetFood",
        tertiaryTag = normalizedTags.getOrNull(2)?.asChipLabel() ?: "#LiveMusic",
        likesText = formatCompactNumber(likes),
        commentsText = comments.toString(),
        postedText = formatPostedTime(publishTime),
        statusText = timeRemaining.ifBlank { if (isEnded) "Ended" else "Live now" },
        imageUrl = imageUrl
    )
}

private fun String.asChipLabel(defaultValue: String = "#Event"): String {
    val cleaned = trim().replace(" ", "")
    return if (cleaned.isBlank()) defaultValue else "#${cleaned}"
}

private fun buildDistanceText(locationName: String): String {
    return if (locationName.isBlank()) "0.5km" else locationName
}

private fun formatCompactNumber(value: Int): String {
    return when {
        value >= 1000 -> String.format(Locale.US, "%.1fk", value / 1000f)
        else -> value.toString()
    }
}

private fun formatPostedTime(timestamp: Long): String {
    val elapsedMillis = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val minutes = elapsedMillis / 60_000L
    return when {
        minutes < 1L -> "POSTED JUST NOW"
        minutes < 60L -> "POSTED ${minutes}M AGO"
        minutes < 1_440L -> "POSTED ${minutes / 60L}H AGO"
        else -> "POSTED ${minutes / 1_440L}D AGO"
    }
}
