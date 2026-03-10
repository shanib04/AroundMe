package com.colman.aroundme

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.colman.aroundme.auth.AuthResultState
import com.colman.aroundme.auth.LoginViewModel
import com.colman.aroundme.databinding.FragmentLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * Login screen for email/password and Google authentication.
 */
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels {
        LoginViewModel.Factory()
    }

    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            viewModel.resetState()
            showMessage(getString(R.string.google_sign_in_cancelled))
            return@registerForActivityResult
        }

        handleGoogleSignInResult(result.data)
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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUi()
        observeViewModel()
    }

    private fun setupUi() = with(binding) {
        loginButton.setOnClickListener {
            val email = emailEditText.text?.toString().orEmpty()
            val password = passwordEditText.text?.toString().orEmpty()

            if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                emailLayout.error = getString(R.string.invalid_email)
                return@setOnClickListener
            }

            emailLayout.error = null
            passwordLayout.error = null
            viewModel.loginWithEmailAndPassword(email, password)
        }

        signUpText.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        googleButton.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
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

    private fun renderLoading(isLoading: Boolean) = with(binding) {
        progressBar.isVisible = isLoading
        loginButton.isEnabled = !isLoading
        googleButton.isEnabled = !isLoading
        emailEditText.isEnabled = !isLoading
        passwordEditText.isEnabled = !isLoading
        signUpText.isEnabled = !isLoading
    }

    private fun configureGoogleSignIn() {
        val webClientId = getString(R.string.default_web_client_id)
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireContext(), options)
    }

    private fun handleGoogleSignInResult(data: Intent?) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                showMessage(getString(R.string.google_sign_in_token_missing))
                return
            }
            viewModel.loginWithGoogle(idToken)
        } catch (exception: ApiException) {
            showMessage(exception.localizedMessage ?: getString(R.string.google_sign_in_failed))
        } catch (exception: FirebaseFirestoreException) {
            showMessage(getString(R.string.google_firestore_permission_denied))
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
