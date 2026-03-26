package com.colman.aroundme.features.feed

import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.User
import java.util.Locale

data class EventCardItem(
    val event: Event,
    val hostName: String,
    val hostSubtitle: String,
    val locationText: String,
    val statusText: String,
    val activeVotesText: String,
    val inactiveVotesText: String,
    val averageRatingText: String,
    val postedText: String,
    val tagLabels: List<String>
)

object EventCardItemMapper {

    fun fromEvent(
        event: Event,
        user: User?,
        statusText: String,
        postedText: String
    ): EventCardItem {
        return EventCardItem(
            event = event,
            hostName = user?.displayName?.takeIf { it.isNotBlank() } ?: EventTextFormatter.unknownPublisherText(),
            hostSubtitle = user?.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
                ?: event.category.ifBlank { EventTextFormatter.eventHostFallbackText() },
            locationText = event.locationName.ifBlank { EventTextFormatter.unknownLocationText() },
            statusText = statusText,
            activeVotesText = formatCompactCount(event.activeVotes),
            inactiveVotesText = formatCompactCount(event.inactiveVotes),
            averageRatingText = EventTextFormatter.compactRatingText(event),
            postedText = postedText,
            tagLabels = EventTextFormatter.cardTagLabels(event.tags)
        )
    }

    private fun formatCompactCount(value: Int): String {
        return if (value >= 1000) {
            String.format(Locale.US, "%.1fk", value / 1000f)
        } else {
            value.toString()
        }
    }
}
