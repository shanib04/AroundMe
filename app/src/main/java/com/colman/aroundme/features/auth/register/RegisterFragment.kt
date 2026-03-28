package com.colman.aroundme.features.auth.register

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.colman.aroundme.R
import com.colman.aroundme.databinding.FragmentRegisterBinding
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

// Register screen for email/password and Google sign-up
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null

    private val viewModel: RegisterViewModel by viewModels {
        RegisterViewModel.Factory(requireActivity().application)
    }

    private lateinit var credentialManager: CredentialManager
    private lateinit var googleSignUpRequest: GetCredentialRequest
    private var selectedImageUri: Uri? = null

    private val getContentLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            viewModel.clearFieldErrors()
            loadImage(uri)
        }
    }

    private val takePicturePreviewLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val uri = saveBitmapToCache(bitmap)
            selectedImageUri = uri
            viewModel.clearFieldErrors()
            loadImage(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureGoogleSignIn()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return requireNotNull(_binding).root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUi()
        observeViewModel()
    }

    private fun setupUi() {
        val binding = _binding ?: return
        with(binding) {
            profileImageView.setOnClickListener { showImagePickerOptions() }
            selectImageButton.setOnClickListener { showImagePickerOptions() }

            signUpButton.setOnClickListener {
                viewModel.register(
                    displayName = displayNameEditText.text?.toString().orEmpty(),
                    username = usernameEditText.text?.toString().orEmpty(),
                    email = emailEditText.text?.toString().orEmpty(),
                    password = passwordEditText.text?.toString().orEmpty(),
                    confirmPassword = confirmPasswordEditText.text?.toString().orEmpty(),
                    imageUri = selectedImageUri
                )
            }

            signInText.setOnClickListener {
                findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
            }

            googleButton.setOnClickListener {
                launchGoogleSignUp()
            }
        }
    }

    private fun observeViewModel() {
        val binding = _binding ?: return
        viewModel.formState.observe(viewLifecycleOwner) { state ->
            binding.displayNameLayout.error = state.displayNameError
            binding.usernameLayout.error = state.usernameError
            binding.emailLayout.error = state.emailError
            binding.passwordLayout.error = state.passwordError
            binding.confirmPasswordLayout.error = state.confirmPasswordError
        }

        viewModel.registerState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is RegisterUiState.Idle -> renderLoading(false)
                is RegisterUiState.Loading -> renderLoading(true)
                is RegisterUiState.Success -> {
                    renderLoading(false)
                    navigateToHome()
                    viewModel.resetState()
                }
                is RegisterUiState.Error -> {
                    renderLoading(false)
                    showMessage(state.message)
                    viewModel.resetState()
                }
            }
        }
    }

    private fun navigateToHome() {
        if (!isAdded || view == null) return
        val navController = findNavController()
        if (navController.currentDestination?.id !in setOf(R.id.registerFragment, R.id.loginFragment)) return

        navController.navigate(
            R.id.feedFragment,
            null,
            NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(R.id.loginFragment, true)
                .build()
        )
    }

    private fun renderLoading(isLoading: Boolean) {
        val binding = _binding ?: return
        with(binding) {
            progressBar.isVisible = isLoading
            signUpButton.isEnabled = !isLoading
            googleButton.isEnabled = !isLoading
            selectImageButton.isEnabled = !isLoading
            profileImageView.isEnabled = !isLoading
            displayNameEditText.isEnabled = !isLoading
            usernameEditText.isEnabled = !isLoading
            emailEditText.isEnabled = !isLoading
            passwordEditText.isEnabled = !isLoading
            confirmPasswordEditText.isEnabled = !isLoading
            signInText.isEnabled = !isLoading
        }
    }

    private fun showImagePickerOptions() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.choose_profile_picture)
            .setItems(
                arrayOf(
                    getString(R.string.select_profile_picture),
                    getString(R.string.capture_profile_picture)
                )
            ) { _, which ->
                when (which) {
                    0 -> getContentLauncher.launch("image/*")
                    1 -> takePicturePreviewLauncher.launch(null)
                }
            }
            .show()
    }

    private fun configureGoogleSignIn() {
        val webClientId = getString(R.string.default_web_client_id)
        credentialManager = CredentialManager.create(requireContext())
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
            .build()
        googleSignUpRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()
    }

    private fun launchGoogleSignUp() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = credentialManager.getCredential(requireActivity(), googleSignUpRequest)
                handleGoogleCredential(response.credential)
            } catch (_: GetCredentialCancellationException) {
                viewModel.resetState()
                showMessage(getString(R.string.google_sign_up_cancelled))
            } catch (ex: GetCredentialException) {
                viewModel.resetState()
                showMessage(ex.localizedMessage ?: getString(R.string.google_sign_up_failed))
            } catch (t: Throwable) {
                viewModel.resetState()
                showMessage(t.localizedMessage ?: getString(R.string.google_sign_up_failed))
            }
        }
    }

    private fun handleGoogleCredential(credential: Credential) {
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            showMessage(getString(R.string.google_sign_up_failed))
            return
        }

        try {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken
            if (idToken.isNullOrBlank()) {
                showMessage(getString(R.string.google_sign_in_token_missing))
                return
            }
            viewModel.registerWithGoogle(idToken)
        } catch (_: GoogleIdTokenParsingException) {
            showMessage(getString(R.string.google_sign_up_failed))
        }
    }

    private fun loadImage(uri: Uri) {
        val binding = _binding ?: return
        Glide.with(this)
            .load(uri)
            .circleCrop()
            .placeholder(R.drawable.ic_person_placeholder)
            .error(R.drawable.ic_person_placeholder)
            .into(binding.profileImageView)
    }

    private fun saveBitmapToCache(bitmap: Bitmap): Uri {
        val file = File(requireContext().cacheDir, "captured_profile_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            outputStream.flush()
        }
        return Uri.fromFile(file)
    }

    private fun showMessage(message: String) {
        val binding = _binding ?: return
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
