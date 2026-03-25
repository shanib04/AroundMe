package com.colman.aroundme.features.event

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.colman.aroundme.R
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
        EventDetailsViewModel.Factory(EventRepository.Companion.getInstance(requireContext()), eventId)
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
        observeEvent()
    }

    private fun bindActions() {
        val binding = requireNotNull(binding)
        binding.closeButton.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.stillHappeningButton.setOnClickListener {
            submitVote(getString(R.string.event_details_vote_live))
        }
        binding.endedButton.setOnClickListener {
            submitVote(getString(R.string.event_details_vote_ended))
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
            binding.locationTitleText.text = event.locationName
            binding.reportedTimeText.text = event.timeRemaining
            binding.helpText.text = getString(R.string.event_details_help_text, 240)

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

    private fun submitVote(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        findNavController().navigateUp()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}