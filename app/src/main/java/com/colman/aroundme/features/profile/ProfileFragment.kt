package com.colman.aroundme.features.profile

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.colman.aroundme.R
import com.colman.aroundme.data.model.Achievement
import com.colman.aroundme.databinding.FragmentProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.NumberFormat

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = requireNotNull(_binding) { "FragmentProfileBinding accessed outside of onCreateView/onDestroyView" }

    private val viewModel: ProfileViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
    }

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

        try {
            observeViewModel()
            viewModel.loadCurrentUser()

            if (_binding != null) {
                binding.toolbarProfile.setNavigationOnClickListener {
                    findNavController().navigate(R.id.feedFragment)
                }
                binding.toolbarProfile.menu.clear()
                binding.toolbarProfile.inflateMenu(R.menu.menu_profile)
                binding.toolbarProfile.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_edit_profile -> {
                            findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
                            true
                        }
                        R.id.action_logout -> {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Logout")
                                .setMessage("Are you sure you want to logout?")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Logout") { _, _ ->
                                    viewModel.logout()
                                }
                                .show()
                            true
                        }
                        else -> false
                    }
                }
                binding.viewAllAchievementsText.setOnClickListener {
                    findNavController().navigate(R.id.action_profileFragment_to_achievementsHistoryFragment)
                }
                // radius slider interaction
                binding.radiusSlider.addOnChangeListener { _, value, fromUser ->
                    val km = value.toInt()
                    binding.radiusValueText.text = getString(R.string.map_radius_km_format, km)
                    if (fromUser) {
                        viewModel.setRadiusKm(km)
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e("ProfileFragment", "Error initializing profile UI", ex)
            Snackbar.make(view, "Profile failed to load", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadCurrentUser()
    }

    private fun observeViewModel() {
        val profileBinding = _binding ?: return

        viewModel.logoutState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ProfileViewModel.LogoutState.Idle,
                is ProfileViewModel.LogoutState.Loading -> Unit
                is ProfileViewModel.LogoutState.Success -> {
                    viewModel.consumeLogoutState()
                    val navController = findNavController()
                    navController.navigate(
                        R.id.loginFragment,
                        null,
                        NavOptions.Builder()
                            .setLaunchSingleTop(true)
                            .setPopUpTo(R.id.loginFragment, true)
                            .build()
                    )
                }
                is ProfileViewModel.LogoutState.Error -> {
                    viewModel.consumeLogoutState()
                    Snackbar.make(profileBinding.root, state.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        viewModel.imageUri.observe(viewLifecycleOwner) { uri ->
            if (uri != null) {
                Glide.with(this)
                    .load(uri)
                    .placeholder(R.drawable.ic_person_placeholder)
                    .error(R.drawable.ic_person_placeholder)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .circleCrop()
                    .into(profileBinding.profileImageView)
            } else {
                profileBinding.profileImageView.setImageResource(R.drawable.ic_person_placeholder)
            }
        }

        viewModel.displayName.observe(viewLifecycleOwner) { displayName ->
            profileBinding.profileNameText.text = displayName.ifBlank { getString(R.string.profile_not_available) }
        }

        viewModel.username.observe(viewLifecycleOwner) { username ->
            profileBinding.profileHandleText.text = username.takeIf { it.isNotBlank() }?.let { "@$it" }.orEmpty()
            profileBinding.profileHandleText.isVisible = username.isNotBlank()
        }

        viewModel.eventsCreated.observe(viewLifecycleOwner) { count ->
            profileBinding.eventsCountText.text = count.toString()
            val hasPosts = count > 0
            profileBinding.noPostsPlaceholder.isVisible = !hasPosts
            profileBinding.pointsCard.isVisible = true
        }

        viewModel.totalValidations.observe(viewLifecycleOwner) { total ->
            profileBinding.validationsCountText.text = total.toString()
        }

        viewModel.calculatedPoints.observe(viewLifecycleOwner) { pts ->
            val formatted = NumberFormat.getIntegerInstance().format(pts)
            profileBinding.pointsValueText.text = formatted
        }

        viewModel.levelLabel.observe(viewLifecycleOwner) { level ->
            profileBinding.levelPill.text = level
        }

        viewModel.progressText.observe(viewLifecycleOwner) { txt ->
            profileBinding.progressRightText.text = txt
        }

        viewModel.achievements.observe(viewLifecycleOwner) { list ->
            bindAchievementPreview(list.orEmpty())
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

        viewModel.isReliableContributor.observe(viewLifecycleOwner) { isReliable ->
            profileBinding.reliableBadge.isVisible = isReliable
        }

        viewModel.pointsSummaryText.observe(viewLifecycleOwner) { pointsText ->
            profileBinding.pointsSummaryText.text = pointsText
        }

        viewModel.completionPercentText.observe(viewLifecycleOwner) { completionText ->
            profileBinding.completionPercentText.text = completionText
        }
    }

    private fun bindAchievementPreview(achievements: List<Achievement>) {
        val binding = _binding ?: return
        val slots = listOf(
            Triple(binding.achievementSlotOne, binding.achievementOneIconText, binding.achievementOneNameText),
            Triple(binding.achievementSlotTwo, binding.achievementTwoIconText, binding.achievementTwoNameText),
            Triple(binding.achievementSlotThree, binding.achievementThreeIconText, binding.achievementThreeNameText)
        )

        slots.forEachIndexed { index, (container, iconView, nameView) ->
            val achievement = achievements.getOrNull(index)
            if (achievement == null) {
                container.isVisible = false
                container.contentDescription = null
                container.setOnClickListener(null)
                container.isClickable = false
                container.isFocusable = false
            } else {
                container.isVisible = true
                iconView.text = achievement.icon
                nameView.text = achievement.name
                iconView.setBackgroundResource(backgroundForAchievement(achievement))
                container.contentDescription = achievement.name
                container.setOnClickListener(null)
                container.isClickable = false
                container.isFocusable = false
            }
        }
    }

    private fun backgroundForAchievement(achievement: Achievement): Int {
        val name = achievement.name.lowercase()
        return when {
            name.contains("rising") ||
                name.contains("legend") ||
                name.contains("fresh face") -> R.drawable.ach_bg_orange

            name.contains("trustworthy") ||
                name.contains("oracle") ||
                name.contains("fact checker") -> R.drawable.ach_bg_blue

            else -> R.drawable.ach_bg_purple
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
