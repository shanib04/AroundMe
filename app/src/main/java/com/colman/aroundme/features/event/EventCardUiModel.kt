package com.colman.aroundme.features.event
import java.util.Locale
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
fun com.colman.aroundme.data.model.Event.toEventCardUiModel(): EventCardUiModel {
    val normalizedTags = this.tags.filter { tag -> tag.isNotBlank() }
    val safePublisherId = this.publisherId
    val hostNameValue = when {
        safePublisherId.isBlank() -> "@AroundMeHost"
        safePublisherId.startsWith("@") -> safePublisherId
        else -> "@$safePublisherId"
    }
    val likes = if (this.activeVotes > 0) this.activeVotes else 1200
    val comments = if (this.inactiveVotes > 0) this.inactiveVotes else 45
    return EventCardUiModel(
        id = this.id,
        hostName = hostNameValue,
        hostSubtitle = this.category.ifBlank { "Event Host" },
        distanceText = if (this.locationName.isBlank()) "0.5km" else this.locationName,
        title = this.title.ifBlank { "Untitled Event" },
        description = this.description.ifBlank { "Something interesting is happening nearby." },
        primaryTag = normalizedTags.getOrNull(0)?.let { "#${it.trim().replace(" ", "")}" } ?: "#AroundMe",
        secondaryTag = normalizedTags.getOrNull(1)?.let { "#${it.trim().replace(" ", "")}" } ?: "#StreetFood",
        tertiaryTag = normalizedTags.getOrNull(2)?.let { "#${it.trim().replace(" ", "")}" } ?: "#LiveMusic",
        likesText = if (likes >= 1000) String.format(Locale.US, "%.1fk", likes / 1000f) else likes.toString(),
        commentsText = comments.toString(),
        postedText = formatPostedTime(this.publishTime),
        statusText = if (this.timeRemaining.isBlank()) { if (this.isEnded) "Ended" else "Live now" } else this.timeRemaining,
        imageUrl = this.imageUrl
    )
}
private fun formatPostedTime(timestamp: Long): String {
    val elapsedMillis = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val minutes = elapsedMillis / 60000L
    return when {
        minutes < 1L -> "POSTED JUST NOW"
        minutes < 60L -> "POSTED ${minutes}M AGO"
        minutes < 1440L -> "POSTED ${minutes / 60L}H AGO"
        else -> "POSTED ${minutes / 1440L}D AGO"
    }
}