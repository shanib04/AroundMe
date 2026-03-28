package com.colman.aroundme.features.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.colman.aroundme.R
import com.colman.aroundme.data.repository.EventRepository
import com.colman.aroundme.data.repository.UserRepository
import com.colman.aroundme.databinding.FragmentFeedBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MyEventsFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: MyEventsViewModel by viewModels {
        MyEventsViewModel.Factory(
            EventRepository.getInstance(requireContext()),
            UserRepository.getInstance(requireContext())
        )
    }

    private val adapter by lazy {
        MyEventsAdapter(
            onEditClick = ::openEditEvent,
            onRecreateClick = ::openRecreateEvent
        )
    }

    private val userRepository by lazy { UserRepository.getInstance(requireContext()) }
    private var userSyncJob: Job? = null
    private var ensureUsersJob: Job? = null

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
        userSyncJob?.cancel()
        userSyncJob = viewLifecycleOwner.lifecycleScope.launch {
            userRepository.syncFromRemoteNow()
        }
        binding.feedTitleText.text = getString(R.string.my_events_title)
        binding.feedTitleText.setPadding(
            binding.feedTitleText.paddingLeft,
            binding.feedTitleText.paddingTop,
            binding.feedTitleText.paddingRight,
            resources.getDimensionPixelSize(R.dimen.my_events_title_bottom_padding)
        )
        binding.sortInputLayout.isVisible = false
        binding.loadingMoreIndicator.isVisible = false
        binding.feedRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.feedRecyclerView.adapter = adapter

        viewModel.events.observe(viewLifecycleOwner) { rows ->
            ensureUsersJob?.cancel()
            ensureUsersJob = lifecycleScope.launch {
                userRepository.ensureUsersLoaded(
                    rows.mapNotNull { row ->
                        (row as? MyEventRow.EventRow)?.item?.event?.publisherId
                    }
                )
            }
            rows.forEach { row ->
                val eventRow = row as? MyEventRow.EventRow ?: return@forEach
                if (eventRow.item.hostName == EventTextFormatter.unknownPublisherText() && eventRow.item.event.publisherId.isNotBlank()) {
                    userRepository.refreshUserFromRemote(eventRow.item.event.publisherId)
                }
            }
            adapter.submitList(rows)
            binding.emptyText.isVisible = rows.none { it is MyEventRow.EventRow }
            binding.emptyText.text = getString(R.string.my_events_empty)
        }
    }

    private fun openEditEvent(eventId: String) {
        val bundle = Bundle().apply {
            putString("eventId", eventId)
            putString("mode", "edit")
        }
        findNavController().navigate(R.id.createEventFragment, bundle)
    }

    private fun openRecreateEvent(eventId: String) {
        val bundle = Bundle().apply {
            putString("eventId", eventId)
            putString("mode", "recreate")
        }
        findNavController().navigate(R.id.createEventFragment, bundle)
    }

    override fun onDestroyView() {
        ensureUsersJob?.cancel()
        userSyncJob?.cancel()
        binding.feedRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
