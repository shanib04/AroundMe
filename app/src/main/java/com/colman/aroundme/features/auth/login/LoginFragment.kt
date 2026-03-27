package com.colman.aroundme.features.auth.login

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.colman.aroundme.R
import com.colman.aroundme.databinding.FragmentLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = requireNotNull(_binding) { "FragmentLoginBinding accessed outside of onCreateView/onDestroyView" }

    private val viewModel: LoginViewModel by viewModels {
        LoginViewModel.Factory()
    }

    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleIntentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK) {
            handleLegacyGoogleSignInResult(data)
            return@registerForActivityResult
        }

        // Try to extract a more helpful error if provided
        var detailedMessage: String? = null
        try {
            if (data != null) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                // force getResult to throw if there's an error
                task.getResult(ApiException::class.java)
            }
        } catch (ae: ApiException) {
            detailedMessage = ae.localizedMessage
        } catch (t: Throwable) {
            detailedMessage = t.localizedMessage
        }

        viewModel.resetState()
        if (!detailedMessage.isNullOrBlank()) {
            showSigninFailedDialog(detailedMessage)
        } else {
            showSigninFailedDialog(getString(R.string.google_sign_in_cancelled))
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
                // Launch the sign-in intent
                googleIntentLauncher.launch(googleSignInClient.signInIntent)
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

    // Configure Google SignIn
    private fun configureGoogleSignIn() {
        val webClientId = getString(R.string.default_web_client_id)
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireContext(), options)
    }

    // Handle GoogleSignIn intent result
    private fun handleLegacyGoogleSignInResult(data: Intent?) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken.isNullOrBlank()) {
                showMessage(getString(R.string.google_sign_in_token_missing))
                return
            }
            // success -> clear flag
            viewModel.loginWithGoogle(idToken)
        } catch (ex: ApiException) {
            // Developer console / SHA issues manifest as status code 10 (DEVELOPER_ERROR)
            if (ex.statusCode == 10) {
                showDeveloperConfigDialog(ex)
            } else {
                val msg = ex.localizedMessage ?: getString(R.string.google_sign_in_failed)
                showMessage(msg)
            }
        } catch (t: Throwable) {
            showMessage(t.localizedMessage ?: getString(R.string.google_sign_in_failed))
        }
    }

    private fun showDeveloperConfigDialog(ex: ApiException) {
        val title = getString(R.string.google_sign_in_failed)
        val msg = ex.localizedMessage ?: "Developer console is not set up correctly."
        val hint = "Please verify the OAuth client (web client) and SHA-1 keys in the Google Cloud Console for this project.\n\nIf you're testing locally, you can also use email/password login."
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage("$msg\n\n$hint")
            .setPositiveButton("Retry") { dialog, _ ->
                dialog.dismiss()
                googleIntentLauncher.launch(googleSignInClient.signInIntent)
            }
            .setNegativeButton("Use email/password") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
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
                googleIntentLauncher.launch(googleSignInClient.signInIntent)
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
