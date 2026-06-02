package com.ethiouber.driver.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.ethiouber.driver.R
import com.ethiouber.driver.databinding.FragmentLoginBinding
import com.ethiouber.driver.utils.Extensions.hide
import com.ethiouber.driver.utils.Extensions.show
import com.ethiouber.driver.utils.Extensions.toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeLoginState()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val phone = binding.etPhone.text.toString().trim()
            val password = binding.etPassword.text.toString()

            when {
                phone.isEmpty() -> binding.tilPhone.error = "Phone number is required"
                phone.length < 9 -> binding.tilPhone.error = "Enter a valid phone number"
                password.isEmpty() -> binding.tilPassword.error = "Password is required"
                password.length < 6 -> binding.tilPassword.error = "Password must be at least 6 characters"
                else -> {
                    binding.tilPhone.error = null
                    binding.tilPassword.error = null
                    viewModel.login(phone, password)
                }
            }
        }

        binding.tvRegister.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }
    }

    private fun observeLoginState() {
        lifecycleScope.launch {
            viewModel.loginState.collect { state ->
                when (state) {
                    is AuthUiState.Loading -> {
                        binding.progressBar.show()
                        binding.btnLogin.isEnabled = false
                    }
                    is AuthUiState.Success -> {
                        binding.progressBar.hide()
                        binding.btnLogin.isEnabled = true
                        viewModel.pendingPhone = binding.etPhone.text.toString().trim()
                        findNavController().navigate(R.id.action_loginFragment_to_otpFragment)
                        viewModel.resetLoginState()
                    }
                    is AuthUiState.Error -> {
                        binding.progressBar.hide()
                        binding.btnLogin.isEnabled = true
                        toast(state.message)
                        viewModel.resetLoginState()
                    }
                    is AuthUiState.Idle -> {
                        binding.progressBar.hide()
                        binding.btnLogin.isEnabled = true
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
