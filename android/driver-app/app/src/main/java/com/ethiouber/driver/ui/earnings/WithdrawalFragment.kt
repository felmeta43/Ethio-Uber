package com.ethiouber.driver.ui.earnings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.ethiouber.driver.R
import com.ethiouber.driver.databinding.FragmentWithdrawalBinding
import com.ethiouber.driver.utils.Extensions.hide
import com.ethiouber.driver.utils.Extensions.show
import com.ethiouber.driver.utils.Extensions.toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WithdrawalFragment : Fragment() {

    private var _binding: FragmentWithdrawalBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EarningsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWithdrawalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSpinner()
        setupListeners()
        observeWithdrawalState()
    }

    private fun setupSpinner() {
        val methods = resources.getStringArray(R.array.withdrawal_methods)
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            methods
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMethod.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnSubmit.setOnClickListener {
            val amountStr = binding.etAmount.text?.toString()?.trim() ?: ""
            val accountNumber = binding.etAccount.text?.toString()?.trim() ?: ""
            val selectedMethod = binding.spinnerMethod.selectedItem?.toString() ?: ""

            when {
                amountStr.isEmpty() -> toast(getString(R.string.amount_error))
                amountStr.toDoubleOrNull() == null || amountStr.toDouble() <= 0 ->
                    toast(getString(R.string.amount_error))
                accountNumber.isEmpty() -> toast(getString(R.string.account_number_error))
                else -> {
                    val amount = amountStr.toDouble()
                    viewModel.requestWithdrawal(amount, selectedMethod, accountNumber)
                }
            }
        }
    }

    private fun observeWithdrawalState() {
        lifecycleScope.launch {
            viewModel.withdrawalState.collect { state ->
                when (state) {
                    is WithdrawalUiState.Loading -> {
                        binding.progressBar.show()
                        binding.btnSubmit.isEnabled = false
                    }
                    is WithdrawalUiState.Success -> {
                        binding.progressBar.hide()
                        binding.btnSubmit.isEnabled = true
                        toast(getString(R.string.withdrawal_success))
                        viewModel.resetWithdrawalState()
                        findNavController().navigateUp()
                    }
                    is WithdrawalUiState.Error -> {
                        binding.progressBar.hide()
                        binding.btnSubmit.isEnabled = true
                        toast(state.message)
                        viewModel.resetWithdrawalState()
                    }
                    is WithdrawalUiState.Idle -> {
                        binding.progressBar.hide()
                        binding.btnSubmit.isEnabled = true
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
