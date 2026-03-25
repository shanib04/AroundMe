package com.colman.aroundme.features.event

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.colman.aroundme.R
import com.colman.aroundme.data.model.EventVoteType
import com.colman.aroundme.data.repository.EventRepository
import com.colman.aroundme.databinding.FragmentEventDetailsBinding
import com.google.android.material.chip.Chip

class EventDetailsFragment : Fragment() {

    private var _binding: FragmentEventDetailsBinding? = null
    private val binding: FragmentEventDetailsBinding?
        get() = _binding

    // SafeArgs generated class may not be available in this environment; read from args bundle instead.
    private val eventId: String
        get() = arguments?.getString("eventId") ?: ""

    private val viewModel: EventDetailsViewModel by viewModels {
        EventDetailsViewModel.Factory(EventRepository.getInstance(requireContext()), eventId)
    }

    private val ratingStars by lazy {
        listOf(
            requireNotNull(binding).star1Text,
            requireNotNull(binding).star2Text,
            requireNotNull(binding).star3Text,
            requireNotNull(binding).star4Text,
            requireNotNull(binding).star5Text
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEventDetailsBinding.inflate(inflater, container, false)
        return requireNotNull(binding).root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindActions()
        bindRatingActions()
        observeEvent()
        observeVoteState()
        observeSelectedVoteType()
        observeRating()
    }

    private fun bindActions() {
        val binding = requireNotNull(binding)
        binding.closeButton.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.stillHappeningButton.setOnClickListener {
            submitVote(EventVoteType.ACTIVE, getString(R.string.event_details_vote_live))
        }
        binding.endedButton.setOnClickListener {
            submitVote(EventVoteType.INACTIVE, getString(R.string.event_details_vote_ended))
        }
    }

    private fun bindRatingActions() {
        ratingStars.forEachIndexed { index, starView ->
            val ratingValue = index + 1
            starView.contentDescription = getString(R.string.event_rating_star_content_description, ratingValue)
            starView.setOnClickListener {
                viewModel.submitRating(ratingValue)
            }
        }
    }

    private fun observeEvent() {
        viewModel.event.observe(viewLifecycleOwner) { event ->
            val binding = binding ?: return@observe
            if (event == null) {
                Toast.makeText(requireContext(), R.string.event_details_not_found, Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
                return@observe
            }

            binding.eventTitleText.text = event.title
            binding.eventSubtitleText.text = event.description
            binding.eventSubtitleText.isVisible = event.description.isNotBlank()
            binding.locationTitleText.text = event.locationName
            binding.reportedTimeText.text = event.timeRemaining
            binding.helpText.text = getString(R.string.event_details_help_text, event.activeVotes + event.inactiveVotes)
            binding.activeVotesCountText.text = event.activeVotes.toString()
            binding.inactiveVotesCountText.text = event.inactiveVotes.toString()
            binding.ratingSummaryText.text = if (event.ratingCount > 0) {
                getString(R.string.event_rating_summary_format, event.averageRating, event.ratingCount)
            } else {
                getString(R.string.event_rating_empty)
            }

            binding.tagsChipGroup.removeAllViews()
            event.tags.forEach { tag ->
                val chip = Chip(requireContext()).apply {
                    text = tag
                    isClickable = false
                    isCheckable = false
                }
                binding.tagsChipGroup.addView(chip)
            }

            Glide.with(this)
                .load(event.imageUrl)
                .centerCrop()
                .into(binding.headerImageView)
        }
    }

    private fun observeVoteState() {
        viewModel.isSubmittingVote.observe(viewLifecycleOwner) { isSubmitting ->
            val binding = binding ?: return@observe
            binding.stillHappeningButton.isEnabled = !isSubmitting
            binding.endedButton.isEnabled = !isSubmitting
            binding.stillHappeningButton.alpha = if (isSubmitting) 0.6f else 1f
            binding.endedButton.alpha = if (isSubmitting) 0.6f else 1f
        }
    }

    private fun observeSelectedVoteType() {
        viewModel.selectedVoteType.observe(viewLifecycleOwner) { voteType ->
            val binding = binding ?: return@observe
            val activeSelected = voteType == EventVoteType.ACTIVE
            val inactiveSelected = voteType == EventVoteType.INACTIVE
            binding.stillHappeningButton.strokeWidth = if (activeSelected) 4 else 0
            binding.endedButton.strokeWidth = if (inactiveSelected) 4 else 0
            binding.stillHappeningButton.strokeColor = if (activeSelected) android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), android.R.color.white)) else null
            binding.endedButton.strokeColor = if (inactiveSelected) android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), android.R.color.white)) else null
        }
    }

    private fun observeRating() {
        viewModel.selectedRating.observe(viewLifecycleOwner) { rating ->
            renderSelectedRating(rating ?: 0)
        }
    }

    private fun renderSelectedRating(selectedRating: Int) {
        ratingStars.forEachIndexed { index, starView ->
            val isSelected = index < selectedRating
            starView.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isSelected) R.color.primary_coral else android.R.color.darker_gray
                )
            )
        }
    }

    private fun submitVote(voteType: EventVoteType, message: String) {
        if (eventId.isBlank()) return
        viewModel.submitVote(voteType)
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}