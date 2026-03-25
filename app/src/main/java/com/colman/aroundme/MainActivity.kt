package com.colman.aroundme

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.colman.aroundme.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
                // Remove all padding for auth screens to allow edge-to-edge backgrounds
                binding.navHostFragment.updatePadding(top = 0, bottom = 0)
                binding.bottomNavigationView.updatePadding(bottom = 0)
            } else {
                binding.navHostFragment.updatePadding(top = systemBars.top, bottom = 80) // Adjust bottom for nav bar
                binding.bottomNavigationView.updatePadding(bottom = systemBars.bottom)
            }
            
            insets
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavigationView.setupWithNavController(navController)

        binding.fabAdd.setOnClickListener {
            navController.navigate(R.id.createEventFragment)
        }

        val authDestinations = setOf(R.id.loginFragment, R.id.registerFragment)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Re-trigger inset application when destination changes
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
                        // Explicitly select the feed tab when on the feed
                        if (binding.bottomNavigationView.selectedItemId != R.id.feedFragment) {
                            binding.bottomNavigationView.selectedItemId = R.id.feedFragment
                        }
                    }
                    R.id.mapFragment -> {
                        if (binding.bottomNavigationView.selectedItemId != R.id.mapFragment) {
                            binding.bottomNavigationView.selectedItemId = R.id.mapFragment
                        }
                    }
                    R.id.profileFragment -> {
                        if (binding.bottomNavigationView.selectedItemId != R.id.profileFragment) {
                            binding.bottomNavigationView.selectedItemId = R.id.profileFragment
                        }
                    }
                    R.id.createEventFragment -> {
                        // For the FAB-only screen, clear selection so no tab appears active
                        for (i in 0 until menu.size()) {
                            menu.getItem(i).isChecked = false
                        }
                    }
                }
            }
        }
    }
}
