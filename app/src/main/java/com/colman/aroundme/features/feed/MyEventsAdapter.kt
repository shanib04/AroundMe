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
import com.squareup.picasso.Picasso

class MyEventsAdapter(
    private val onEditClick: (String) -> Unit,
    private val onRecreateClick: (String) -> Unit
) : ListAdapter<MyEventItem, MyEventsAdapter.MyEventViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyEventViewHolder {
        val binding = ViewEventCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MyEventViewHolder(binding, onEditClick, onRecreateClick)
    }

    override fun onBindViewHolder(holder: MyEventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MyEventViewHolder(
        private val binding: ViewEventCardBinding,
        private val onEditClick: (String) -> Unit,
        private val onRecreateClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MyEventItem) {
            val event = item.event
            binding.hostNameText.text = item.publisherDisplayName
            binding.hostSubtitleText.text = item.publisherUsername.ifBlank { event.category.ifBlank { "Your Event" } }
            binding.locationText.text = event.locationName
            binding.distanceBadgeText.text = if (event.ratingCount > 0) {
                String.format(java.util.Locale.US, "%.1f★", event.averageRating)
            } else {
                binding.root.context.getString(R.string.event_rating_empty)
            }
            binding.statusBadgeText.text = if (event.isEnded) {
                binding.root.context.getString(R.string.feed_status_ended)
            } else {
                binding.root.context.getString(R.string.feed_status_live)
            }
            binding.eventTitleText.text = event.title
            binding.eventDescriptionText.text = event.description
            binding.eventDescriptionText.isVisible = event.description.isNotBlank()
            binding.activeVotesText.text = event.activeVotes.toString()
            binding.inactiveVotesText.text = event.inactiveVotes.toString()
            binding.averageRatingText.text = if (event.ratingCount > 0) {
                String.format(java.util.Locale.US, "%.1f★", event.averageRating)
            } else {
                "New"
            }
            binding.postedTimeText.text = if (event.isEnded) {
                binding.root.context.getString(R.string.my_events_recreate)
            } else {
                binding.root.context.getString(R.string.my_events_edit)
            }

            binding.moreButton.isVisible = false
            binding.activeVotesButton.isVisible = false
            binding.inactiveVotesButton.isVisible = false
            binding.ownerActionButton.isVisible = true
            val isEnded = event.isEnded
            binding.ownerActionButton.text = if (isEnded) {
                binding.root.context.getString(R.string.my_events_recreate)
            } else {
                binding.root.context.getString(R.string.my_events_edit)
            }
            binding.ownerActionButton.setBackgroundColor(
                ContextCompat.getColor(
                    binding.root.context,
                    if (isEnded) R.color.my_events_recreate_button else R.color.my_events_edit_button
                )
            )
            binding.ownerActionButton.setTextColor(
                ContextCompat.getColor(
                    binding.root.context,
                    if (isEnded) R.color.my_events_recreate_button_text else R.color.my_events_edit_button_text
                )
            )
            binding.ownerActionButton.setOnClickListener {
                if (isEnded) onRecreateClick(event.id) else onEditClick(event.id)
            }

            val chips = listOf(binding.primaryTagChip, binding.secondaryTagChip, binding.tertiaryTagChip)
            chips.forEachIndexed { index, chip ->
                val text = event.tags.getOrNull(index)?.let { "#${it.trim().replace(" ", "")}" }
                chip.isVisible = text != null
                chip.text = text ?: ""
            }
            if (event.tags.isEmpty()) {
                binding.primaryTagChip.isVisible = true
                binding.primaryTagChip.text = binding.root.context.getString(R.string.feed_default_tag)
                binding.secondaryTagChip.isVisible = false
                binding.tertiaryTagChip.isVisible = false
            }

            if (event.imageUrl.isBlank()) {
                binding.eventImageView.setImageResource(R.drawable.bg_register_image_placeholder)
            } else {
                Picasso.get()
                    .load(event.imageUrl)
                    .placeholder(R.drawable.bg_register_image_placeholder)
                    .error(R.drawable.bg_register_image_placeholder)
                    .fit()
                    .centerCrop()
                    .into(binding.eventImageView)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<MyEventItem>() {
        override fun areItemsTheSame(oldItem: MyEventItem, newItem: MyEventItem): Boolean = oldItem.event.id == newItem.event.id
        override fun areContentsTheSame(oldItem: MyEventItem, newItem: MyEventItem): Boolean = oldItem == newItem
    }
}
