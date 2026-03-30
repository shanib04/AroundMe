package com.colman.aroundme.features.event

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.colman.aroundme.R
import com.colman.aroundme.utils.IsraelTime
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.data.model.EventVoteType
import com.colman.aroundme.data.model.NearbyPlace
import com.colman.aroundme.data.model.User
import com.colman.aroundme.data.model.versionedProfileImageUrl
import com.colman.aroundme.data.remote.FirebaseModel
import com.colman.aroundme.data.repository.EventRepository
import com.colman.aroundme.data.repository.PlacesRepository
import com.colman.aroundme.data.repository.UserRepository
import com.colman.aroundme.databinding.FragmentEventDetailsBinding
import java.util.Locale

class EventDetailsFragment : Fragment() {

    private var _binding: FragmentEventDetailsBinding? = null
    private val binding: FragmentEventDetailsBinding
        get() = requireNotNull(_binding) { "FragmentEventDetailsBinding accessed outside of view lifecycle" }

    private val args by lazy { EventDetailsFragmentArgs.fromBundle(requireArguments()) }

    private val viewModel: EventDetailsViewModel by viewModels {
        EventDetailsViewModel.Factory(
            eventId = args.eventId,
            eventRepository = EventRepository.getInstance(requireContext()),
            userRepository = UserRepository.getInstance(requireContext()),
            placesRepository = PlacesRepository.getInstance(),
            firebaseModel = FirebaseModel.getInstance()
        )
    }

    private val nearbyAdapter by lazy {
        NearbyEssentialsAdapter(::openPlace)
    }

    private val eventTimePrimaryView: TextView
        get() = binding.root.findViewById(R.id.eventTimePrimary)

    private val eventTimeSecondaryView: TextView
        get() = binding.root.findViewById(R.id.eventTimeSecondary)

    private var suppressRatingListener = false
    private var lastRenderedPublisherImageUrl: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEventDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecycler()
        bindActions()
        observeViewModel()

