package com.colman.aroundme.features.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.colman.aroundme.R
import com.colman.aroundme.data.repository.EventRepository
import com.colman.aroundme.data.repository.UserRepository
import com.colman.aroundme.databinding.FragmentFeedBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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
            onEventClick = ::openEventDetails,
            onEditClick = ::openEditEvent,
            onRecreateClick = ::openRecreateEvent,
            onDeleteClick = ::confirmDeleteEvent
        )
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
        binding.feedTitleText.text = getString(R.string.my_events_title)
        binding.feedTitleText.setPadding(
            binding.feedTitleText.paddingLeft,
            binding.feedTitleText.paddingTop,
            binding.feedTitleText.paddingRight,
            14.dpToPx()
        )
        binding.sortInputLayout.isVisible = false
        binding.loadingMoreIndicator.isVisible = false
        binding.feedRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.feedRecyclerView.adapter = adapter

        viewModel.events.observe(viewLifecycleOwner) { rows ->
            adapter.submitList(rows)
            val hasEvents = rows.any { it is MyEventRow.EventRow }
            binding.emptyText.isVisible = !hasEvents
            binding.feedRecyclerView.isVisible = hasEvents
            if (!hasEvents) {
                binding.emptyText.text = getString(R.string.my_events_empty)
            }
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            val hasEvents = adapter.currentList.any { it is MyEventRow.EventRow }
            binding.initialLoadingContainer.isVisible = loading
            binding.feedRecyclerView.isVisible = !loading && hasEvents
            binding.emptyText.isVisible = !loading && !hasEvents
        }

        viewModel.deleteSuccessMessage.observe(viewLifecycleOwner) { message ->
            if (message.isNullOrBlank()) return@observe
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            viewModel.onDeleteMessageShown()
        }

        viewModel.deleteErrorMessage.observe(viewLifecycleOwner) { message ->
            if (message.isNullOrBlank()) return@observe
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            viewModel.onDeleteMessageShown()
        }
    }

    private fun openEditEvent(eventId: String) {
        val action = MyEventsFragmentDirections.actionMyEventsFragmentToCreateEventFragment(
            eventId = eventId,
            mode = "edit"
        )
        findNavController().navigate(action)
    }

    private fun openRecreateEvent(eventId: String) {
        val action = MyEventsFragmentDirections.actionMyEventsFragmentToCreateEventFragment(
            eventId = eventId,
            mode = "recreate"
        )
        findNavController().navigate(action)
    }

    private fun openEventDetails(eventId: String) {
        val action = MyEventsFragmentDirections.actionMyEventsFragmentToEventDetailsFragment(eventId)
        findNavController().navigate(action)
    }

    private fun confirmDeleteEvent(eventId: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.my_events_delete_title)
            .setMessage(R.string.my_events_delete_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.my_events_delete_action) { _, _ ->
                viewModel.deleteEvent(eventId)
            }
            .show()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        binding.feedRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
