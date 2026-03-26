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

        fun bind(myEvent: MyEventItem) {
            val item = myEvent.item
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

    private object DiffCallback : DiffUtil.ItemCallback<MyEventItem>() {
        override fun areItemsTheSame(oldItem: MyEventItem, newItem: MyEventItem): Boolean = oldItem.item.event.id == newItem.item.event.id
        override fun areContentsTheSame(oldItem: MyEventItem, newItem: MyEventItem): Boolean = oldItem == newItem
    }
}
