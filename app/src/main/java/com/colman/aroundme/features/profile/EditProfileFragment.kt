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
import com.bumptech.glide.Glide
import com.colman.aroundme.databinding.FragmentEditProfileBinding
import com.colman.aroundme.features.profile.ProfileViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.core.widget.addTextChangedListener
import androidx.navigation.fragment.findNavController
import com.colman.aroundme.R

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
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())
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

        // Bio character counter
        binding.bioEditText.addTextChangedListener {
            val len = it?.length ?: 0
            binding.bioCharCount.text = "$len/120"
            if (len > 120) binding.bioEditText.error = "Bio too long"
        }

        // Username inline validation: regex while typing, remote uniqueness on focus lost
        val usernameRegex = "^[a-z0-9_-]{1,15}$".toRegex()
        binding.usernameEditText.addTextChangedListener {
            val txt = it?.toString().orEmpty()
            if (txt.isNotBlank() && !usernameRegex.matches(txt)) {
                binding.usernameEditText.error = "Lowercase letters, numbers, - or _ (max 15)"
            } else {
                binding.usernameEditText.error = null
            }
        }

        binding.usernameEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val candidate = binding.usernameEditText.text?.toString().orEmpty()
                if (candidate.isNotBlank() && usernameRegex.matches(candidate)) {
                    // perform remote uniqueness check
                    lifecycleScope.launch {
                        val currentId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                        val taken = viewModel.isUsernameTakenRemote(candidate, currentId)
                        if (taken) binding.usernameEditText.error = "Username already taken"
                    }
                }
            }
        }

        viewModel.name.observe(viewLifecycleOwner) { name -> binding.nameEditText.setText(name) }
        viewModel.email.observe(viewLifecycleOwner) { email -> binding.emailEditText.setText(email) }
        viewModel.username.observe(viewLifecycleOwner) { username -> binding.usernameEditText.setText(username) }
        viewModel.displayName.observe(viewLifecycleOwner) { dn -> binding.displayNameEditText.setText(dn) }
        viewModel.bio.observe(viewLifecycleOwner) { b -> binding.bioEditText.setText(b) }
        viewModel.imageUri.observe(viewLifecycleOwner) { uri ->
            if (uri != null) Glide.with(this).load(uri).circleCrop().into(binding.editProfileImageView)
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.saveProgress.visibility = if (loading) View.VISIBLE else View.GONE
            binding.saveButton.isEnabled = !loading
            binding.deleteButton.isEnabled = !loading
            binding.nameEditText.isEnabled = !loading
            binding.emailEditText.isEnabled = !loading
            binding.cameraFab.isEnabled = !loading
        }

        viewModel.uploadProgress.observe(viewLifecycleOwner) { p ->
            if (p > 0) {
                binding.uploadProgressBar.visibility = View.VISIBLE
                binding.uploadProgressBar.progress = p
            } else {
                binding.uploadProgressBar.visibility = View.GONE
            }
        }

        binding.cameraFab.setOnClickListener {
            // request camera permission and then launch camera capture
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

        binding.clearImageButton.setOnClickListener {
            tempImageUri = null
            binding.editProfileImageView.setImageResource(com.colman.aroundme.R.drawable.ic_person_placeholder)
        }

        binding.saveButton.setOnClickListener {
            val newName = binding.nameEditText.text?.toString().orEmpty()
            val newEmail = binding.emailEditText.text?.toString().orEmpty()
            val newUsername = binding.usernameEditText.text?.toString().orEmpty()
            val newDisplay = binding.displayNameEditText.text?.toString().orEmpty()
            val newBio = binding.bioEditText.text?.toString().orEmpty()

            if (newName.isBlank()) {
                binding.nameEditText.error = "Name required"
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                binding.emailEditText.error = "Enter valid email"
                return@setOnClickListener
            }

            // username rules: up to 15 chars; lowercase letters, numbers, hyphen, underscore
            val usernameRegex = "^[a-z0-9_-]{1,15}$".toRegex()
            if (newUsername.isNotBlank() && !usernameRegex.matches(newUsername)) {
                binding.usernameEditText.error = "Invalid username"
                return@setOnClickListener
            }

            if (newDisplay.length > 20) {
                binding.displayNameEditText.error = "Max 20 chars"
                return@setOnClickListener
            }

            if (newBio.length > 120) {
                binding.bioEditText.error = "Bio too long"
                return@setOnClickListener
            }

            // Check username uniqueness
            lifecycleScope.launch {
                val currentId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                val unique = if (newUsername.isBlank()) true else viewModel.isUsernameUniqueLocal(newUsername, currentId)
                if (!unique) {
                    binding.usernameEditText.error = "Username taken"
                    return@launch
                }

                // update ViewModel fields
                viewModel.setName(newName)
                viewModel.setEmail(newEmail)
                viewModel.setUsername(newUsername)
                viewModel.setDisplayName(newDisplay)
                viewModel.setBio(newBio)
                // save; ViewModel handles threading
                viewModel.saveProfile(currentId, tempImageUri) { ok, err ->
                    if (ok) Snackbar.make(binding.root, "Saved", Snackbar.LENGTH_SHORT).show()
                    else Snackbar.make(binding.root, "Save failed: $err", Snackbar.LENGTH_LONG).show()
                }
            }
        }

        binding.deleteButton.setOnClickListener {
            // confirm deletion
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete profile")
                .setMessage("This will permanently delete your profile and all your events. Are you sure?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ ->
                    val currentId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                    viewModel.deleteProfile(currentId) { ok, err ->
                        if (ok) {
                            // navigate to login
                            findNavController().navigate(R.id.loginFragment)
                        } else {
                            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("Error")
                                .setMessage(err ?: "Failed to delete profile")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                }
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
