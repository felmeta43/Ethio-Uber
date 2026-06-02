package com.ethiouber.customer.ui.auth

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.ethiouber.customer.R
import com.ethiouber.customer.databinding.FragmentOtpBinding
import com.ethiouber.customer.utils.hide
import com.ethiouber.customer.utils.show
import com.ethiouber.customer.utils.toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OtpFragment : Fragment() {

    private var _binding: FragmentOtpBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()
    private var countDownTimer: CountDownTimer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOtpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvPhone.text = "Code sent to ${viewModel.pendingPhone}"
        startResendTimer()

        binding.btnVerify.setOnClickListener {
            val otp = binding.etOtp.text.toString().trim()
            if (otp.length < 6) { toast("Enter the 6-digit code"); return@setOnClickListener }
            viewModel.verifyOtp(otp)
        }

        binding.tvResend.setOnClickListener {
            viewModel.sendOtp(viewModel.pendingPhone)
            startResendTimer()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is AuthState.Loading -> { binding.progressBar.show(); binding.btnVerify.isEnabled = false }
                    is AuthState.Success -> {
                        binding.progressBar.hide()
                        toast("Phone verified!")
                        findNavController().navigate(R.id.action_otpFragment_to_homeFragment)
                        viewModel.resetState()
                    }
                    is AuthState.Error -> {
                        binding.progressBar.hide()
                        binding.btnVerify.isEnabled = true
                        toast(state.message)
                        viewModel.resetState()
                    }
                    else -> { binding.progressBar.hide(); binding.btnVerify.isEnabled = true }
                }
            }
        }
    }

    private fun startResendTimer() {
        binding.tvResend.isEnabled = false
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(60_000L, 1_000L) {
            override fun onTick(ms: Long) {
                binding.tvResend.text = "Resend in ${ms / 1000}s"
            }
            override fun onFinish() {
                binding.tvResend.text = "Resend OTP"
                binding.tvResend.isEnabled = true
            }
        }.start()
    }

    override fun onDestroyView() {
        countDownTimer?.cancel()
        super.onDestroyView()
        _binding = null
    }
}
