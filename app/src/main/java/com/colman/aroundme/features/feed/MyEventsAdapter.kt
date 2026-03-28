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
import com.colman.aroundme.databinding.ViewMyEventsSectionHeaderBinding
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso

class MyEventsAdapter(
    private val onEditClick: (String) -> Unit,
    private val onRecreateClick: (String) -> Unit
) : ListAdapter<MyEventRow, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is MyEventRow.SectionHeader -> VIEW_TYPE_HEADER
            is MyEventRow.EventRow -> VIEW_TYPE_EVENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ViewMyEventsSectionHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SectionHeaderViewHolder(binding)
            }
            else -> {
                val binding = ViewEventCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                MyEventViewHolder(binding, onEditClick, onRecreateClick)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is MyEventRow.SectionHeader -> (holder as SectionHeaderViewHolder).bind(item)
            is MyEventRow.EventRow -> (holder as MyEventViewHolder).bind(item)
        }
    }

    class SectionHeaderViewHolder(
        private val binding: ViewMyEventsSectionHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: MyEventRow.SectionHeader) {
            binding.sectionTitleText.text = header.title
        }
    }

    class MyEventViewHolder(
        private val binding: ViewEventCardBinding,
        private val onEditClick: (String) -> Unit,
        private val onRecreateClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: MyEventRow.EventRow) {
            val item = row.item
            val event = item.event
            binding.hostNameText.text = item.hostName
            binding.hostSubtitleText.text = item.hostSubtitle
            bindHostAvatar(item.hostAvatarUrl)
            binding.locationText.text = item.locationText
            binding.distanceBadgeText.text = item.averageRatingText
            binding.statusBadgeText.text = item.statusText
            binding.eventTitleText.text = event.title
            binding.eventDescriptionText.text = event.description
            binding.eventDescriptionText.isVisible = event.description.isNotBlank()
            binding.activeVotesText.text = item.activeVotesText
            binding.inactiveVotesText.text = item.inactiveVotesText
            binding.averageRatingText.text = item.averageRatingText
            binding.postedTimeText.text = item.postedText
            binding.postedTimeText.isVisible = false

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

            bindTags(item.tagLabels)
            bindImage(event.imageUrl)
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

    private object DiffCallback : DiffUtil.ItemCallback<MyEventRow>() {
        override fun areItemsTheSame(oldItem: MyEventRow, newItem: MyEventRow): Boolean {
            return when {
                oldItem is MyEventRow.SectionHeader && newItem is MyEventRow.SectionHeader -> oldItem.title == newItem.title
                oldItem is MyEventRow.EventRow && newItem is MyEventRow.EventRow -> oldItem.item.event.id == newItem.item.event.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: MyEventRow, newItem: MyEventRow): Boolean = oldItem == newItem
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_EVENT = 1
    }
}
