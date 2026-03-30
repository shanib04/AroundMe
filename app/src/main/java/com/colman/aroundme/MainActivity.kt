package com.colman.aroundme

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.colman.aroundme.data.repository.UserSessionManager
import com.colman.aroundme.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        UserSessionManager.getInstance(applicationContext).start()

        setupEdgeToEdge()
        setupNavigation()
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            val currentDestinationId = navHostFragment.navController.currentDestination?.id

            val authDestinations = setOf(R.id.loginFragment, R.id.registerFragment)

            if (currentDestinationId in authDestinations) {
                binding.navHostFragment.updatePadding(top = 0, bottom = 0)
                binding.bottomNavigationView.updatePadding(bottom = 0)
            } else {
                binding.navHostFragment.updatePadding(top = systemBars.top, bottom = 80)
                binding.bottomNavigationView.updatePadding(bottom = systemBars.bottom)
            }

            insets
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Let NavigationUI handle normal selection for tabs
        binding.bottomNavigationView.setupWithNavController(navController)

        binding.bottomNavigationView.setOnItemReselectedListener {}

        binding.fabAdd.setOnClickListener {
            navController.navigate(R.id.createEventFragment)
        }

        val authDestinations = setOf(R.id.loginFragment, R.id.registerFragment)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            ViewCompat.requestApplyInsets(binding.root)

            if (destination.id in authDestinations) {
                binding.bottomNavigationView.visibility = View.GONE
                binding.fabAdd.visibility = View.GONE
            } else {
                binding.bottomNavigationView.visibility = View.VISIBLE
                binding.fabAdd.visibility = View.VISIBLE

                val menu = binding.bottomNavigationView.menu

                when (destination.id) {
                    R.id.feedFragment -> {
                        // Ensure feed is visually selected when on feed
                        if (binding.bottomNavigationView.selectedItemId != R.id.feedFragment) {
                            binding.bottomNavigationView.selectedItemId = R.id.feedFragment
                        }
                    }
                    R.id.createEventFragment -> {
                        for (i in 0 until menu.size()) {
                            menu.getItem(i).isChecked = false
                        }
                    }
                }
            }
        }
    }
}
