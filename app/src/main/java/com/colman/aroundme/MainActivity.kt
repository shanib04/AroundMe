package com.colman.aroundme

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)

        val authDestinations = setOf(R.id.loginFragment, R.id.registerFragment)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            bottomNav.isVisible = destination.id !in authDestinations
        }
        bottomNav.setOnItemReselectedListener { item ->
            Log.d(TAG, "bottomNav reselected itemId=${item.itemId}")
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            Log.d(TAG, "destinationId=${destination.id}")
        }
    }

    companion object {
        private const val TAG = "AroundMeNav"
    }
}
