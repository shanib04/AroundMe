package com.colman.aroundme.features.feed

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.EventVoteType
import com.colman.aroundme.databinding.ViewEventCardBinding

class HomeFeedAdapter(
    private val onItemClicked: (eventId: String) -> Unit,
    private val onVoteClicked: (eventId: String, voteType: EventVoteType) -> Unit
) : ListAdapter<Event, HomeFeedAdapter.EventViewHolder>(EventDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ViewEventCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventViewHolder(binding, onItemClicked, onVoteClicked)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class EventViewHolder(
        private val binding: ViewEventCardBinding,
        private val onItemClicked: (String) -> Unit,
        private val onVoteClicked: (String, EventVoteType) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(event: Event) {
            binding.eventTitleText.text = event.title
            binding.locationText.text = event.locationName

            // Update the vote counters shown on the pills
            binding.activeVotesText.text = event.activeVotes.toString()
            binding.inactiveVotesText.text = event.inactiveVotes.toString()

            binding.root.setOnClickListener { onItemClicked(event.id) }
            binding.moreButton.setOnClickListener { onItemClicked(event.id) }

            // Vote actions
            binding.activeVotesButton.setOnClickListener { onVoteClicked(event.id, EventVoteType.ACTIVE) }
            binding.inactiveVotesButton.setOnClickListener { onVoteClicked(event.id, EventVoteType.INACTIVE) }
        }
    }

    private class EventDiffCallback : DiffUtil.ItemCallback<Event>() {
        override fun areItemsTheSame(oldItem: Event, newItem: Event): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Event, newItem: Event): Boolean = oldItem == newItem
    }
}
