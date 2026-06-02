package com.ethiouber.driver.ui.earnings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethiouber.driver.data.model.TransactionData
import com.ethiouber.driver.data.model.WalletData
import com.ethiouber.driver.data.repository.EarningsRepository
import com.ethiouber.driver.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class EarningsUiState {
    object Idle : EarningsUiState()
    object Loading : EarningsUiState()
    data class Success(val wallet: WalletData, val transactions: List<TransactionData>) :
        EarningsUiState()
    data class Error(val message: String) : EarningsUiState()
}

sealed class WithdrawalUiState {
    object Idle : WithdrawalUiState()
    object Loading : WithdrawalUiState()
    object Success : WithdrawalUiState()
    data class Error(val message: String) : WithdrawalUiState()
}

@HiltViewModel
class EarningsViewModel @Inject constructor(
    private val earningsRepository: EarningsRepository
) : ViewModel() {

    private val _earningsState = MutableStateFlow<EarningsUiState>(EarningsUiState.Idle)
    val earningsState: StateFlow<EarningsUiState> = _earningsState.asStateFlow()

    private val _withdrawalState = MutableStateFlow<WithdrawalUiState>(WithdrawalUiState.Idle)
    val withdrawalState: StateFlow<WithdrawalUiState> = _withdrawalState.asStateFlow()

    init {
        loadEarnings()
    }

    fun loadEarnings() {
        viewModelScope.launch {
            _earningsState.value = EarningsUiState.Loading

            var wallet: WalletData? = null
            var transactions: List<TransactionData> = emptyList()
            var errorMessage: String? = null

            earningsRepository.getWallet().collect { result ->
                when (result) {
                    is Result.Success -> wallet = result.data
                    is Result.Error -> errorMessage = result.message
                    is Result.Loading -> {}
                }
            }

            if (wallet != null) {
                earningsRepository.getTransactions().collect { result ->
                    when (result) {
                        is Result.Success -> transactions = result.data
                        is Result.Error -> {} // non-critical; keep showing wallet
                        is Result.Loading -> {}
                    }
                }
                _earningsState.value = EarningsUiState.Success(wallet!!, transactions)
            } else {
                _earningsState.value =
                    EarningsUiState.Error(errorMessage ?: "Failed to load earnings")
            }
        }
    }

    fun requestWithdrawal(amount: Double, method: String, accountNumber: String) {
        viewModelScope.launch {
            _withdrawalState.value = WithdrawalUiState.Loading
            earningsRepository.requestWithdrawal(
                amount = amount,
                bankName = method,
                accountNumber = accountNumber,
                accountHolderName = ""
            ).collect { result ->
                _withdrawalState.value = when (result) {
                    is Result.Loading -> WithdrawalUiState.Loading
                    is Result.Success -> WithdrawalUiState.Success
                    is Result.Error -> WithdrawalUiState.Error(result.message)
                }
            }
        }
    }

    fun resetWithdrawalState() {
        _withdrawalState.value = WithdrawalUiState.Idle
    }
}
