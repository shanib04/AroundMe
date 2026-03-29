package com.colman.aroundme.features.feed

import com.colman.aroundme.data.model.Event
import java.util.Locale

private const val UNKNOWN_PUBLISHER = "Unknown Publisher"
private const val UNKNOWN_LOCATION = "Unknown location"
private const val EVENT_HOST = "Event Host"
private const val EVENT_LIVE = "Live"
private const val EVENT_ENDED = "Ended"
private const val NO_RATINGS_YET = "No ratings yet"

object EventTextFormatter {

    fun unknownPublisherText(): String = UNKNOWN_PUBLISHER
    fun unknownLocationText(): String = UNKNOWN_LOCATION
    fun eventHostFallbackText(): String = EVENT_HOST

    fun statusText(event: Event): String {
        return when {
            event.isEnded -> EVENT_ENDED
            event.expirationTime <= 0L -> EVENT_LIVE
            else -> event.timeRemaining
                .trim()
                .takeIf(::isRemainingTimeLabel)
                ?: buildStatusFromExpiration(event.expirationTime)
        }
    }

    fun ratingSummaryText(event: Event): String {
        return if (event.ratingCount <= 0) {
            NO_RATINGS_YET
        } else {
            String.format(Locale.US, "%.1f average • %d ratings", event.averageRating, event.ratingCount)
        }
    }

    fun compactRatingText(event: Event): String {
        return if (event.ratingCount <= 0) {
            NO_RATINGS_YET
        } else {
            String.format(Locale.US, "%.1f", event.averageRating)
        }
    }

    fun postedTimeText(timestamp: Long): String {
        val elapsedMinutes = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L)) / 60000L
        return when {
            elapsedMinutes < 1L -> "POSTED JUST NOW"
            elapsedMinutes < 60L -> "POSTED ${elapsedMinutes}M AGO"
            elapsedMinutes < 1440L -> "POSTED ${elapsedMinutes / 60L}H AGO"
            else -> "POSTED ${elapsedMinutes / 1440L}D AGO"
        }
    }

    fun cardTagLabels(tags: List<String>, maxCount: Int = 3): List<String> {
        return tags
            .mapNotNull { raw ->
                raw.trim().takeIf { it.isNotBlank() }?.replace(" ", "")?.let { "#$it" }
            }
            .take(maxCount)
    }

    private fun buildStatusFromExpiration(expirationTime: Long): String {
        val remainingMinutes = ((expirationTime - System.currentTimeMillis()) / 60000L).coerceAtLeast(0L)
        return when {
            remainingMinutes < 60L -> "Ends in ${remainingMinutes}m"
            remainingMinutes < 1440L -> "Ends in ${remainingMinutes / 60L}h"
            else -> "Ends in ${remainingMinutes / 1440L}d"
        }
    }

    private fun isRemainingTimeLabel(value: String): Boolean {
        val normalized = value.lowercase(Locale.US)
        return normalized.startsWith("ends in") || Regex("^\\d+\\s*[mhd]$", RegexOption.IGNORE_CASE).matches(value)
    }
}
