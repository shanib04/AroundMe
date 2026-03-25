package com.colman.aroundme.features.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.colman.aroundme.R
import com.colman.aroundme.data.repository.EventRepository
import com.colman.aroundme.databinding.FragmentFeedBinding
import com.colman.aroundme.databinding.ViewEventCardBinding
import com.colman.aroundme.features.event.EventCardBinder

class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding: FragmentFeedBinding
        get() = requireNotNull(_binding)

    private val eventCardBinding: ViewEventCardBinding
        get() = ViewEventCardBinding.bind(binding.eventCard.root)

    private val viewModel: FeedViewModel by viewModels {
        FeedViewModel.Factory(EventRepository.getInstance(requireContext()))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.featuredEventCard.observe(viewLifecycleOwner) { card ->
            if (card == null) {
                binding.feedSubtitleText.text = getString(R.string.feed_empty_state_subtitle)
                EventCardBinder.showEmpty(eventCardBinding)
            } else {
                binding.feedSubtitleText.text = getString(R.string.feed_subtitle)
                EventCardBinder.bind(eventCardBinding, card) { eventId ->
                    openEventDetails(eventId)
                }
            }
        }
    }

    private fun openEventDetails(eventId: String) {
        val bundle = Bundle().apply {
            putString("eventId", eventId)
        }
        findNavController().navigate(R.id.action_feedFragment_to_eventDetailsFragment, bundle)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
