package com.ethiouber.customer.ui.profile

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.ethiouber.customer.R
import com.ethiouber.customer.databinding.FragmentProfileBinding
import com.ethiouber.customer.utils.hide
import com.ethiouber.customer.utils.show
import com.ethiouber.customer.utils.toast
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

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.btnAddresses.setOnClickListener {
            // Saved Addresses screen (future implementation)
            toast(getString(R.string.coming_soon))
        }

        binding.btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.logout))
                .setMessage(getString(R.string.logout_confirm_message))
                .setPositiveButton(getString(R.string.logout)) { _, _ ->
                    viewModel.logout()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is ProfileUiState.Loading -> {
                        binding.btnLogout.isEnabled = false
                    }
                    is ProfileUiState.ProfileLoaded -> {
                        binding.btnLogout.isEnabled = true
                        binding.tvName.text = state.user.fullName
                        binding.tvPhone.text = state.user.phoneNumber
                    }
                    is ProfileUiState.LoggedOut -> {
                        // Navigate to login, clearing entire back stack
                        findNavController().navigate(
                            R.id.action_profileFragment_to_loginFragment
                        )
                    }
                    is ProfileUiState.PasswordChanged -> {
                        binding.btnLogout.isEnabled = true
                        toast(state.message)
                        viewModel.resetState()
                    }
                    is ProfileUiState.Error -> {
                        binding.btnLogout.isEnabled = true
                        toast(state.message)
                        viewModel.resetState()
                    }
                    else -> {
                        binding.btnLogout.isEnabled = true
                    }
                }
            }
        }
    }

    private fun showChangePasswordDialog() {
        val currentPasswordInput = EditText(requireContext()).apply {
            hint = getString(R.string.current_password)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val newPasswordInput = EditText(requireContext()).apply {
            hint = getString(R.string.new_password)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val confirmPasswordInput = EditText(requireContext()).apply {
            hint = getString(R.string.confirm_new_password)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = resources.getDimensionPixelSize(R.dimen.dialog_padding)
            setPadding(padding, padding / 2, padding, 0)
            addView(currentPasswordInput)
            addView(newPasswordInput)
            addView(confirmPasswordInput)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.change_password))
            .setView(container)
            .setPositiveButton(getString(R.string.change)) { _, _ ->
                viewModel.changePassword(
                    currentPasswordInput.text.toString(),
                    newPasswordInput.text.toString(),
                    confirmPasswordInput.text.toString()
                )
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
