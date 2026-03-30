package com.colman.aroundme.features.create

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.colman.aroundme.R
import com.colman.aroundme.core.time.IsraelTime
import com.colman.aroundme.data.repository.EventRepository
import com.colman.aroundme.databinding.FragmentCreateEventBinding
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.io.File
import java.util.Calendar
import java.util.Locale

class CreateEventFragment : Fragment() {

    private var _binding: FragmentCreateEventBinding? = null
    private val binding: FragmentCreateEventBinding
        get() = requireNotNull(_binding) { "Binding is only valid between onCreateView and onDestroyView." }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var selectedLatitude: Double = 0.0
    private var selectedLongitude: Double = 0.0
    private var selectedGeohash: String = ""
    private var selectedLocationName: String = ""
    private var isEndManuallySelected = false
    private var hasExpirationTime = false

    private val startCalendar = IsraelTime.calendar()
    private val endCalendar = IsraelTime.calendar().apply {
        timeInMillis = startCalendar.timeInMillis
    }

    private val viewModel: CreateEventViewModel by viewModels {
        CreateEventViewModel.Factory(EventRepository.getInstance(requireContext()))
    }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            viewModel.setImageUri(it)
        }
    }

    private var cameraImageUri: Uri? = null
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraImageUri?.let {
                viewModel.setImageUri(it)
            }
        } else {
            Toast.makeText(context, "Camera cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            getCurrentLocation()
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val navArgs by lazy { CreateEventFragmentArgs.fromBundle(requireArguments()) }

    private val eventMode: String
        get() = navArgs.mode
    private val sourceEventId: String?
        get() = navArgs.eventId.ifBlank { null }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreateEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        applyModeUi()
        setupListeners()
        setupCategories()
        setupTagsAutocomplete()
        observeViewModel()
        updateDateTimeDisplays()

        parentFragmentManager.setFragmentResultListener("location_request", viewLifecycleOwner) { _, bundle ->
            selectedLatitude = bundle.getDouble("latitude")
            selectedLongitude = bundle.getDouble("longitude")
            selectedGeohash = bundle.getString("geohash").orEmpty()

            selectedLocationName =
                bundle.getString("address")?.takeIf { it.isNotBlank() }
                    ?: bundle.getString("locationName")?.takeIf { it.isNotBlank() }
                    ?: String.format(Locale.getDefault(), "%.4f, %.4f", selectedLatitude, selectedLongitude)

            binding.tvLocationName.text = selectedLocationName
            binding.tvLocationSubtitle.text = String.format(
                Locale.getDefault(),
                "%.4f, %.4f",
                selectedLatitude,
                selectedLongitude
            )
        }

        if (!sourceEventId.isNullOrBlank() && viewModel.editingEvent.value == null) {
            viewModel.loadEvent(requireNotNull(sourceEventId))
        }
    }

    private fun setupCategories() {
        val categories = listOf("Music", "Food", "Sport", "Art", "Beer", "Gaming", "Other")
        val adapter = ArrayAdapter(requireContext(), R.layout.item_dropdown_option, categories)
        binding.actvCategory.setAdapter(adapter)
    }

    private fun setupListeners() {
        binding.btnCancel.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnCamera.setOnClickListener {
            checkCameraPermissionAndLaunch()
        }

        binding.btnGallery.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        binding.btnLocation.setOnClickListener {
            showLocationSourceDialog()
        }

        binding.btnAddTag.setOnClickListener {
            addTag()
        }

        binding.btnDate.setOnClickListener {
            showDatePicker(startCalendar) { year, month, day ->
                startCalendar.set(year, month, day)
                if (!isEndManuallySelected || endCalendar.before(startCalendar)) {
                    endCalendar.timeInMillis = startCalendar.timeInMillis
                }
                updateDateTimeDisplays()
            }
        }

        binding.btnTime.setOnClickListener {
            showTimePicker(startCalendar) { hour, minute ->
                startCalendar.set(Calendar.HOUR_OF_DAY, hour)
                startCalendar.set(Calendar.MINUTE, minute)
                startCalendar.set(Calendar.SECOND, 0)
                startCalendar.set(Calendar.MILLISECOND, 0)
                if (!isEndManuallySelected || endCalendar.before(startCalendar)) {
                    endCalendar.timeInMillis = startCalendar.timeInMillis
                }
                updateDateTimeDisplays()
            }
        }

        binding.btnEndDate.setOnClickListener {
            showDatePicker(endCalendar) { year, month, day ->
                val tempCalendar = (endCalendar.clone() as Calendar).apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }

                if (tempCalendar.before(startCalendar)) {
                    Toast.makeText(context, "End time must be after or equal to start time", Toast.LENGTH_SHORT).show()
                } else {
                    endCalendar.set(Calendar.YEAR, year)
                    endCalendar.set(Calendar.MONTH, month)
                    endCalendar.set(Calendar.DAY_OF_MONTH, day)
                    isEndManuallySelected = true
                    updateDateTimeDisplays()
                }
            }
        }

        binding.btnEndTime.setOnClickListener {
            showTimePicker(endCalendar) { hour, minute ->
                val tempCalendar = (endCalendar.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                if (tempCalendar.before(startCalendar)) {
                    Toast.makeText(context, "End time must be after or equal to start time", Toast.LENGTH_SHORT).show()
                } else {
                    endCalendar.set(Calendar.HOUR_OF_DAY, hour)
                    endCalendar.set(Calendar.MINUTE, minute)
                    endCalendar.set(Calendar.SECOND, 0)
                    endCalendar.set(Calendar.MILLISECOND, 0)
                    isEndManuallySelected = true
                    updateDateTimeDisplays()
                }
            }
        }

        binding.btnPublish.setOnClickListener {
            val title = binding.etEventTitle.text.toString().trim()
            val description = binding.etEventDescription.text.toString().trim()
            val category = binding.actvCategory.text.toString()

            if (title.isEmpty()) {
                binding.etEventTitle.error = "Title is required"
                return@setOnClickListener
            }
            if (category.isEmpty()) {
                binding.actvCategory.error = "Category is required"
                return@setOnClickListener
            }
            if (!hasEventImage()) {
                Toast.makeText(context, "An image is required for the event", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedLocationName.isEmpty()) {
                Toast.makeText(context, "Please select a location", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.saveEvent(
                mode = eventMode,
                existingEventId = sourceEventId,
                title = title,
                description = description,
                locationName = selectedLocationName,
                latitude = selectedLatitude,
                longitude = selectedLongitude,
                geohash = selectedGeohash,
                category = category,
                tags = viewModel.tags.value ?: emptyList(),
                publishTime = startCalendar.timeInMillis,
                expirationTime = if (hasExpirationTime) endCalendar.timeInMillis else 0L,
                imageUri = viewModel.selectedImageUri.value
            )
        }

        // Initially hide end time controls
        binding.groupEndTime.visibility = View.GONE

        // Toggle for enabling/disabling end time
        binding.switchEndTime.setOnCheckedChangeListener { _, isChecked: Boolean ->
            hasExpirationTime = isChecked
            binding.groupEndTime.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    private fun checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchCamera() {
        try {
            val photoFile = File(
                requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "IMG_${System.currentTimeMillis()}.jpg"
            )
            cameraImageUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )
            cameraImageUri?.let { uri ->
                cameraLauncher.launch(uri)
            } ?: run {
                Toast.makeText(context, "Unable to open camera", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error creating image file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDateTimeDisplays() {
        val dateSdf = IsraelTime.formatter("MMM dd, yyyy", Locale.getDefault())
        val timeSdf = IsraelTime.formatter("HH:mm", Locale.getDefault())

        binding.btnDate.text = dateSdf.format(startCalendar.time)
        binding.btnTime.text = timeSdf.format(startCalendar.time)
        binding.btnEndDate.text = dateSdf.format(endCalendar.time)
        binding.btnEndTime.text = timeSdf.format(endCalendar.time)
    }

    private fun showDatePicker(baseCalendar: Calendar, onSelected: (Int, Int, Int) -> Unit) {
        DatePickerDialog(
            requireContext(),
            { _, year, month, day -> onSelected(year, month, day) },
            baseCalendar.get(Calendar.YEAR),
            baseCalendar.get(Calendar.MONTH),
            baseCalendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker(baseCalendar: Calendar, onSelected: (Int, Int) -> Unit) {
        TimePickerDialog(
            requireContext(),
            { _, hour, minute -> onSelected(hour, minute) },
            baseCalendar.get(Calendar.HOUR_OF_DAY),
            baseCalendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun showLocationSourceDialog() {
        val options = arrayOf("Current Location", "Pick on Map")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Select Location Source")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkLocationPermission()
                    1 -> findNavController().navigate(R.id.action_createEventFragment_to_locationPickerFragment)
                }
            }
            .show()
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun setupTagsAutocomplete() {
         val suggestedTags = listOf("Music", "Food", "Sport", "Art", "Free", "Family", "Nightlife")
            val adapter = ArrayAdapter(requireContext(), R.layout.item_dropdown_option, suggestedTags)
         binding.etAddTag.setAdapter(adapter)
    }

    private fun addTag() {
        val currentTags = (viewModel.tags.value ?: emptyList()).toMutableList()
        if (currentTags.size >= 5) {
            Toast.makeText(context, "Maximum 5 tags allowed", Toast.LENGTH_SHORT).show()
            return
        }

        val tagText = binding.etAddTag.text.toString().trim()
        if (tagText.isNotEmpty()) {
            val formattedTag = tagText.replaceFirstChar { it.uppercase() }
            if (!currentTags.contains(formattedTag)) {
                currentTags.add(formattedTag)
                viewModel.setTags(currentTags)
                binding.etAddTag.text.clear()
            } else {
                Toast.makeText(context, "Tag already added", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.tags.observe(viewLifecycleOwner) { tags ->
            binding.chipGroupTags.removeAllViews()
            tags.forEach { tag ->
                addChipToGroup(tag)
            }
            binding.tvTagsCount.text = "${tags.size}/5"
        }

        viewModel.selectedImageUri.observe(viewLifecycleOwner) { uri ->
            renderEventImage(uri, viewModel.editingEvent.value?.imageUrl)
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is CreateEventUiState.Loading -> {
                    binding.loadingOverlay.isVisible = true
                    binding.btnPublish.isEnabled = false
                }
                is CreateEventUiState.Success -> {
                    binding.loadingOverlay.isVisible = false
                    Toast.makeText(context, "Event created successfully!", Toast.LENGTH_SHORT).show()
                    if (eventMode == "edit") {
                        findNavController().popBackStack()
                    } else {
                        openEventDetails(state.eventId)
                    }
                }
                is CreateEventUiState.Error -> {
                    binding.loadingOverlay.isVisible = false
                    binding.btnPublish.isEnabled = true
                    Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                }
                else -> {
                    binding.loadingOverlay.isVisible = false
                    binding.btnPublish.isEnabled = true
                }
            }
        }

        viewModel.editingEvent.observe(viewLifecycleOwner) { event ->
            val currentEvent = event ?: return@observe
            binding.etEventTitle.setText(currentEvent.title)
            binding.etEventDescription.setText(currentEvent.description)
            binding.actvCategory.setText(currentEvent.category, false)
            selectedLatitude = currentEvent.latitude
            selectedLongitude = currentEvent.longitude
            selectedGeohash = currentEvent.geohash
            selectedLocationName = currentEvent.locationName
            binding.tvLocationName.text = currentEvent.locationName
            binding.tvLocationSubtitle.text = String.format(Locale.getDefault(), "%.4f, %.4f", currentEvent.latitude, currentEvent.longitude)
            startCalendar.timeInMillis = if (eventMode == "recreate") System.currentTimeMillis() else currentEvent.publishTime
            endCalendar.timeInMillis = if (currentEvent.expirationTime > 0L && eventMode != "recreate") currentEvent.expirationTime else startCalendar.timeInMillis
            hasExpirationTime = currentEvent.expirationTime > 0L && eventMode != "recreate"
            binding.switchEndTime.isChecked = hasExpirationTime
            binding.groupEndTime.isVisible = hasExpirationTime
            updateDateTimeDisplays()
            renderEventImage(viewModel.selectedImageUri.value, currentEvent.imageUrl)
            binding.btnPublish.text = if (eventMode == "edit") getString(R.string.my_events_edit) else getString(R.string.my_events_recreate)
        }
    }

    private fun applyModeUi() {
        binding.screenTitle.text = when (eventMode) {
            "edit" -> getString(R.string.my_events_edit)
            "recreate" -> getString(R.string.my_events_recreate)
            else -> getString(R.string.create_event_title)
        }

        if (eventMode == "create") {
            binding.btnPublish.text = getString(R.string.create_event_title)
        }
    }

    private fun hasEventImage(): Boolean {
        return viewModel.selectedImageUri.value != null || !viewModel.editingEvent.value?.imageUrl.isNullOrBlank()
    }

    private fun openEventDetails(eventId: String) {
        val navController = findNavController()
        navController.navigate(
            R.id.eventDetailsFragment,
            Bundle().apply { putString("eventId", eventId) },
            NavOptions.Builder()
                .setPopUpTo(R.id.createEventFragment, true)
                .build()
        )
    }

    private fun renderEventImage(selectedUri: Uri?, existingImageUrl: String?) {
        when {
            selectedUri != null -> {
                binding.placeholderContainer.isVisible = false
                Glide.with(this).load(selectedUri).centerCrop().into(binding.ivEventImage)
            }
            !existingImageUrl.isNullOrBlank() -> {
                binding.placeholderContainer.isVisible = false
                Glide.with(this).load(existingImageUrl).centerCrop().into(binding.ivEventImage)
            }
            else -> {
                binding.placeholderContainer.isVisible = true
                binding.ivEventImage.setImageDrawable(null)
            }
        }
    }

    private fun addChipToGroup(tag: String) {
        val chip = com.google.android.material.chip.Chip(requireContext())
        chip.text = "#$tag"
        chip.isCloseIconVisible = true
        chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.create_tag_text))
        chip.setChipBackgroundColorResource(R.color.white)
        chip.setChipStrokeColorResource(R.color.create_input_border)
        chip.chipStrokeWidth = 2f
        chip.setOnCloseIconClickListener {
            val currentTags = (viewModel.tags.value ?: emptyList()).toMutableList()
            currentTags.remove(tag)
            viewModel.setTags(currentTags)
        }
        binding.chipGroupTags.addView(chip)
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        binding.tvLocationName.text = "Getting location..."
        val cancellationTokenSource = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location ->
            if (location != null) {
                selectedLatitude = location.latitude
                selectedLongitude = location.longitude
                selectedGeohash = GeoFireUtils.getGeoHashForLocation(GeoLocation(location.latitude, location.longitude))

                binding.tvLocationSubtitle.text = String.format(Locale.getDefault(), "%.4f, %.4f", location.latitude, location.longitude)

                try {
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        selectedLocationName = addresses[0].getAddressLine(0) ?: "Fixed Location"
                        binding.tvLocationName.text = selectedLocationName
                    } else {
                        selectedLocationName = "Current Location"
                        binding.tvLocationName.text = selectedLocationName
                    }
                } catch (e: Exception) {
                    selectedLocationName = "Current Location"
                    binding.tvLocationName.text = selectedLocationName
                }

            } else {
                Toast.makeText(context, "Unable to get location", Toast.LENGTH_SHORT).show()
                binding.tvLocationName.text = "Select Location"
            }
        }.addOnFailureListener {
            Toast.makeText(context, "Failed to get location: ${it.message}", Toast.LENGTH_SHORT).show()
            binding.tvLocationName.text = "Select Location"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
