package com.colman.aroundme.ui.create

import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
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
import java.util.Locale

class LocationPickerFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentLocationPickerBinding? = null
    private val binding get() = requireNotNull(_binding) { "FragmentLocationPickerBinding accessed outside of onCreateView/onDestroyView" }

    private var googleMap: GoogleMap? = null
    private var selectedLatLng: LatLng? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLocationPickerBinding.inflate(inflater, container, false)
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
    }

    private fun searchLocation(query: String) {
        if (query.isBlank()) return
        
        try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            val addresses = geocoder.getFromLocationName(query, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val latLng = LatLng(address.latitude, address.longitude)
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            } else {
                Toast.makeText(context, "Location not found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error searching location", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        // Default to a central location (e.g., Israel) if no location is selected
        val defaultLocation = LatLng(32.0853, 34.7818)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f))

        map.setOnCameraIdleListener {
            val center = map.cameraPosition.target
            selectedLatLng = center
            updateAddress(center)
        }
    }

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
        _binding = null
    }
}
