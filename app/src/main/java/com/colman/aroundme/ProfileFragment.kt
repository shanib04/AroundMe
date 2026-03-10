package com.colman.aroundme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.colman.aroundme.auth.ProfileUiState
import com.colman.aroundme.auth.ProfileViewModel
import com.colman.aroundme.databinding.FragmentProfileBinding
import com.google.android.material.snackbar.Snackbar

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null

    private val viewModel: ProfileViewModel by viewModels {
        ProfileViewModel.Factory()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return requireNotNull(_binding).root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
        viewModel.loadProfile()
    }

    private fun observeViewModel() {
        val binding = _binding ?: return
        viewModel.profileState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ProfileUiState.Loading -> renderLoading(true)
                is ProfileUiState.Success -> {
                    renderLoading(false)
                    val profile = state.profile
                    binding.nameValue.text = profile.fullName.ifBlank { getString(R.string.profile_not_available) }
                    binding.emailValue.text = profile.email.ifBlank { getString(R.string.profile_not_available) }
                    binding.idValue.text = profile.userId.ifBlank { getString(R.string.profile_not_available) }
                    renderProfileImage(profile.imageUrl)
                }
                is ProfileUiState.Error -> {
                    renderLoading(false)
                    binding.nameValue.text = getString(R.string.profile_not_available)
                    binding.emailValue.text = getString(R.string.profile_not_available)
                    binding.idValue.text = getString(R.string.profile_not_available)
                    renderProfileImage(null)
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun renderProfileImage(imageUrl: String?) {
        val binding = _binding ?: return
        Glide.with(this).clear(binding.profileImageView)
        val source = imageUrl?.takeIf { it.isNotBlank() }
        Glide.with(this)
            .load(source ?: R.drawable.ic_person_placeholder)
            .circleCrop()
            .placeholder(R.drawable.ic_person_placeholder)
            .error(R.drawable.ic_person_placeholder)
            .into(binding.profileImageView)
    }

    private fun renderLoading(isLoading: Boolean) {
        val binding = _binding ?: return
        binding.progressBar.isVisible = isLoading
        binding.profileCard.isVisible = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
