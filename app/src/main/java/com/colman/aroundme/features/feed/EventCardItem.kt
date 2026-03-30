package com.colman.aroundme.features.feed

import androidx.core.view.isVisible
import com.colman.aroundme.R
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.MapCoordinate
import com.colman.aroundme.data.model.User
import com.colman.aroundme.data.model.versionedProfileImageUrl
import com.colman.aroundme.databinding.ViewEventCardBinding
import com.squareup.picasso.Picasso
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class EventCardItem(
    val event: Event,
    val hostName: String,
    val hostSubtitle: String,
    val hostAvatarUrl: String,
    val locationText: String,
    val distanceLabelText: String,
    val statusText: String,
    val activeVotesText: String,
    val inactiveVotesText: String,
    val averageRatingText: String,
    val postedText: String,
    val tagLabels: List<String>
) {
    fun bindSharedContent(binding: ViewEventCardBinding) {
        binding.hostNameText.text = hostName
        binding.hostSubtitleText.text = hostSubtitle
        bindHostAvatar(binding)
        binding.locationText.text = locationText
        binding.distanceBadgeText.text = distanceLabelText
        binding.statusBadgeText.text = statusText
        binding.activeVotesText.text = activeVotesText
        binding.inactiveVotesText.text = inactiveVotesText
        binding.averageRatingText.text = averageRatingText
        binding.postedTimeText.text = postedText
        bindTags(binding)
        bindImage(binding)
    }

    private fun bindTags(binding: ViewEventCardBinding) {
        val chips = listOf(binding.primaryTagChip, binding.secondaryTagChip, binding.tertiaryTagChip)
        chips.forEachIndexed { index, chip ->
            val text = tagLabels.getOrNull(index)
            chip.isVisible = text != null
            chip.text = text ?: ""
        }

        if (tagLabels.isEmpty()) {
            binding.primaryTagChip.isVisible = true
            binding.primaryTagChip.text = binding.root.context.getString(R.string.feed_default_tag)
            binding.secondaryTagChip.isVisible = false
            binding.tertiaryTagChip.isVisible = false
        }
    }

    private fun bindHostAvatar(binding: ViewEventCardBinding) {
        if (hostAvatarUrl.isBlank()) {
            binding.hostAvatarImageView.setImageResource(R.drawable.ic_person_placeholder)
            return
        }

        Picasso.get()
            .load(hostAvatarUrl)
            .placeholder(R.drawable.ic_person_placeholder)
            .error(R.drawable.ic_person_placeholder)
            .fit()
            .centerCrop()
            .into(binding.hostAvatarImageView)
    }

    private fun bindImage(binding: ViewEventCardBinding) {
        if (event.imageUrl.isBlank()) {
            binding.eventImageView.setImageResource(R.drawable.bg_register_image_placeholder)
            return
        }

        Picasso.get()
            .load(event.imageUrl)
            .placeholder(R.drawable.bg_register_image_placeholder)
            .error(R.drawable.bg_register_image_placeholder)
            .fit()
            .centerCrop()
            .into(binding.eventImageView)
    }
}

object EventCardItemMapper {

    fun fromEvent(
        event: Event,
        user: User?,
        distanceLabelText: String,
        statusText: String,
        postedText: String
    ): EventCardItem {
        return EventCardItem(
            event = event,
            hostName = user?.displayName?.takeIf { it.isNotBlank() } ?: EventTextFormatter.unknownPublisherText(),
            hostSubtitle = event.category.ifBlank { EventTextFormatter.eventHostFallbackText() },
            hostAvatarUrl = user?.versionedProfileImageUrl().orEmpty(),
            locationText = event.locationName.ifBlank { EventTextFormatter.unknownLocationText() },
            distanceLabelText = distanceLabelText,
            statusText = statusText,
            activeVotesText = formatCompactCount(event.activeVotes),
            inactiveVotesText = formatCompactCount(event.inactiveVotes),
            averageRatingText = EventTextFormatter.compactRatingText(event),
            postedText = postedText,
            tagLabels = EventTextFormatter.cardTagLabels(event.tags)
        )
    }

    fun distanceLabelText(event: Event, origin: MapCoordinate): String {
        return formatDistance(distanceKm(origin, MapCoordinate(event.latitude, event.longitude)))
    }

    private fun distanceKm(start: MapCoordinate, end: MapCoordinate): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(end.latitude - start.latitude)
        val dLon = Math.toRadians(end.longitude - start.longitude)
        val lat1 = Math.toRadians(start.latitude)
        val lat2 = Math.toRadians(end.latitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
            sin(dLon / 2) * sin(dLon / 2) * cos(lat1) * cos(lat2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusKm * c
    }

    private fun formatDistance(distanceKm: Double): String {
        return if (distanceKm < 1.0) {
            "${(distanceKm * 1000).toInt()}m"
        } else {
            String.format(Locale.US, "%.1fkm", distanceKm)
        }
    }

    private fun formatCompactCount(value: Int): String {
        return if (value >= 1000) {
            String.format(Locale.US, "%.1fk", value / 1000f)
        } else {
            value.toString()
        }
    }
}
