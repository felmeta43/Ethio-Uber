package com.ethiouber.customer.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethiouber.customer.data.model.PaymentInitData
import com.ethiouber.customer.data.model.TransactionData
import com.ethiouber.customer.data.model.WalletData
import com.ethiouber.customer.data.repository.PaymentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PaymentUiState {
    object Idle : PaymentUiState()
    object Loading : PaymentUiState()
    data class WalletLoaded(val wallet: WalletData) : PaymentUiState()
    data class TransactionsLoaded(val transactions: List<TransactionData>) : PaymentUiState()
    data class TopUpInitiated(val paymentData: PaymentInitData) : PaymentUiState()
    data class PaymentProcessed(val paymentData: PaymentInitData) : PaymentUiState()
    data class Success(val message: String = "") : PaymentUiState()
    data class Error(val message: String) : PaymentUiState()
}

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository
) : ViewModel() {

    private val _state = MutableStateFlow<PaymentUiState>(PaymentUiState.Idle)
    val state: StateFlow<PaymentUiState> = _state

    private val _walletBalance = MutableStateFlow<Double?>(null)
    val walletBalance: StateFlow<Double?> = _walletBalance

    private val _transactions = MutableStateFlow<List<TransactionData>>(emptyList())
    val transactions: StateFlow<List<TransactionData>> = _transactions

    fun getWalletBalance() {
        viewModelScope.launch {
            _state.value = PaymentUiState.Loading
            paymentRepository.getWallet()
                .onSuccess { wallet ->
                    _walletBalance.value = wallet.balance
                    _state.value = PaymentUiState.WalletLoaded(wallet)
                }
                .onFailure { error ->
                    _state.value = PaymentUiState.Error(error.message ?: "Failed to load wallet")
                }
        }
    }

    fun getTransactions(page: Int = 1) {
        viewModelScope.launch {
            _state.value = PaymentUiState.Loading
            paymentRepository.getTransactions(page)
                .onSuccess { transactions ->
                    _transactions.value = transactions
                    _state.value = PaymentUiState.TransactionsLoaded(transactions)
                }
                .onFailure { error ->
                    _state.value = PaymentUiState.Error(
                        error.message ?: "Failed to load transactions"
                    )
                }
        }
    }

    /**
     * Initiates a wallet top-up via the specified payment method.
     * @param amount Amount in ETB to top up
     * @param paymentMethod e.g. "TELEBIRR", "CBE_BIRR"
     */
    fun topUpWallet(amount: Double, paymentMethod: String = "TELEBIRR") {
        if (amount <= 0) {
            _state.value = PaymentUiState.Error("Please enter a valid amount")
            return
        }
        viewModelScope.launch {
            _state.value = PaymentUiState.Loading
            paymentRepository.topUpWallet(amount, paymentMethod)
                .onSuccess { paymentData ->
                    _state.value = PaymentUiState.TopUpInitiated(paymentData)
                }
                .onFailure { error ->
                    _state.value = PaymentUiState.Error(error.message ?: "Top-up failed")
                }
        }
    }

    /**
     * Initiates payment for an order.
     * @param orderId ID of the order to pay for
     * @param paymentMethod Payment method to use
     */
    fun processPayment(orderId: Int, paymentMethod: String) {
        viewModelScope.launch {
            _state.value = PaymentUiState.Loading
            paymentRepository.initiatePayment(orderId, paymentMethod)
                .onSuccess { paymentData ->
                    _state.value = PaymentUiState.PaymentProcessed(paymentData)
                }
                .onFailure { error ->
                    _state.value = PaymentUiState.Error(error.message ?: "Payment failed")
                }
        }
    }

    fun resetState() {
        _state.value = PaymentUiState.Idle
    }
}
