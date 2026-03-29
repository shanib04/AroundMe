package com.colman.aroundme.features.feed

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.colman.aroundme.R
import com.colman.aroundme.databinding.ViewEventCardBinding

class EventAdapter(
    private val onEventClick: (String) -> Unit
) : ListAdapter<FeedEventItem, EventAdapter.EventViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ViewEventCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventViewHolder(binding, onEventClick)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class EventViewHolder(
        private val binding: ViewEventCardBinding,
        private val onEventClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(feedItem: FeedEventItem) {
            val item = feedItem.item
            val event = item.event
            item.bindSharedContent(binding)
            binding.eventTitleText.text = event.title.ifBlank { binding.root.context.getString(R.string.feed_empty_state_title) }
            binding.eventDescriptionText.text = event.description.ifBlank {
                binding.root.context.getString(R.string.feed_empty_state_description)
            }
            binding.eventDescriptionText.isVisible = event.description.isNotBlank()

            // Feed should NOT allow voting; it only shows vote counts.
            binding.activeVotesButton.isClickable = false
            binding.activeVotesButton.isFocusable = false
            binding.inactiveVotesButton.isClickable = false
            binding.inactiveVotesButton.isFocusable = false

            binding.root.setOnClickListener { onEventClick(event.id) }
            binding.moreButton.setOnClickListener { onEventClick(event.id) }
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
