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
import com.colman.aroundme.ui.auth.ProfileViewModel
import com.google.android.material.snackbar.Snackbar

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
                binding.editProfileButton.setOnClickListener {
                    Snackbar.make(binding.root, "Edit profile is not implemented yet", Snackbar.LENGTH_SHORT).show()
                }

                // radius slider interaction: update label when changed (display-only save not required)
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

        viewModel.isValidator.observe(viewLifecycleOwner) { isVal ->
            b.validatorBadge.isVisible = isVal
        }

        viewModel.eventsCount.observe(viewLifecycleOwner) { count ->
            b.eventsCountText.text = count.toString()
        }

        viewModel.validationsCount.observe(viewLifecycleOwner) { count ->
            b.validationsCountText.text = count.toString()
        }

        viewModel.pointsValue.observe(viewLifecycleOwner) { pts ->
            b.pointsValueText.text = pts
        }

        viewModel.levelLabel.observe(viewLifecycleOwner) { level ->
            b.levelPill.text = level
        }

        viewModel.progressText.observe(viewLifecycleOwner) { txt ->
            b.progressRightText.text = txt
        }

        viewModel.achievements.observe(viewLifecycleOwner) { list ->
            // static layout contains three achievement slots; populate their captions
            if (list.isNotEmpty()) {
                val first = list.getOrNull(0) ?: ""
                val second = list.getOrNull(1) ?: ""
                val third = list.getOrNull(2) ?: ""
                // the layout uses children with text labels; find them by traversing the achievementsRow
                val row = b.achievementsRow
                if (row.childCount >= 3) {
                    val a1 = row.getChildAt(0)
                    val a2 = row.getChildAt(1)
                    val a3 = row.getChildAt(2)
                    // each is a LinearLayout with a TextView as the second child
                    (a1 as? android.view.ViewGroup)?.let { vg -> (vg.getChildAt(1) as? android.widget.TextView)?.text = first }
                    (a2 as? android.view.ViewGroup)?.let { vg -> (vg.getChildAt(1) as? android.widget.TextView)?.text = second }
                    (a3 as? android.view.ViewGroup)?.let { vg -> (vg.getChildAt(1) as? android.widget.TextView)?.text = third }
                }
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
