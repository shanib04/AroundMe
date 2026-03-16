package com.colman.aroundme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.colman.aroundme.data.Event
import com.colman.aroundme.data.EventRepository
import com.colman.aroundme.databinding.FragmentMapBinding
import com.colman.aroundme.ui.map.CategoryFilterAdapter
import com.colman.aroundme.ui.map.MapCoordinate
import com.colman.aroundme.ui.map.MapViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MapViewModel by viewModels {
        MapViewModel.Factory(EventRepository())
    }

    private lateinit var categoryAdapter: CategoryFilterAdapter
    private var googleMap: GoogleMap? = null
    private val activeMarkers = mutableMapOf<String, Marker>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.mapView) as? SupportMapFragment
        mapFragment?.getMapAsync { map ->
            googleMap = map
            setupMap()
            observeViewModel()
        }

        setupUI()
    }

    private fun setupMap() {
        val map = googleMap ?: return
        
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isCompassEnabled = false

        map.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                binding.searchAreaButton.visibility = View.VISIBLE
            }
        }

        map.setOnMarkerClickListener { marker ->
            val eventId = marker.tag as? String
            if (eventId != null) {
                viewModel.selectEvent(eventId)
                marker.showInfoWindow()
            }
            true
        }
    }

    private fun setupUI() {
        categoryAdapter = CategoryFilterAdapter(emptyList()) { filter ->
            viewModel.toggleFilter(filter)
        }
        binding.categoryRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }

        binding.searchAreaButton.setOnClickListener {
            val map = googleMap ?: return@setOnClickListener
            val center = map.cameraPosition.target
            viewModel.updateSearchArea(MapCoordinate(center.latitude, center.longitude), "Custom Area")
            binding.searchAreaButton.visibility = View.GONE
        }

        binding.myLocationButton.setOnClickListener {
            viewModel.updateSearchArea(MapViewModel.DEFAULT_SEARCH_CENTER, MapViewModel.DEFAULT_SEARCH_LABEL)
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(MapViewModel.DEFAULT_SEARCH_CENTER.latitude, MapViewModel.DEFAULT_SEARCH_CENTER.longitude), 
                14f
            ))
            binding.searchAreaButton.visibility = View.GONE
        }

        binding.radiusSlider.addOnChangeListener { _, value, _ ->
            binding.radiusValueText.text = "${value.toInt()} km"
            viewModel.updateRadius(value)
        }

        binding.searchLocationButton.setOnClickListener {
            val location = binding.locationEditText.text.toString()
            if (location.isNotBlank()) {
                val mockCenter = if (location.contains("kefar", ignoreCase = true)) {
                    MapViewModel.KEFAR_SAVA_CENTER
                } else {
                    MapViewModel.JERUSALEM_CENTER
                }
                viewModel.updateSearchArea(mockCenter, location)
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    LatLng(mockCenter.latitude, mockCenter.longitude), 
                    14f
                ))
                binding.searchAreaButton.visibility = View.GONE
            }
        }

        binding.featuredCard.root.setOnClickListener {
            viewModel.selectedEvent.value?.id?.let { eventId ->
                val action = MapFragmentDirections.actionMapFragmentToEventDetailsFragment(eventId)
                findNavController().navigate(action)
            }
        }
    }

    private fun observeViewModel() {
        if (googleMap == null) return

        viewModel.filteredEvents.observe(viewLifecycleOwner) { events ->
            updateMapMarkers(events)
        }

        viewModel.searchCenter.observe(viewLifecycleOwner) { center ->
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(center.latitude, center.longitude),
                14f
            ))
        }

        viewModel.availableFilters.observe(viewLifecycleOwner) { filters ->
            categoryAdapter.updateFilters(filters)
        }

        viewModel.selectedFilters.observe(viewLifecycleOwner) { selected ->
            categoryAdapter.updateSelection(selected)
        }

        viewModel.searchLocationLabel.observe(viewLifecycleOwner) { label ->
            binding.currentAreaText.text = "Showing events around $label"
        }

        viewModel.selectedEvent.observe(viewLifecycleOwner) { event ->
            if (event == null) {
                binding.featuredCard.root.visibility = View.GONE
            } else {
                binding.featuredCard.root.visibility = View.VISIBLE
                binding.featuredCard.eventTitleText.text = event.title
                
                val distance = viewModel.distanceFromCenterKm(event)
                binding.featuredCard.eventLocationText.text = "${event.locationName} • ${"%.1f".format(distance)}km away"
                
                binding.featuredCard.eventTimeValue.text = event.timeRemaining
                
                Glide.with(this)
                    .load(event.imageUrl)
                    .centerCrop()
                    .into(binding.featuredCard.eventImageView)
                    
                activeMarkers[event.id]?.showInfoWindow()
            }
        }
    }

    private fun updateMapMarkers(events: List<Event>) {
        val map = googleMap ?: return
        
        map.clear()
        activeMarkers.clear()
        
        for (event in events) {
            val position = LatLng(event.latitude, event.longitude)
            val marker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(event.title)
            )
            if (marker != null) {
                marker.tag = event.id
                activeMarkers[event.id] = marker
            }
        }
        
        viewModel.selectedEvent.value?.id?.let { selectedId ->
            activeMarkers[selectedId]?.showInfoWindow()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        googleMap = null
        activeMarkers.clear()
    }
}
