package com.colman.aroundme.features.auth.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.colman.aroundme.R
import com.colman.aroundme.databinding.FragmentLoginBinding
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = requireNotNull(_binding) { "FragmentLoginBinding accessed outside of onCreateView/onDestroyView" }

    private val viewModel: LoginViewModel by viewModels {
        LoginViewModel.Factory()
    }

    private lateinit var credentialManager: CredentialManager
    private lateinit var googleSignInRequest: GetCredentialRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureGoogleSignIn()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
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
            loginButton.setOnClickListener {
                val identifier = emailEditText.text?.toString().orEmpty()
                val password = passwordEditText.text?.toString().orEmpty()

                emailLayout.error = null
                passwordLayout.error = null

                viewModel.loginWithIdentifierAndPassword(identifier, password)
            }

            signUpText.setOnClickListener {
                findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
            }

            googleButton.setOnClickListener {
                launchGoogleSignIn()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.loginState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AuthResultState.Idle -> renderLoading(false)
                is AuthResultState.Loading -> renderLoading(true)
                is AuthResultState.Success -> {
                    renderLoading(false)
                    navigateToHome()
                }
                is AuthResultState.Error -> {
                    renderLoading(false)
                    showMessage(state.message)
                }
            }
        }
    }

    private fun navigateToHome() {
        if (!isAdded || view == null) return
        val navController = findNavController()
        if (navController.currentDestination?.id !in setOf(R.id.loginFragment, R.id.registerFragment)) return

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
            loginButton.isEnabled = !isLoading
            googleButton.isEnabled = !isLoading
            emailEditText.isEnabled = !isLoading
            passwordEditText.isEnabled = !isLoading
            signUpText.isEnabled = !isLoading
        }
    }

    private fun configureGoogleSignIn() {
        val webClientId = getString(R.string.default_web_client_id)
        credentialManager = CredentialManager.create(requireContext())
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
            .build()
        googleSignInRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()
    }

    private fun launchGoogleSignIn() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = credentialManager.getCredential(requireActivity(), googleSignInRequest)
                handleGoogleCredential(response.credential)
            } catch (_: GetCredentialCancellationException) {
                viewModel.resetState()
                showSigninFailedDialog(getString(R.string.google_sign_in_cancelled))
            } catch (ex: GetCredentialException) {
                viewModel.resetState()
                showSigninFailedDialog(ex.localizedMessage ?: getString(R.string.google_sign_in_failed))
            } catch (t: Throwable) {
                viewModel.resetState()
                showSigninFailedDialog(t.localizedMessage ?: getString(R.string.google_sign_in_failed))
            }
        }
    }

    private fun handleGoogleCredential(credential: Credential) {
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            showMessage(getString(R.string.google_sign_in_failed))
            return
        }

        try {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken
            if (idToken.isNullOrBlank()) {
                showMessage(getString(R.string.google_sign_in_token_missing))
                return
            }
            viewModel.loginWithGoogle(idToken)
        } catch (_: GoogleIdTokenParsingException) {
            showMessage(getString(R.string.google_sign_in_failed))
        }
    }

    private fun showSigninFailedDialog(message: String) {
        val binding = _binding ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.google_sign_in_failed))
            .setMessage(message)
            .setCancelable(false)
            .setNegativeButton("Use email/password") { dialog, _ ->
                dialog.dismiss()
                val identifier = binding.emailEditText.text?.toString().orEmpty()
                val password = binding.passwordEditText.text?.toString().orEmpty()
                viewModel.loginWithIdentifierAndPassword(identifier, password)
            }
            .setPositiveButton("Retry") { dialog, _ ->
                dialog.dismiss()
                launchGoogleSignIn()
            }
            .show()
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
