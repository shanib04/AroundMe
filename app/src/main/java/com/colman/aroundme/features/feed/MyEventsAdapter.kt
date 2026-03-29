package com.colman.aroundme.features.feed

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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
            item.bindSharedContent(binding)
            binding.eventTitleText.text = event.title
            binding.eventDescriptionText.text = event.description
            binding.eventDescriptionText.isVisible = event.description.isNotBlank()
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

            applyEndedStyle(isEnded)
        }

        private fun applyEndedStyle(isEnded: Boolean) {
            val context = binding.root.context
            val primaryColor = ContextCompat.getColor(
                context,
                if (isEnded) R.color.my_events_ended_text_primary else R.color.text_dark
            )
            val secondaryColor = ContextCompat.getColor(
                context,
                if (isEnded) R.color.my_events_ended_text_secondary else R.color.text_light
            )
            val subtitleColor = ContextCompat.getColor(
                context,
                if (isEnded) R.color.my_events_ended_text_secondary else R.color.primary_coral
            )
            val strokeColor = ContextCompat.getColor(
                context,
                if (isEnded) R.color.my_events_ended_card_stroke else android.R.color.transparent
            )

            binding.root.alpha = if (isEnded) 0.92f else 1f
            binding.root.strokeColor = strokeColor
            binding.root.strokeWidth = if (isEnded) 1 else 0
            binding.eventImageView.alpha = if (isEnded) 0.58f else 1f
            binding.eventImageView.colorFilter = if (isEnded) {
                ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            } else {
                null
            }
            binding.hostAvatarImageView.alpha = if (isEnded) 0.72f else 1f
            binding.hostNameText.setTextColor(primaryColor)
            binding.hostSubtitleText.setTextColor(subtitleColor)
            binding.locationText.setTextColor(secondaryColor)
            binding.eventTitleText.setTextColor(primaryColor)
            binding.eventDescriptionText.setTextColor(secondaryColor)
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
