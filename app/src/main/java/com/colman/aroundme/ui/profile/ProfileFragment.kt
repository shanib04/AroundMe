@file:Suppress("RedundantQualifierName")
package com.colman.aroundme.ui.profile

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.colman.aroundme.R
import com.colman.aroundme.databinding.FragmentProfileBinding
import com.colman.aroundme.ui.profile.ProfileViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.navigation.fragment.findNavController
import java.text.NumberFormat
import android.widget.TextView

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // Create AndroidViewModel using the AndroidViewModelFactory to ensure Application is provided
    private val viewModel: ProfileViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
    }

    private var currentUserId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // inflate with try/catch to avoid crashes due to layout inflation issues
        return try {
            _binding = FragmentProfileBinding.inflate(inflater, container, false)
            requireNotNull(_binding).root
        } catch (ex: Exception) {
            Log.e("ProfileFragment", "Failed to inflate profile layout", ex)
            // fallback: inflate a minimal view so we don't crash
            val fallback = inflater.inflate(android.R.layout.simple_list_item_1, container, false)
            Snackbar.make(fallback, "Could not load profile UI", Snackbar.LENGTH_LONG).show()
            fallback
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Attempt to get current Firebase user id
        val fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        currentUserId = fbUser?.uid ?: "local_user"

        // Observe and load inside try/catch to avoid runtime crashes
        try {
            observeViewModel()
            viewModel.loadCurrentUser(currentUserId)

            // Only access binding if inflation succeeded
            if (_binding != null) {
                // Toolbar navigation and menu
                binding.toolbarProfile.setNavigationOnClickListener { findNavController().popBackStack() }
                binding.toolbarProfile.inflateMenu(R.menu.menu_profile)
                binding.toolbarProfile.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_settings -> {
                            // Open edit profile screen directly
                            findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
                            true
                        }
                        R.id.action_logout -> {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Logout")
                                .setMessage("Are you sure you want to logout?")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Logout") { _, _ ->
                                    viewModel.logout { ok ->
                                        if (ok) {
                                            val nav = findNavController()
                                            nav.popBackStack(nav.graph.startDestinationId, false)
                                            nav.navigate(R.id.loginFragment)
                                        }
                                    }
                                }
                                .show()
                            true
                        }
                        else -> false
                    }
                }
                // radius slider interaction
                binding.radiusSlider.addOnChangeListener { _, value, _ ->
                    val km = value.toInt()
                    binding.radiusValueText.text = getString(R.string.map_radius_km_format, km)
                    viewModel.setRadiusKm(km)
                }
            }
        } catch (ex: Exception) {
            Log.e("ProfileFragment", "Error initializing profile UI", ex)
            // If view is available, show snackbar; otherwise log
            if (view != null) Snackbar.make(view, "Profile failed to load", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun observeViewModel() {
        val b = _binding ?: return

        viewModel.imageUri.observe(viewLifecycleOwner) { uri ->
            if (uri != null) Glide.with(this).load(uri).circleCrop().into(b.profileImageView)
            else b.profileImageView.setImageResource(R.drawable.ic_person_placeholder)
        }

        viewModel.name.observe(viewLifecycleOwner) { name ->
            b.profileNameText.text = name.ifBlank { getString(R.string.profile_not_available) }
        }

        viewModel.handle.observe(viewLifecycleOwner) { handle ->
            b.profileHandleText.text = handle
        }

        viewModel.userDegree.observe(viewLifecycleOwner) { degree ->
            b.userDegreeText.text = degree
            b.userDegreeText.isVisible = degree.isNotBlank()
        }

        viewModel.eventsCreated.observe(viewLifecycleOwner) { count ->
            b.eventsCountText.text = count.toString()
            val hasPosts = count > 0
            // show placeholder message when no posts, but always display the points card (green box)
            b.noPostsPlaceholder.isVisible = !hasPosts
            b.pointsCard.isVisible = true
        }

        viewModel.totalValidations.observe(viewLifecycleOwner) { total ->
            b.validationsCountText.text = total.toString()
        }

        viewModel.influenceScore.observe(viewLifecycleOwner) { inf ->
            b.influenceText.text = inf
        }

        viewModel.calculatedPoints.observe(viewLifecycleOwner) { pts ->
            b.pointsValueText.text = NumberFormat.getIntegerInstance().format(pts)
        }

        viewModel.levelLabel.observe(viewLifecycleOwner) { level ->
            b.levelPill.text = level
        }

        viewModel.progressText.observe(viewLifecycleOwner) { txt ->
            b.progressRightText.text = txt
        }

        viewModel.achievements.observe(viewLifecycleOwner) { list ->
            // populate achievement captions safely
            val l = list ?: emptyList()
            val first = l.getOrNull(0) ?: ""
            val second = l.getOrNull(1) ?: ""
            val third = l.getOrNull(2) ?: ""
            val row = b.achievementsRow
            if (row.childCount >= 3) {
                val a1 = row.getChildAt(0)
                val a2 = row.getChildAt(1)
                val a3 = row.getChildAt(2)
                (a1 as? ViewGroup)?.let { vg -> (vg.getChildAt(1) as? TextView)?.text = first }
                (a2 as? ViewGroup)?.let { vg -> (vg.getChildAt(1) as? TextView)?.text = second }
                (a3 as? ViewGroup)?.let { vg -> (vg.getChildAt(1) as? TextView)?.text = third }
            }
        }

        viewModel.radiusKm.observe(viewLifecycleOwner) { km ->
            binding.radiusValueText.text = getString(R.string.map_radius_km_format, km)
            binding.radiusSlider.value = km.toFloat()
        }

        // Observe progress percent and update progressFill width after layout
        viewModel.progressPercent.observe(viewLifecycleOwner) { percent ->
            val track = binding.progressTrack
            val fill = binding.progressFill
            track.post {
                val total = track.width - track.paddingLeft - track.paddingRight
                val fillWidth = (total * percent).toInt()
                val lp = fill.layoutParams
                lp.width = fillWidth
                fill.layoutParams = lp
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
