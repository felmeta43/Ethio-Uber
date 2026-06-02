package com.ethiouber.customer.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.ethiouber.customer.R
import com.ethiouber.customer.databinding.FragmentRegisterBinding
import com.ethiouber.customer.utils.hide
import com.ethiouber.customer.utils.show
import com.ethiouber.customer.utils.toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRegister.setOnClickListener {
            val name = binding.etFullName.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirm = binding.etConfirmPassword.text.toString()

            when {
                name.isEmpty() || phone.isEmpty() || password.isEmpty() ->
                    toast("Please fill all fields")
                password != confirm ->
                    toast("Passwords do not match")
                password.length < 8 ->
                    toast("Password must be at least 8 characters")
                else -> viewModel.register(name, phone, null, password, confirm)
            }
        }

        binding.tvLogin.setOnClickListener { findNavController().navigateUp() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is AuthState.Loading -> {
                        binding.progressBar.show()
                        binding.btnRegister.isEnabled = false
                    }
                    is AuthState.Registered, is AuthState.OtpSent -> {
                        binding.progressBar.hide()
                        binding.btnRegister.isEnabled = true
                        if (state is AuthState.OtpSent) {
                            findNavController().navigate(R.id.action_registerFragment_to_otpFragment)
                        } else {
                            // After registration, auto-send OTP
                            viewModel.sendOtp(viewModel.pendingPhone)
                        }
                        viewModel.resetState()
                    }
                    is AuthState.Error -> {
                        binding.progressBar.hide()
                        binding.btnRegister.isEnabled = true
                        toast(state.message)
                        viewModel.resetState()
                    }
                    else -> {
                        binding.progressBar.hide()
                        binding.btnRegister.isEnabled = true
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
