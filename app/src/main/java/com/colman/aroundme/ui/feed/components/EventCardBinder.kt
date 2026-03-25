package com.colman.aroundme.ui.feed.components

import com.bumptech.glide.Glide
import com.colman.aroundme.R
import com.colman.aroundme.databinding.ViewEventCardBinding

object EventCardBinder {
    fun bind(
        binding: ViewEventCardBinding,
        model: EventCardUiModel,
        onClick: ((String) -> Unit)? = null
    ) {
        binding.hostNameText.text = model.hostName
        binding.hostSubtitleText.text = model.hostSubtitle
        binding.distanceBadgeText.text = model.distanceText
        binding.statusBadgeText.text = model.statusText
        binding.eventTitleText.text = model.title
        binding.eventDescriptionText.text = model.description
        binding.primaryTagChip.text = model.primaryTag
        binding.secondaryTagChip.text = model.secondaryTag
        binding.tertiaryTagChip.text = model.tertiaryTag
        binding.likesText.text = model.likesText
        binding.commentsText.text = model.commentsText
        binding.postedTimeText.text = model.postedText

        if (model.imageUrl.isNotBlank()) {
            Glide.with(binding.root.context)
                .load(model.imageUrl)
                .placeholder(R.drawable.bg_register_image_placeholder)
                .centerCrop()
                .into(binding.eventImageView)
        } else {
            binding.eventImageView.setImageResource(R.drawable.bg_register_image_placeholder)
        }

        binding.root.setOnClickListener {
            onClick?.invoke(model.id)
        }
        binding.shareButton.setOnClickListener {
            onClick?.invoke(model.id)
        }
        binding.moreButton.setOnClickListener(null)
        binding.likeButton.setOnClickListener(null)
        binding.commentButton.setOnClickListener(null)
    }

    fun showEmpty(binding: ViewEventCardBinding) {
        binding.hostNameText.text = "@AroundMe"
        binding.hostSubtitleText.text = binding.root.context.getString(R.string.feed_empty_state_subtitle)
        binding.distanceBadgeText.text = "0.0km"
        binding.statusBadgeText.text = binding.root.context.getString(R.string.feed_empty_state_status)
        binding.eventTitleText.text = binding.root.context.getString(R.string.feed_empty_state_title)
        binding.eventDescriptionText.text = binding.root.context.getString(R.string.feed_empty_state_description)
        binding.primaryTagChip.text = "#AroundMe"
        binding.secondaryTagChip.text = "#Events"
        binding.tertiaryTagChip.text = "#Soon"
        binding.likesText.text = "0"
        binding.commentsText.text = "0"
        binding.postedTimeText.text = binding.root.context.getString(R.string.feed_empty_state_posted)
        binding.eventImageView.setImageResource(R.drawable.bg_register_image_placeholder)
        binding.root.setOnClickListener(null)
        binding.shareButton.setOnClickListener(null)
        binding.likeButton.setOnClickListener(null)
        binding.commentButton.setOnClickListener(null)
        binding.moreButton.setOnClickListener(null)
    }
}
