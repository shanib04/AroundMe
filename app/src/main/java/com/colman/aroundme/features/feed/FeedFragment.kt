package com.colman.aroundme.features.feed

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.colman.aroundme.R
import com.colman.aroundme.data.model.MapCoordinate
import com.colman.aroundme.data.repository.EventRepository
import com.colman.aroundme.data.repository.UserRepository
import com.colman.aroundme.databinding.FragmentFeedBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.concurrent.TimeUnit

class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding: FragmentFeedBinding
        get() = requireNotNull(_binding) { "FragmentFeedBinding accessed outside of view lifecycle" }

    private val viewModel: FeedViewModel by viewModels {
        FeedViewModel.Factory(
            EventRepository.getInstance(requireContext()),
            UserRepository.getInstance(requireContext())
        )
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationTokenSource: CancellationTokenSource? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
        ) {
            fetchCurrentLocation()
        }
    }

    private val eventAdapter by lazy {
        EventAdapter(::openEventDetails)
    }

    private val feedScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            if (dy <= 0) return

            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
            val totalCount = layoutManager.itemCount
            if (totalCount == 0) return

            val lastVisible = layoutManager.findLastVisibleItemPosition()
            val thresholdIndex = (totalCount * 0.8f).toInt()
            if (lastVisible >= thresholdIndex) {
                viewModel.loadMoreEvents()
            }
        }
    }

    private var lastHandledScrollToTopToken: Long = 0L
    private var selectedSortOption: FeedSortOption = FeedSortOption.NEWEST

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSortDropdown()
        observeViewModel()
        requestLocationForDistance()
    }

    private fun setupRecyclerView() {
        binding.feedRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = eventAdapter
            itemAnimator = null
            addOnScrollListener(feedScrollListener)
        }
    }

    private fun setupSortDropdown() {
        val options = FeedSortOption.entries.map { it.label }
        val adapter = ArrayAdapter(requireContext(), R.layout.item_dropdown_option, options)
        binding.sortDropdown.setAdapter(adapter)
        binding.sortDropdown.setText(selectedSortOption.label, false)
        binding.sortDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedSortOption = FeedSortOption.entries[position]
            viewModel.setSortOption(selectedSortOption)
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            // If fragment view is already destroyed, ignore emissions.
            if (_binding == null) return@observe

            render(state)
        }
    }

    private fun render(state: FeedUiState) {
        if (_binding == null) return

        if (selectedSortOption != state.sortOption) {
            selectedSortOption = state.sortOption
            binding.sortDropdown.setText(state.sortOption.label, false)
        }

        eventAdapter.submitList(state.items) {
            if (state.scrollToTopToken != 0L && state.scrollToTopToken != lastHandledScrollToTopToken) {
                lastHandledScrollToTopToken = state.scrollToTopToken
                binding.feedRecyclerView.scrollToPosition(0)
            }
        }
        binding.initialLoadingContainer.isVisible = state.isInitialLoading
        binding.loadingMoreIndicator.isVisible = state.isLoadingMore && state.items.isNotEmpty()
        binding.emptyText.isVisible = state.items.isEmpty() && !state.isInitialLoading
        binding.emptyText.text = state.emptyMessage
        binding.feedRecyclerView.isVisible = state.items.isNotEmpty() && !state.isInitialLoading
    }

    private fun requestLocationForDistance() {
        val hasFineLocation = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFineLocation || hasCoarseLocation) {
            fetchCurrentLocation()
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation() {
        locationTokenSource?.cancel()
        locationTokenSource = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            locationTokenSource?.token
        ).addOnSuccessListener { location: Location? ->
            if (!applyLocationIfUsable(location)) {
                fetchLastKnownLocation()
            }
        }.addOnFailureListener {
            fetchLastKnownLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLastKnownLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                applyLocationIfUsable(location)
            }
    }

    private fun applyLocationIfUsable(location: Location?): Boolean {
        val safeLocation = location ?: return false
        if (!safeLocation.isAccurateEnough()) return false

        viewModel.updateUserLocation(
            MapCoordinate(safeLocation.latitude, safeLocation.longitude),
            "Current Location"
        )
        return true
    }

    private fun Location.isAccurateEnough(): Boolean {
        val ageMillis = elapsedRealtimeAgeMillis()
        val maxAgeMillis = TimeUnit.MINUTES.toMillis(5)
        val maxAccuracyMeters = 250f
        return ageMillis in 0..maxAgeMillis && accuracy <= maxAccuracyMeters
    }

    private fun Location.elapsedRealtimeAgeMillis(): Long {
        return TimeUnit.NANOSECONDS.toMillis(android.os.SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos)
    }

    private fun openEventDetails(eventId: String) {
        val action = FeedFragmentDirections.actionFeedFragmentToEventDetailsFragment(eventId)
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        // Cancel jobs first to prevent callbacks trying to access a cleared binding.
        locationTokenSource?.cancel()

        _binding?.feedRecyclerView?.removeOnScrollListener(feedScrollListener)
        _binding?.feedRecyclerView?.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
