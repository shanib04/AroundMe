package com.colman.aroundme.features.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
import com.colman.aroundme.R
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.MapCoordinate
import com.colman.aroundme.data.repository.EventRepository
import com.colman.aroundme.databinding.FragmentMapBinding
import com.colman.aroundme.features.feed.EventTextFormatter
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MapFragment : Fragment() {

    private companion object {
        const val DEFAULT_MAP_ZOOM = 15f
        const val USER_LOCATION_ZOOM = 17f
    }

    private var _binding: FragmentMapBinding? = null
    private val binding get() = requireNotNull(_binding) { "FragmentMapBinding accessed outside of onCreateView/onDestroyView" }

    private val userRepository by lazy { com.colman.aroundme.data.repository.UserRepository.getInstance(requireContext()) }

    private val viewModel: MapViewModel by viewModels {
        MapViewModel.Factory(EventRepository.getInstance(requireContext()), 15f)
    }

    private lateinit var categoryAdapter: CategoryFilterAdapter
    private var googleMap: GoogleMap? = null
    private val activeMarkers = mutableMapOf<String, Marker>()

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var geocoderJob: Job? = null
    private var radiusBootstrapJob: Job? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                enableMyLocation()
            }
            else -> {
                moveToLocation(MapViewModel.KEFAR_SAVA_CENTER, DEFAULT_MAP_ZOOM, animate = false)
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

        binding.root.post {
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
                bootstrapSavedRadius()

                locationPermissionRequest.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }

    private fun bootstrapSavedRadius() {
        radiusBootstrapJob?.cancel()
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (currentUserId.isBlank()) {
            applySavedRadius(15f)
            return
        }

        radiusBootstrapJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            userRepository.refreshUserFromRemote(currentUserId)
            val savedRadius = runCatching {
                userRepository.getUserById(currentUserId).first()?.discoveryRadiusKm?.toFloat()
                }.getOrNull() ?: DEFAULT_MAP_ZOOM

            withContext(Dispatchers.Main) {
                applySavedRadius(savedRadius)
            }
        }
    }

    private fun applySavedRadius(savedRadius: Float) {
        if (_binding == null) return
        viewModel.updateRadius(savedRadius)
        binding.radiusSlider.value = savedRadius
        binding.radiusValueText.text = if (savedRadius % 1.0f == 0.0f) {
            "${savedRadius.toInt()} km"
        } else {
            "${"%.1f".format(savedRadius)} km"
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        val map = googleMap ?: return
        map.isMyLocationEnabled = true

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val userLocation = MapCoordinate(location.latitude, location.longitude)
                moveToLocation(userLocation, USER_LOCATION_ZOOM, animate = false)
                viewModel.updateSearchArea(userLocation, "Current Location")
            } else {
                moveToLocation(MapViewModel.KEFAR_SAVA_CENTER, DEFAULT_MAP_ZOOM, animate = false)
                viewModel.updateSearchArea(MapViewModel.KEFAR_SAVA_CENTER, "Kefar Sava")
            }
        }
    }

    private fun moveToLocation(coordinate: MapCoordinate, zoom: Float, animate: Boolean = true) {
        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(
            LatLng(coordinate.latitude, coordinate.longitude),
            zoom
        )
        if (animate) {
            googleMap?.animateCamera(cameraUpdate)
        } else {
            googleMap?.moveCamera(cameraUpdate)
        }
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
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
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

        // Navigation button should launch external intent to open maps app (Waze/Google Maps)
        binding.featuredCard.navigateButton.setOnClickListener {
            viewModel.selectedEvent.value?.let { event ->
                val gmmIntentUri = Uri.parse("google.navigation:q=${event.latitude},${event.longitude}")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                try {
                    startActivity(mapIntent)
                } catch (_: ActivityNotFoundException) {
                    val genericUri = Uri.parse("geo:${event.latitude},${event.longitude}?q=${event.latitude},${event.longitude}(${Uri.encode(event.title)})")
                    val genericIntent = Intent(Intent.ACTION_VIEW, genericUri)
                    try {
                        startActivity(genericIntent)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(requireContext(), "No navigation app found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Click on root opens the details page
        binding.featuredCard.root.setOnClickListener {
            viewModel.selectedEvent.value?.id?.let { eventId ->
                navigateToEventDetails(eventId)
            }
        }

        // Combined suggestions: events + addresses
        binding.locationEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s?.toString()?.trim().orEmpty()
                if (query.length < 3) {
                    binding.locationEditText.dismissDropDown()
                    return
                }

                val allEvents = viewModel.allEventsSnapshot()
                val matchingEvents = allEvents.filter { event ->
                    event.title.contains(query, ignoreCase = true) ||
                    event.locationName.contains(query, ignoreCase = true)
                }

                // Build event suggestions first
                val suggestionItems = mutableListOf<SuggestionItem>()
                matchingEvents.forEach { event ->
                    suggestionItems += SuggestionItem.EventSuggestion(
                        displayText = "${event.title} • ${event.locationName}",
                        event = event
                    )
                }

                // Then start address suggestions via Geocoder
                geocoderJob?.cancel()
                geocoderJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val geocoderSuggestions = try {
                        val geocoder = Geocoder(requireContext())
                        @Suppress("DEPRECATION")
                        val results = geocoder.getFromLocationName(query, 5)
                        results?.mapNotNull { address ->
                            val line = address.getAddressLine(0) ?: return@mapNotNull null
                            SuggestionItem.AddressSuggestion(
                                displayText = line,
                                latitude = address.latitude,
                                longitude = address.longitude,
                                label = address.locality ?: address.featureName ?: line
                            )
                        } ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }

                    val combined = (suggestionItems + geocoderSuggestions).take(10)
                    if (combined.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            val adapter = object : ArrayAdapter<String>(
                                requireContext(),
                                R.layout.item_dropdown_option,
                                combined.map { item ->
                                    when (item) {
                                        is SuggestionItem.EventSuggestion -> "${item.displayText}  (event)"
                                        is SuggestionItem.AddressSuggestion -> "${item.displayText}  (address)"
                                    }
                                }
                            ) {}

                            binding.locationEditText.setTag(R.id.locationEditText, combined)
                            binding.locationEditText.setAdapter(adapter)
                            binding.locationEditText.showDropDown()
                        }
                    }
                }
            }
        })

        binding.locationEditText.setOnItemClickListener { _, _, position, _ ->
            @Suppress("UNCHECKED_CAST")
            val items = binding.locationEditText.getTag(R.id.locationEditText) as? List<SuggestionItem>
                ?: return@setOnItemClickListener
            val item = items.getOrNull(position) ?: return@setOnItemClickListener

            when (item) {
                is SuggestionItem.EventSuggestion -> {
                    val event = item.event
                    val center = MapCoordinate(event.latitude, event.longitude)
                    viewModel.updateSearchArea(center, event.locationName)
                    moveToLocation(center, DEFAULT_MAP_ZOOM)
                    viewModel.selectEvent(event.id)
                    binding.searchAreaButton.visibility = View.GONE
                }
                is SuggestionItem.AddressSuggestion -> {
                    val center = MapCoordinate(item.latitude, item.longitude)
                    viewModel.updateSearchArea(center, item.label)
                    moveToLocation(center, DEFAULT_MAP_ZOOM)
                    binding.searchAreaButton.visibility = View.GONE
                }
            }

            hideKeyboard()
        }

        binding.locationEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(binding.locationEditText.text.toString())
                hideKeyboard()
                true
            } else {
                false
            }
        }
    }

    private sealed class SuggestionItem {
        data class EventSuggestion(val displayText: String, val event: Event) : SuggestionItem()
        data class AddressSuggestion(
            val displayText: String,
            val latitude: Double,
            val longitude: Double,
            val label: String
        ) : SuggestionItem()
    }

    private fun hideKeyboard() {
        val view = activity?.currentFocus ?: return
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        binding.locationEditText.clearFocus()
    }

    private fun performSearch(rawQuery: String? = null) {
        val query = rawQuery?.trim().orEmpty().ifEmpty {
            binding.locationEditText.text.toString().trim()
        }
        if (query.isNotBlank()) {
            val matchingEvent = viewModel.filteredEvents.value?.firstOrNull {
                it.title.contains(query, ignoreCase = true) ||
                it.locationName.contains(query, ignoreCase = true)
            }

            if (matchingEvent != null) {
                val center = MapCoordinate(matchingEvent.latitude, matchingEvent.longitude)
                viewModel.updateSearchArea(center, matchingEvent.locationName)
                moveToLocation(center, DEFAULT_MAP_ZOOM)
                viewModel.selectEvent(matchingEvent.id)
                binding.searchAreaButton.visibility = View.GONE
                return
            }

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(requireContext())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName(query, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val center = MapCoordinate(address.latitude, address.longitude)
                        withContext(Dispatchers.Main) {
                            viewModel.updateSearchArea(
                                center,
                                address.locality ?: address.featureName ?: query
                            )
                            moveToLocation(center, DEFAULT_MAP_ZOOM)
                            binding.searchAreaButton.visibility = View.GONE
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                requireContext(),
                                "Location not found",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            "Search failed. Check network.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }


    private fun observeViewModel() {
        if (googleMap == null) return

        viewModel.filteredEvents.observe(viewLifecycleOwner) { events ->
            updateMapMarkers(events)

            val suggestions = (events.map { it.title } + events.map { it.locationName })
                .filter { it.length > 3 && !it.matches(Regex("^(.)\\1+$")) } // filter out short garbage like "aaa"
                .plus(listOf("Tel Aviv", "Jerusalem", "Kefar Sava", "Haifa"))

            val uniqueSuggestions = suggestions.distinct().toTypedArray()
            val adapter = ArrayAdapter(
                requireContext(),
                R.layout.item_dropdown_option,
                uniqueSuggestions
            )
            binding.locationEditText.setAdapter(adapter)
        }

        viewModel.availableFilters.observe(viewLifecycleOwner) { filters ->
            categoryAdapter.updateFilters(filters)
        }

        viewModel.selectedFilters.observe(viewLifecycleOwner) { selected ->
            categoryAdapter.updateSelection(selected)
        }

        viewModel.searchLocationLabel.observe(viewLifecycleOwner) { label ->
            binding.currentAreaText.text = getString(R.string.map_showing_events_around_format, label)
        }

        viewModel.selectedEventItem.observe(viewLifecycleOwner) { selectedItem ->
            val density = resources.displayMetrics.density
            val event = selectedItem?.event
            if (selectedItem == null || event == null) {
                binding.featuredCard.root.visibility = View.GONE
                googleMap?.setPadding(0, 0, 0, 0)
            } else {
                binding.featuredCard.root.visibility = View.VISIBLE
                binding.featuredCard.eventTitleText.text = selectedItem.title
                binding.featuredCard.eventLocationText.text = selectedItem.locationSummary
                binding.featuredCard.ratingCountText.text = String.format(
                    Locale.US,
                    "%.1f",
                    event.averageRating
                )
                binding.featuredCard.eventTimeValue.text = selectedItem.timeText

                Glide.with(this)
                    .load(event.imageUrl)
                    .centerCrop()
                    .into(binding.featuredCard.eventImageView)

                googleMap?.setPadding(0, 0, 0, (150 * density).toInt())
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

        val knownCategories: Map<String, Int> = mapOf(
            "food" to R.drawable.ic_marker_food,
            "music" to R.drawable.ic_marker_music,
            "art" to R.drawable.ic_marker_art,
            "beer" to R.drawable.ic_marker_beer,
            "sport" to R.drawable.ic_marker_sport,
            "gaming" to R.drawable.ic_marker_gaming
        )

        var primaryCategory = "unknown"
        for (tag in eventCategories) {
            val normalizedTag = tag.trim().lowercase()
            if (knownCategories.containsKey(normalizedTag)) {
                primaryCategory = normalizedTag
                break
            }
        }

        val drawableRes = knownCategories[primaryCategory] ?: R.drawable.ic_marker_unknown

        val drawable = ContextCompat.getDrawable(requireContext(), drawableRes)
            ?: return BitmapDescriptorFactory.defaultMarker()

        val density = resources.displayMetrics.density
        val targetWidth = (36 * density).toInt()
        val targetHeight = (44 * density).toInt()

        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onDestroyView() {
        radiusBootstrapJob?.cancel()
        super.onDestroyView()
        _binding = null
        googleMap = null
        activeMarkers.clear()
        geocoderJob?.cancel()
    }
}
