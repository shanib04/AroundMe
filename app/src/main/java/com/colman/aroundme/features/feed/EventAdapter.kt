package com.colman.aroundme.features.feed

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.colman.aroundme.R
import com.colman.aroundme.databinding.ViewEventCardBinding
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso

enum class FeedSortOption(val label: String) {
    DISTANCE("Distance"),
    ENDING_SOON("Ending Soon"),
    NEWEST("Newest")
}

class EventAdapter(
    private val onEventClick: (String) -> Unit,
    private val onVoteClick: (String, Boolean) -> Unit
) : ListAdapter<FeedEventItem, EventAdapter.EventViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ViewEventCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventViewHolder(binding, onEventClick, onVoteClick)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class EventViewHolder(
        private val binding: ViewEventCardBinding,
        private val onEventClick: (String) -> Unit,
        private val onVoteClick: (String, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(feedItem: FeedEventItem) {
            val item = feedItem.item
            val event = item.event
            binding.hostNameText.text = item.hostName
            binding.hostSubtitleText.text = item.hostSubtitle
            bindHostAvatar(item.hostAvatarUrl)
            binding.locationText.text = item.locationText
            binding.distanceBadgeText.text = feedItem.distanceText
            binding.statusBadgeText.text = item.statusText
            binding.eventTitleText.text = event.title.ifBlank { binding.root.context.getString(R.string.feed_empty_state_title) }
            binding.eventDescriptionText.text = event.description.ifBlank {
                binding.root.context.getString(R.string.feed_empty_state_description)
            }
            binding.activeVotesText.text = item.activeVotesText
            binding.inactiveVotesText.text = item.inactiveVotesText
            binding.postedTimeText.text = item.postedText
            binding.averageRatingText.text = item.averageRatingText

            bindTags(item.tagLabels)
            bindImage(event.imageUrl)
            binding.eventDescriptionText.isVisible = event.description.isNotBlank()
            updateVoteSelection(feedItem)

            binding.root.setOnClickListener { onEventClick(event.id) }
            binding.moreButton.setOnClickListener { onEventClick(event.id) }
            binding.activeVotesButton.setOnClickListener { onVoteClick(event.id, true) }
            binding.inactiveVotesButton.setOnClickListener { onVoteClick(event.id, false) }
        }

        private fun updateVoteSelection(item: FeedEventItem) {
            val selectedBackground = R.drawable.bg_live_badge_card
            val defaultBackground = R.drawable.pill_white_rounded
            val selectedTextColor = ContextCompat.getColor(binding.root.context, android.R.color.black)
            val defaultTextColor = ContextCompat.getColor(binding.root.context, R.color.text_dark)

            binding.activeVotesButton.setBackgroundResource(if (item.isActiveVoteSelected) selectedBackground else defaultBackground)
            binding.inactiveVotesButton.setBackgroundResource(if (item.isInactiveVoteSelected) selectedBackground else defaultBackground)
            binding.activeVotesText.setTextColor(if (item.isActiveVoteSelected) selectedTextColor else defaultTextColor)
            binding.inactiveVotesText.setTextColor(if (item.isInactiveVoteSelected) selectedTextColor else defaultTextColor)
        }

        private fun bindTags(tagLabels: List<String>) {
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

        private fun bindHostAvatar(avatarUrl: String) {
            if (avatarUrl.isBlank()) {
                binding.hostAvatarImageView.setImageResource(R.drawable.ic_person_placeholder)
                return
            }

            Picasso.get()
                .load(avatarUrl)
                .placeholder(R.drawable.ic_person_placeholder)
                .error(R.drawable.ic_person_placeholder)
                .memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
                .networkPolicy(NetworkPolicy.NO_CACHE)
                .fit()
                .centerCrop()
                .into(binding.hostAvatarImageView)
        }

        private fun bindImage(imageUrl: String) {
            if (imageUrl.isBlank()) {
                binding.eventImageView.setImageResource(R.drawable.bg_register_image_placeholder)
                return
            }

            Picasso.get()
                .load(imageUrl)
                .placeholder(R.drawable.bg_register_image_placeholder)
                .error(R.drawable.bg_register_image_placeholder)
                .fit()
                .centerCrop()
                .into(binding.eventImageView)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<FeedEventItem>() {
        override fun areItemsTheSame(oldItem: FeedEventItem, newItem: FeedEventItem): Boolean {
            return oldItem.item.event.id == newItem.item.event.id
        }

        override fun areContentsTheSame(oldItem: FeedEventItem, newItem: FeedEventItem): Boolean {
            return oldItem == newItem
        }
    }
}
