package com.ethiouber.driver.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.ethiouber.driver.R
import com.ethiouber.driver.data.local.SessionManager
import com.ethiouber.driver.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        lifecycleScope.launch {
            val isLoggedIn = sessionManager.isLoggedIn.first()
            val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
            navGraph.setStartDestination(
                if (isLoggedIn) R.id.homeFragment else R.id.loginFragment
            )
            navController.graph = navGraph

            binding.bottomNav.setupWithNavController(navController)

            navController.addOnDestinationChangedListener { _, destination, _ ->
                val showBottom = destination.id in setOf(
                    R.id.homeFragment,
                    R.id.orderHistoryFragment,
                    R.id.earningsFragment,
                    R.id.profileFragment
                )
                binding.bottomNav.visibility = if (showBottom) View.VISIBLE else View.GONE
            }
        }
    }
}
