package com.colman.aroundme

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.colman.aroundme.data.Event
import com.colman.aroundme.data.EventRepository
import com.colman.aroundme.databinding.FragmentMapBinding
import com.colman.aroundme.ui.map.CategoryFilterAdapter
import com.colman.aroundme.ui.map.MapCoordinate
import com.colman.aroundme.ui.map.MapViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MapViewModel by viewModels {
        MapViewModel.Factory(EventRepository())
    }

    private lateinit var categoryAdapter: CategoryFilterAdapter
    private var googleMap: GoogleMap? = null
    private val activeMarkers = mutableMapOf<String, Marker>()

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                enableMyLocation()
            }
            else -> {
                moveToLocation(MapViewModel.KEFAR_SAVA_CENTER, 15f)
                viewModel.updateSearchArea(MapViewModel.KEFAR_SAVA_CENTER, "Kefar Sava")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.featuredCard.root.visibility = View.GONE
        binding.loadingOverlay.visibility = View.VISIBLE

        setupUI()

        // Wait 150ms to allow the Fragment's enter animation to run smoothly before locking the main thread with Maps setup
        viewLifecycleOwner.lifecycleScope.launch {
            delay(150)
            if (_binding == null) return@launch
            
            val mapFragment = SupportMapFragment.newInstance()
            childFragmentManager.beginTransaction()
                .replace(R.id.mapContainer, mapFragment)
                .commitAllowingStateLoss()

            mapFragment.getMapAsync { map ->
                googleMap = map
                
                map.setOnMapLoadedCallback {
                    binding.loadingOverlay.visibility = View.GONE
                }
                
                setupMap()
                observeViewModel()

                locationPermissionRequest.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        val map = googleMap ?: return
        map.isMyLocationEnabled = true

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val userLocation = MapCoordinate(location.latitude, location.longitude)
                moveToLocation(userLocation, 15f)
                viewModel.updateSearchArea(userLocation, "Current Location")
            } else {
                moveToLocation(MapViewModel.KEFAR_SAVA_CENTER, 15f)
                viewModel.updateSearchArea(MapViewModel.KEFAR_SAVA_CENTER, "Kefar Sava")
            }
        }
    }

    private fun moveToLocation(coordinate: MapCoordinate, zoom: Float) {
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(
            LatLng(coordinate.latitude, coordinate.longitude),
            zoom
        ))
    }

    private fun setupMap() {
        val map = googleMap ?: return
        
        map.uiSettings.isZoomGesturesEnabled = true
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isCompassEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false

        map.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                binding.searchAreaButton.visibility = View.VISIBLE
            }
        }

        map.setOnMarkerClickListener { marker ->
            val eventId = marker.tag as? String
            if (eventId != null) {
                viewModel.selectEvent(eventId)
                // Camera will center on the marker inside the observeViewModel block
            } else {
                marker.showInfoWindow()
            }
            true
        }
        
        map.setOnMapClickListener {
            viewModel.selectEvent(null)
        }
    }

    private fun navigateToEventDetails(eventId: String) {
        val action = MapFragmentDirections.actionMapFragmentToEventDetailsFragment(eventId)
        findNavController().navigate(action)
    }

    private fun setupUI() {
        categoryAdapter = CategoryFilterAdapter(emptyList()) { filter ->
            viewModel.toggleFilter(filter)
        }
        binding.categoryRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }

        binding.zoomInButton.setOnClickListener {
            googleMap?.animateCamera(CameraUpdateFactory.zoomIn())
        }
        binding.zoomOutButton.setOnClickListener {
            googleMap?.animateCamera(CameraUpdateFactory.zoomOut())
        }

        binding.searchAreaButton.setOnClickListener {
            val map = googleMap ?: return@setOnClickListener
            val center = map.cameraPosition.target
            viewModel.updateSearchArea(MapCoordinate(center.latitude, center.longitude), "Custom Area")
            binding.searchAreaButton.visibility = View.GONE
        }

        binding.myLocationButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation()
            } else {
                locationPermissionRequest.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
            binding.searchAreaButton.visibility = View.GONE
        }

        binding.radiusSlider.addOnChangeListener { _, value, _ ->
            binding.radiusValueText.text = if (value % 1.0f == 0.0f) "${value.toInt()} km" else "${"%.1f".format(value)} km"
            viewModel.updateRadius(value)
        }
        
        binding.featuredCard.navigateButton.setOnClickListener {
            viewModel.selectedEvent.value?.id?.let { eventId ->
                navigateToEventDetails(eventId)
            }
        }
        
        binding.featuredCard.root.setOnClickListener {
            viewModel.selectedEvent.value?.id?.let { eventId ->
                navigateToEventDetails(eventId)
            }
        }

        binding.searchLocationButton.setOnClickListener {
            performSearch()
        }
        
        binding.locationEditText.setOnItemClickListener { _, _, _, _ ->
            performSearch()
        }
    }
    
    private fun performSearch() {
        val query = binding.locationEditText.text.toString().trim()
        if (query.isNotBlank()) {
            val matchingEvent = viewModel.filteredEvents.value?.firstOrNull {
                it.title.contains(query, ignoreCase = true) || 
                it.locationName.contains(query, ignoreCase = true)
            }

            if (matchingEvent != null) {
                val center = MapCoordinate(matchingEvent.latitude, matchingEvent.longitude)
                viewModel.updateSearchArea(center, matchingEvent.locationName)
                moveToLocation(center, 15f)
                viewModel.selectEvent(matchingEvent.id)
                binding.searchAreaButton.visibility = View.GONE
                return
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(requireContext())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName(query, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val center = MapCoordinate(address.latitude, address.longitude)
                        withContext(Dispatchers.Main) {
                            viewModel.updateSearchArea(center, address.locality ?: address.featureName ?: query)
                            moveToLocation(center, 15f)
                            binding.searchAreaButton.visibility = View.GONE
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Location not found", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Search failed. Check network.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun observeViewModel() {
        if (googleMap == null) return

        viewModel.filteredEvents.observe(viewLifecycleOwner) { events ->
            updateMapMarkers(events)
            
            val suggestions = events.map { it.title } + 
                              events.map { it.locationName } + 
                              listOf("Tel Aviv", "Jerusalem", "Kefar Sava", "Haifa")
                              
            val uniqueSuggestions = suggestions.distinct().toTypedArray()
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, uniqueSuggestions)
            binding.locationEditText.setAdapter(adapter)
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
            val density = resources.displayMetrics.density
            if (event == null) {
                binding.featuredCard.root.visibility = View.GONE
                googleMap?.setPadding(0, 0, 0, 0) 
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
                
                // Add padding so the map centers precisely in the visible space ABOVE the card + buttons
                googleMap?.setPadding(0, 0, 0, (260 * density).toInt()) 

                // Perfectly center the tapped marker considering the newly created space!
                val position = LatLng(event.latitude, event.longitude)
                googleMap?.animateCamera(CameraUpdateFactory.newLatLng(position), 300, null)
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
                    .icon(getMarkerIcon(event))
            )
            if (marker != null) {
                marker.tag = event.id
                activeMarkers[event.id] = marker
            }
        }
    }

    private fun getMarkerIcon(event: Event): BitmapDescriptor {
        val eventCategories = listOf(event.category) + event.tags
        
        val knownIcons = mapOf(
            "food" to R.drawable.ic_icon_food,
            "music" to R.drawable.ic_icon_music,
            "art" to R.drawable.ic_icon_art,
            "beer" to R.drawable.ic_icon_beer,
            "sport" to R.drawable.ic_icon_sport
        )

        val knownColors = mapOf(
            "food" to 0xFFEE7C2B.toInt(),
            "music" to 0xFFFF6B6B.toInt(),
            "art" to 0xFF8E7CFF.toInt(),
            "beer" to 0xFFFFC857.toInt(),
            "sport" to 0xFF20C997.toInt()
        )

        // Find the FIRST category tag that actually has a known custom marker
        var primaryCategory = "unknown"
        for (tag in eventCategories) {
            val normalizedTag = tag.trim().lowercase()
            if (knownIcons.containsKey(normalizedTag)) {
                primaryCategory = normalizedTag
                break
            }
        }

        val drawableRes = knownIcons[primaryCategory] ?: R.drawable.ic_icon_unknown
        val categoryColor = knownColors[primaryCategory] ?: 0xFF9E9E9E.toInt() // Gray for unknown

        val density = resources.displayMetrics.density
        // Define dimensions for a map pin shape
        val width = (42 * density).toInt()
        val height = (54 * density).toInt() 
        val iconSize = (20 * density).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val centerX = width / 2f
        val centerY = width / 2f
        val radius = width / 2f - 2 * density

        // Draw drop shadow at the bottom
        paint.color = 0x33000000
        val shadowRect = RectF(centerX - radius/2, height - 6 * density, centerX + radius/2, height.toFloat())
        canvas.drawOval(shadowRect, paint)

        // Draw beautiful map pin wrapper (circle + triangle pointing down)
        val path = android.graphics.Path()
        path.addCircle(centerX, centerY, radius, android.graphics.Path.Direction.CW)
        
        path.moveTo(centerX - radius * 0.6f, centerY + radius * 0.5f)
        path.lineTo(centerX, height.toFloat() - 4 * density)
        path.lineTo(centerX + radius * 0.6f, centerY + radius * 0.5f)
        path.close()

        paint.color = 0xFFFFFFFF.toInt() // White border
        canvas.drawPath(path, paint)

        // Inner category color fill
        paint.color = categoryColor
        canvas.drawCircle(centerX, centerY, radius - 2 * density, paint)

        // Draw icon in the center
        val drawable = ContextCompat.getDrawable(requireContext(), drawableRes)
        if (drawable != null) {
            val offset = ((width - iconSize) / 2f).toInt()
            drawable.setBounds(offset, offset, offset + iconSize, offset + iconSize)
            // No need to tint, icons are already white
            drawable.draw(canvas)
        }

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        googleMap = null
        activeMarkers.clear()
    }
}
