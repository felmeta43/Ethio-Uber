package com.ethiouber.driver.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.ethiouber.driver.R
import com.ethiouber.driver.databinding.FragmentProfileBinding
import com.ethiouber.driver.utils.Extensions.toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeUiState()
        observeOnlineStatus()
        viewModel.refreshProfileFromNetwork()
    }

    private fun setupListeners() {
        binding.btnLogout.setOnClickListener {
            viewModel.logout()
        }

        binding.switchOnline.setOnCheckedChangeListener { _, _ ->
            viewModel.toggleOnlineStatus()
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is ProfileUiState.Loaded -> populateProfile(state.profile)
                    is ProfileUiState.Error -> toast(state.message)
                    is ProfileUiState.LoggedOut -> navigateToLogin()
                    is ProfileUiState.Loading,
                    is ProfileUiState.Idle -> { /* handled by network calls */ }
                }
            }
        }
    }

    private fun observeOnlineStatus() {
        lifecycleScope.launch {
            viewModel.isOnline.collect { online ->
                // Temporarily remove the listener to avoid triggering toggle again
                binding.switchOnline.setOnCheckedChangeListener(null)
                binding.switchOnline.isChecked = online
                binding.switchOnline.setOnCheckedChangeListener { _, _ ->
                    viewModel.toggleOnlineStatus()
                }
            }
        }
    }

    private fun populateProfile(profile: ProfileData) {
        binding.tvName.text = profile.fullName
        binding.tvPhone.text = profile.phone
        binding.tvRating.text = getString(R.string.rating_format, profile.rating)

        val vehicleText = buildString {
            val type = profile.vehicleType ?: ""
            val plate = profile.vehiclePlate ?: ""
            val model = profile.vehicleModel ?: ""
            if (type.isNotEmpty() || model.isNotEmpty()) {
                append("$type $model".trim())
                if (plate.isNotEmpty()) append(" — $plate")
            } else {
                append("—")
            }
        }
        binding.tvVehicle.text = vehicleText
    }

    private fun navigateToLogin() {
        findNavController().navigate(
            R.id.loginFragment,
            null,
            androidx.navigation.NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, true)
                .build()
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
