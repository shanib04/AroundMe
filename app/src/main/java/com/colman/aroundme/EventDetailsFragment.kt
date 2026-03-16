package com.colman.aroundme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.colman.aroundme.data.EventRepository
import com.colman.aroundme.databinding.FragmentEventDetailsBinding
import com.colman.aroundme.ui.details.EventDetailsViewModel
import com.google.android.material.chip.Chip

class EventDetailsFragment : Fragment() {

    private var _binding: FragmentEventDetailsBinding? = null
    private val binding: FragmentEventDetailsBinding?
        get() = _binding

    private val args: EventDetailsFragmentArgs by navArgs()

    private val viewModel: EventDetailsViewModel by viewModels {
        EventDetailsViewModel.Factory(EventRepository(), args.eventId)
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

            com.bumptech.glide.Glide.with(this)
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