        // Default pill selection state
        renderSelectedEssentials(viewModel.selectedEssentialsType.value ?: EventDetailsViewModel.EssentialsType.PARKING)
    }

    private fun setupRecycler() {
        binding.nearbyRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = nearbyAdapter
            itemAnimator = null
        }
    }

    private fun bindActions() {
        binding.backButton.setOnClickListener { findNavController().navigateUp() }

        binding.btnShare.setOnClickListener {
            val event = viewModel.event.value
            if (event == null) {
                Toast.makeText(requireContext(), R.string.event_details_not_found, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val shareText = "${event.title}\n${event.locationName}\nhttps://maps.google.com/?q=${event.latitude},${event.longitude}"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.feed_share)))
        }

        binding.navigateButton.setOnClickListener {
            val event = viewModel.event.value ?: return@setOnClickListener
            openNavigation(event.latitude, event.longitude, event.title.ifBlank { event.locationName })
        }

        binding.stillHappeningButton.setOnClickListener {
            viewModel.submitVote(EventVoteType.ACTIVE)
        }

        binding.endedButton.setOnClickListener {
            viewModel.submitVote(EventVoteType.INACTIVE)
        }

        binding.filterParking.setOnClickListener { viewModel.loadNearby(EventDetailsViewModel.EssentialsType.PARKING) }
        binding.filterFood.setOnClickListener { viewModel.loadNearby(EventDetailsViewModel.EssentialsType.FOOD) }
        binding.filterGas.setOnClickListener { viewModel.loadNearby(EventDetailsViewModel.EssentialsType.GAS) }

        binding.ratingBar.onRatingBarChangeListener = RatingBar.OnRatingBarChangeListener { _, rating, fromUser ->
            if (!fromUser || suppressRatingListener) return@OnRatingBarChangeListener
            val stars = rating.toDouble().coerceIn(1.0, 5.0)
            viewModel.submitRating(stars)
        }
    }

    private fun observeViewModel() {
        viewModel.event.observe(viewLifecycleOwner) { event ->
            if (event == null) {
                Toast.makeText(requireContext(), R.string.event_details_not_found, Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
                return@observe
            }
            renderEvent(event)
        }

        viewModel.publisher.observe(viewLifecycleOwner) { user ->
            renderPublisher(user)
        }

        viewModel.screenLoading.observe(viewLifecycleOwner) { loading ->
            binding.detailsLoadingOverlay.isVisible = loading
            binding.scrollView.isVisible = !loading
        }

        viewModel.selectedVoteType.observe(viewLifecycleOwner) { voteType ->
            renderVoteSelection(voteType)
        }

        viewModel.myRating.observe(viewLifecycleOwner) { my ->
            suppressRatingListener = true
            binding.ratingBar.rating = (my ?: 0).toFloat()
            suppressRatingListener = false
        }

        viewModel.isSubmittingVote.observe(viewLifecycleOwner) { submitting ->
            binding.stillHappeningButton.isEnabled = !submitting
            binding.endedButton.isEnabled = !submitting
            binding.stillHappeningButton.alpha = if (submitting) 0.6f else 1f
            binding.endedButton.alpha = if (submitting) 0.6f else 1f
        }

        viewModel.isSubmittingRating.observe(viewLifecycleOwner) { submitting ->
            binding.ratingBar.isEnabled = !submitting
            binding.rateHint.isVisible = !submitting
            if (submitting) {
                binding.rateHint.text = "Saving…"
            } else {
                binding.rateHint.text = "Tap to rate"
            }
        }

        viewModel.nearbyLoading.observe(viewLifecycleOwner) { loading ->
            binding.nearbyLoading.isVisible = loading
        }

        viewModel.nearbyPlaces.observe(viewLifecycleOwner) { places ->
            nearbyAdapter.submitList(places)
            binding.nearbyEmpty.isVisible = places.isEmpty() && (viewModel.nearbyLoading.value != true)
        }

        viewModel.selectedEssentialsType.observe(viewLifecycleOwner) { type ->
            renderSelectedEssentials(type)
            nearbyAdapter.fallbackType = type
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (message.isNullOrBlank()) return@observe
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            viewModel.onErrorMessageShown()
        }
    }

    private fun renderEvent(event: Event) {
        binding.eventTitle.text = event.title
        binding.aboutText.text = event.description
        eventTimePrimaryView.text = primaryTimeText(event)
        eventTimeSecondaryView.text = secondaryTimeText(event)

        // Location text: we don't have subtitle in model, keep a subtle variant line with coordinates
        binding.locationName.text = event.locationName.ifBlank { getString(R.string.event_unknown_location) }
        binding.locationSubtitle.text = if (event.locationName.isNotBlank()) {
            "Entrance at ${String.format(Locale.US, "%.5f", event.latitude)}, ${String.format(Locale.US, "%.5f", event.longitude)}"
        } else {
            getString(R.string.event_unknown_location)
        }

        binding.confirmedText.text = "${event.activeVotes} confirmed"
        binding.reportsText.text = "${event.inactiveVotes} reports"

        if (event.ratingCount > 0) {
            binding.ratingValue.text = String.format(Locale.US, "%.1f", event.averageRating)
            binding.ratingCount.text = "${event.ratingCount} reviews"
        } else {
            binding.ratingValue.text = "0.0"
            binding.ratingCount.text = "No reviews yet"
        }

        // Tags
        binding.tagsChipGroup.removeAllViews()
        val tags = event.tags.takeIf { it.isNotEmpty() } ?: listOf(event.category).filter { it.isNotBlank() }
        tags.forEach { tag ->
            binding.tagsChipGroup.addView(EventTagChipBuilder.create(requireContext(), tag))
        }

        Glide.with(this)
            .load(event.imageUrl)
            .centerCrop()
            .into(binding.heroImage)
    }

    private fun renderPublisher(user: User?) {
        binding.publisherName.text = user?.displayName?.ifBlank { user.username } ?: getString(R.string.profile_not_available)
        binding.publisherHandle.text = user?.username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: ""
        binding.publisherHandle.isVisible = !binding.publisherHandle.text.isNullOrBlank()

        val imageUrl = user?.versionedProfileImageUrl()?.ifBlank { null }
        if (imageUrl != lastRenderedPublisherImageUrl) {
            lastRenderedPublisherImageUrl = imageUrl
            Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.ic_person_placeholder)
                .error(R.drawable.ic_person_placeholder)
                .centerCrop()
                .into(binding.publisherAvatar)
        }
    }

    private fun renderVoteSelection(selectedVoteType: EventVoteType?) {
        val neutralBg = requireContext().getColor(R.color.ds_surface_container_high)
        val neutralText = requireContext().getColor(R.color.ds_on_surface_variant)
        val neutralStroke = requireContext().getColor(R.color.ds_surface_container_high)

        val activeBg = requireContext().getColor(R.color.event_positive)
        val activeText = requireContext().getColor(R.color.white)
        val activeStroke = activeBg

        val inactiveBg = requireContext().getColor(R.color.event_negative)
        val inactiveText = requireContext().getColor(R.color.white)
        val inactiveStroke = inactiveBg

        fun style(button: com.google.android.material.button.MaterialButton, selected: Boolean, bg: Int, text: Int, stroke: Int) {
            button.setBackgroundColor(if (selected) bg else neutralBg)
            button.setTextColor(if (selected) text else neutralText)
            button.strokeColor = android.content.res.ColorStateList.valueOf(if (selected) stroke else neutralStroke)
        }

        style(
            button = binding.stillHappeningButton,
            selected = selectedVoteType == EventVoteType.ACTIVE,
            bg = activeBg,
            text = activeText,
            stroke = activeStroke
        )
        style(
            button = binding.endedButton,
            selected = selectedVoteType == EventVoteType.INACTIVE,
            bg = inactiveBg,
            text = inactiveText,
            stroke = inactiveStroke
        )
    }

    private fun renderSelectedEssentials(type: EventDetailsViewModel.EssentialsType) {
        // Active: primary bg + onPrimary text. Inactive: surface-container-high + onSurfaceVariant.
        val activeBg = requireContext().getColor(R.color.ds_secondary)
        val activeText = requireContext().getColor(R.color.ds_on_secondary)
        val inactiveBg = requireContext().getColor(R.color.ds_surface_container_high)
        val inactiveText = requireContext().getColor(R.color.ds_on_surface_variant)

        fun style(button: com.google.android.material.button.MaterialButton, selected: Boolean) {
            button.setBackgroundColor(if (selected) activeBg else inactiveBg)
            button.setTextColor(if (selected) activeText else inactiveText)
        }

        style(binding.filterParking, type == EventDetailsViewModel.EssentialsType.PARKING)
        style(binding.filterFood, type == EventDetailsViewModel.EssentialsType.FOOD)
        style(binding.filterGas, type == EventDetailsViewModel.EssentialsType.GAS)
    }

    private fun openNavigation(lat: Double, lng: Double, label: String) {
        val encodedLabel = Uri.encode(label)
        val geo = Uri.parse("geo:0,0?q=$lat,$lng($encodedLabel)")
        val intent = Intent(Intent.ACTION_VIEW, geo)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.map_navigation_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPlace(place: NearbyPlace) {
        val placeId = place.placeId
        val uri = if (!placeId.isNullOrBlank()) {
            Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(place.name)}&query_place_id=$placeId")
        } else {
            Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(place.name + " " + place.vicinity)}")
        }
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun primaryTimeText(event: Event): String {
        return if (event.isEnded) {
            "Ended"
        } else if (event.expirationTime > 0L) {
            "Ends ${formatEventDateTime(event.expirationTime)}"
        } else {
            "Live now"
        }
    }

    private fun secondaryTimeText(event: Event): String {
        return if (event.publishTime > 0L) {
            "Posted ${formatEventDateTime(event.publishTime)}"
        } else {
            "Start time not available"
        }
    }

    private fun formatEventDateTime(timestamp: Long): String {
        return IsraelTime.formatDateTime(timestamp, locale = Locale.getDefault())
    }
}