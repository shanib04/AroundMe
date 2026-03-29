package com.colman.aroundme.features.profile

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.core.widget.addTextChangedListener
import androidx.navigation.fragment.findNavController
import com.colman.aroundme.R
import com.colman.aroundme.core.time.IsraelTime
import com.bumptech.glide.Glide
import com.colman.aroundme.databinding.FragmentEditProfileBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class EditProfileFragment : Fragment() {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = requireNotNull(_binding) { "FragmentEditProfileBinding accessed outside of onCreateView/onDestroyView" }

    private val viewModel: EditProfileViewModel by viewModels({ requireActivity() })

    private var tempImageUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            tempImageUri = it
            Glide.with(this).load(it).circleCrop().into(binding.editProfileImageView)
        }
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        if (success && tempImageUri != null) {
            Glide.with(this).load(tempImageUri).circleCrop().into(binding.editProfileImageView)
        }
    }

    // Request camera permission at runtime
    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createImageFileUri()
            if (uri != null) {
                tempImageUri = uri
                takePicture.launch(uri)
            } else {
                pickImage.launch("image/*")
            }
        } else {
            // fallback to gallery if permission denied
            pickImage.launch("image/*")
        }
    }

    private fun createImageFileUri(): Uri? {
        val timestamp = IsraelTime.formatter("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val fileName = "IMG_${timestamp}.jpg"
        val file = File(requireContext().cacheDir, fileName)
        // ensure file exists
        file.outputStream().use { }
        return FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return requireNotNull(_binding).root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.saveState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is EditProfileViewModel.SaveState.Idle,
                is EditProfileViewModel.SaveState.Loading -> Unit
                is EditProfileViewModel.SaveState.Success -> {
                    viewModel.consumeSaveState()
                    if (_binding == null) return@observe
                    Snackbar.make(binding.root, state.message ?: getString(R.string.edit_profile_saved), Snackbar.LENGTH_SHORT).show()
                }
                is EditProfileViewModel.SaveState.Error -> {
                    viewModel.consumeSaveState()
                    if (_binding == null) return@observe
                    Snackbar.make(binding.root, getString(R.string.edit_profile_save_failed, state.message), Snackbar.LENGTH_LONG).show()
                }
            }
        }

        viewModel.deleteState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is EditProfileViewModel.DeleteState.Idle,
                is EditProfileViewModel.DeleteState.Loading -> Unit
                is EditProfileViewModel.DeleteState.Success -> {
                    viewModel.consumeDeleteState()
                    if (!isAdded || _binding == null) return@observe
                    findNavController().navigate(
                        R.id.loginFragment,
                        null,
                        androidx.navigation.NavOptions.Builder()
                            .setLaunchSingleTop(true)
                            .setPopUpTo(R.id.loginFragment, true)
                            .build()
                    )
                }
                is EditProfileViewModel.DeleteState.Error -> {
                    viewModel.consumeDeleteState()
                    if (!isAdded) return@observe
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle(R.string.edit_profile_error_title)
                        .setMessage(state.message)
                        .setPositiveButton(R.string.save, null)
                        .show()
                }
            }
        }

        binding.emailEditText.apply {
            isEnabled = false
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = false
            keyListener = null
        }
        binding.emailLayout.helperText = getString(R.string.profile_email_read_only)

        binding.btnCancel.setOnClickListener { findNavController().popBackStack() }

        if (viewModel.currentUserId().isNotBlank()) {
            tempImageUri = null
            viewModel.loadCurrentUser()
        }

        // Username inline validation: regex while typing, remote uniqueness on focus lost
        val usernamePattern = "^[a-z0-9_-]{1,15}$".toRegex()
        binding.usernameEditText.addTextChangedListener {
            val txt = it?.toString().orEmpty()
            if (txt.isNotBlank() && !usernamePattern.matches(txt)) {
                binding.usernameEditText.error = getString(R.string.edit_profile_username_invalid_inline)
            } else {
                binding.usernameEditText.error = null
            }
        }

        binding.usernameEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val candidate = binding.usernameEditText.text?.toString().orEmpty()
                if (candidate.isNotBlank() && usernamePattern.matches(candidate)) {
                    lifecycleScope.launch {
                        val taken = viewModel.isUsernameTakenRemote(candidate, viewModel.currentUserId())
                        if (taken && _binding != null) binding.usernameEditText.error = getString(R.string.edit_profile_username_already_taken)
                    }
                }
            }
        }

        viewModel.email.observe(viewLifecycleOwner) { email -> binding.emailEditText.setText(email) }
        viewModel.username.observe(viewLifecycleOwner) { username -> binding.usernameEditText.setText(username) }
        viewModel.displayName.observe(viewLifecycleOwner) { dn -> binding.displayNameEditText.setText(dn) }
        viewModel.imageUri.observe(viewLifecycleOwner) { uri ->
            if (uri != null) Glide.with(this).load(uri).circleCrop().into(binding.editProfileImageView)
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.saveProgress.visibility = if (loading) View.VISIBLE else View.GONE
            binding.saveButton.isEnabled = !loading
            binding.deleteButton.isEnabled = !loading
            binding.emailEditText.isEnabled = !loading
            binding.cameraButton.isEnabled = !loading
            binding.galleryButton.isEnabled = !loading
        }

        viewModel.uploadProgress.observe(viewLifecycleOwner) { p ->
            if (p > 0) {
                binding.uploadProgressBar.visibility = View.VISIBLE
                binding.uploadProgressBar.progress = p
            } else {
                binding.uploadProgressBar.visibility = View.GONE
            }
        }

        binding.cameraButton.setOnClickListener {
            val hasCameraPerm = ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (hasCameraPerm) {
                val imgUri = createImageFileUri()
                if (imgUri != null) {
                    tempImageUri = imgUri
                    takePicture.launch(imgUri)
                } else {
                    pickImage.launch("image/*")
                }
            } else {
                requestCameraPermission.launch(android.Manifest.permission.CAMERA)
            }
        }

        binding.galleryButton.setOnClickListener {
            pickImage.launch("image/*")
        }

        binding.saveButton.setOnClickListener {
            val newUsername = binding.usernameEditText.text?.toString().orEmpty()
            val newDisplay = binding.displayNameEditText.text?.toString().orEmpty()

            if (newDisplay.isBlank()) {
                binding.displayNameEditText.error = getString(R.string.edit_profile_display_name_required)
                return@setOnClickListener
            }

            if (newUsername.isNotBlank() && !usernamePattern.matches(newUsername)) {
                binding.usernameEditText.error = getString(R.string.edit_profile_username_invalid)
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val unique = if (newUsername.isBlank()) true else viewModel.isUsernameUniqueLocal(newUsername, viewModel.currentUserId())
                if (!unique) {
                    if (_binding != null) binding.usernameEditText.error = getString(R.string.edit_profile_username_taken)
                    return@launch
                }

                viewModel.setUsername(newUsername)
                viewModel.setDisplayName(newDisplay)
                viewModel.saveProfile(tempImageUri)
            }
        }

        binding.deleteButton.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.edit_profile_delete_title)
                .setMessage(R.string.edit_profile_delete_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_profile_label) { _, _ ->
                    viewModel.deleteProfile()
                }
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
