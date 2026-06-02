package com.ethiouber.driver.ui.auth

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.ethiouber.driver.R
import com.ethiouber.driver.databinding.FragmentOtpBinding
import com.ethiouber.driver.utils.Extensions.hide
import com.ethiouber.driver.utils.Extensions.show
import com.ethiouber.driver.utils.Extensions.toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OtpFragment : Fragment() {

    private var _binding: FragmentOtpBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by activityViewModels()
    private var countDownTimer: CountDownTimer? = null
    private var canResend = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOtpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val phone = viewModel.pendingPhone
        binding.tvPhoneHint.text = "Enter the 6-digit code sent to $phone"

        startResendTimer()
        setupListeners()
        observeOtpState()
        observeResendState()
    }

    private fun setupListeners() {
        binding.btnVerify.setOnClickListener {
            val otp = binding.etOtp.text.toString().trim()
            when {
                otp.isEmpty() -> binding.tilOtp.error = "OTP is required"
                otp.length < 6 -> binding.tilOtp.error = "Enter a 6-digit OTP"
                else -> {
                    binding.tilOtp.error = null
                    viewModel.verifyOtp(viewModel.pendingPhone, otp)
                }
            }
        }

        binding.tvResendOtp.setOnClickListener {
            if (canResend) {
                viewModel.resendOtp(viewModel.pendingPhone)
                canResend = false
                startResendTimer()
            }
        }
    }

    private fun startResendTimer() {
        binding.tvResendOtp.isEnabled = false
        binding.tvResendOtp.alpha = 0.5f
        countDownTimer?.cancel()

        countDownTimer = object : CountDownTimer(60_000L, 1000L) {
            override fun onTick(millisRemaining: Long) {
                val seconds = millisRemaining / 1000
                binding.tvResendOtp.text = "Resend OTP in ${seconds}s"
            }

            override fun onFinish() {
                canResend = true
                binding.tvResendOtp.text = "Resend OTP"
                binding.tvResendOtp.isEnabled = true
                binding.tvResendOtp.alpha = 1f
            }
        }.start()
    }

    private fun observeOtpState() {
        lifecycleScope.launch {
            viewModel.otpState.collect { state ->
                when (state) {
                    is AuthUiState.Loading -> {
                        binding.progressBar.show()
                        binding.btnVerify.isEnabled = false
                    }
                    is AuthUiState.Success -> {
                        binding.progressBar.hide()
                        binding.btnVerify.isEnabled = true
                        countDownTimer?.cancel()
                        findNavController().navigate(R.id.action_otpFragment_to_homeFragment)
                        viewModel.resetOtpState()
                    }
                    is AuthUiState.Error -> {
                        binding.progressBar.hide()
                        binding.btnVerify.isEnabled = true
                        toast(state.message)
                        viewModel.resetOtpState()
                    }
                    is AuthUiState.Idle -> {
                        binding.progressBar.hide()
                        binding.btnVerify.isEnabled = true
                    }
                }
            }
        }
    }

    private fun observeResendState() {
        lifecycleScope.launch {
            viewModel.resendOtpState.collect { state ->
                when (state) {
                    is AuthUiState.Success -> {
                        toast("OTP resent successfully")
                    }
                    is AuthUiState.Error -> {
                        toast(state.message)
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        _binding = null
    }
}
