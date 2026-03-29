package com.colman.aroundme.features.create

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import com.colman.aroundme.R
import com.colman.aroundme.databinding.FragmentLocationPickerBinding
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.util.Locale
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocationPickerFragment : Fragment(), OnMapReadyCallback {

    private companion object {
        const val DEFAULT_MAP_ZOOM = 15f
        const val USER_LOCATION_ZOOM = 17f
        val DEFAULT_LOCATION = LatLng(32.1782, 34.9076)
    }

    private var _binding: FragmentLocationPickerBinding? = null
    private val binding get() = requireNotNull(_binding) { "FragmentLocationPickerBinding accessed outside of onCreateView/onDestroyView" }

    private var googleMap: GoogleMap? = null
    private var selectedLatLng: LatLng? = null
    private var geocoderJob: Job? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> enableMyLocation()
            else -> moveToLocation(DEFAULT_LOCATION, DEFAULT_MAP_ZOOM, animate = false)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLocationPickerBinding.inflate(inflater, container, false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.fabClose.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnConfirm.setOnClickListener {
            selectedLatLng?.let { latLng ->
                val address = binding.tvSelectedAddress.text.toString()
                val geohash = GeoFireUtils.getGeoHashForLocation(GeoLocation(latLng.latitude, latLng.longitude))
                
                setFragmentResult("location_request", bundleOf(
                    "latitude" to latLng.latitude,
                    "longitude" to latLng.longitude,
                    "address" to address,
                    "geohash" to geohash
                ))
                findNavController().navigateUp()
            } ?: run {
                Toast.makeText(context, "Please select a location on the map", Toast.LENGTH_SHORT).show()
            }
        }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchLocation(binding.etSearch.text.toString())
                true
            } else {
                false
            }
        }

        // As-you-type suggestions for location search
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s?.toString()?.trim().orEmpty()
                if (query.length < 3) return

                geocoderJob?.cancel()
                geocoderJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val geocoder = Geocoder(requireContext(), Locale.getDefault())
                        @Suppress("DEPRECATION")
                        val results = geocoder.getFromLocationName(query, 5)
                        val suggestions = results?.mapNotNull { it.getAddressLine(0) } ?: emptyList()

                        if (suggestions.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                val adapter = ArrayAdapter(
                                    requireContext(),
                                    R.layout.item_dropdown_option,
                                    suggestions
                                )
                                binding.etSearch.setAdapter(adapter)
                                binding.etSearch.showDropDown()
                            }
                        }
                    } catch (_: Exception) {
                        // ignore
                    }
                }
            }
        })

        binding.etSearch.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.getItemAtPosition(position) as? String ?: return@setOnItemClickListener
            searchLocation(selected)
        }
    }

    @Suppress("DEPRECATION")
    private fun searchLocation(query: String) {
        if (query.isBlank()) return
        
        try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            val addresses = geocoder.getFromLocationName(query, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val latLng = LatLng(address.latitude, address.longitude)
                moveToLocation(latLng, DEFAULT_MAP_ZOOM)
            } else {
                Toast.makeText(context, "Location not found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error searching location", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isCompassEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false

        requestInitialLocation()

        map.setOnCameraIdleListener {
            val center = map.cameraPosition.target
            selectedLatLng = center
            updateAddress(center)
        }
    }

    private fun requestInitialLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            enableMyLocation()
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
    private fun enableMyLocation() {
        googleMap?.isMyLocationEnabled = true
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            val target = if (location != null) {
                LatLng(location.latitude, location.longitude)
            } else {
                DEFAULT_LOCATION
            }
            val zoom = if (location != null) USER_LOCATION_ZOOM else DEFAULT_MAP_ZOOM
            moveToLocation(target, zoom, animate = false)
        }.addOnFailureListener {
            moveToLocation(DEFAULT_LOCATION, DEFAULT_MAP_ZOOM, animate = false)
        }
    }

    private fun moveToLocation(latLng: LatLng, zoom: Float, animate: Boolean = true) {
        val update = CameraUpdateFactory.newLatLngZoom(latLng, zoom)
        if (animate) {
            googleMap?.animateCamera(update)
        } else {
            googleMap?.moveCamera(update)
        }
    }

    @Suppress("DEPRECATION")
    private fun updateAddress(latLng: LatLng) {
        binding.tvLatLong.text = String.format(Locale.getDefault(), "%.4f, %.4f", latLng.latitude, latLng.longitude)

        try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0].getAddressLine(0)
                binding.tvSelectedAddress.text = address
            } else {
                binding.tvSelectedAddress.text = "Unknown Location"
            }
        } catch (e: Exception) {
            binding.tvSelectedAddress.text = "Fetching address..."
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        geocoderJob?.cancel()
        _binding = null
    }
}
