package com.ethiouber.customer.ui.payment

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.ethiouber.customer.R
import com.ethiouber.customer.data.model.TransactionData
import com.ethiouber.customer.databinding.FragmentWalletBinding
import com.ethiouber.customer.utils.hide
import com.ethiouber.customer.utils.show
import com.ethiouber.customer.utils.toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@AndroidEntryPoint
class WalletFragment : Fragment() {

    private var _binding: FragmentWalletBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PaymentViewModel by viewModels()

    private lateinit var transactionAdapter: TransactionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWalletBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        binding.btnTopUp.setOnClickListener {
            showTopUpDialog()
        }

        observeViewModel()

        viewModel.getWalletBalance()
        viewModel.getTransactions()
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter()
        binding.rvTransactions.apply {
            adapter = transactionAdapter
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(
                DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
            )
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is PaymentUiState.Loading -> {
                        binding.progressBar.show()
                    }
                    is PaymentUiState.WalletLoaded -> {
                        binding.progressBar.hide()
                        val fmt = NumberFormat.getNumberInstance(Locale.US)
                        binding.tvBalance.text = getString(
                            R.string.wallet_balance_format,
                            fmt.format(state.wallet.balance)
                        )
                    }
                    is PaymentUiState.TransactionsLoaded -> {
                        binding.progressBar.hide()
                        transactionAdapter.submitList(state.transactions)
                    }
                    is PaymentUiState.TopUpInitiated -> {
                        binding.progressBar.hide()
                        // Open payment URL in browser or custom tab
                        val url = state.paymentData.paymentUrl
                            ?: state.paymentData.checkoutUrl
                        if (url != null) {
                            openUrl(url)
                        } else {
                            toast(getString(R.string.top_up_initiated))
                        }
                        viewModel.resetState()
                    }
                    is PaymentUiState.Error -> {
                        binding.progressBar.hide()
                        toast(state.message)
                        viewModel.resetState()
                    }
                    else -> {
                        binding.progressBar.hide()
                    }
                }
            }
        }
    }

    private fun showTopUpDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.enter_amount_etb)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = resources.getDimensionPixelSize(R.dimen.dialog_padding)
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.top_up_wallet))
            .setView(container)
            .setPositiveButton(getString(R.string.proceed)) { _, _ ->
                val amountStr = input.text.toString().trim()
                val amount = amountStr.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    toast(getString(R.string.invalid_amount))
                } else {
                    viewModel.topUpWallet(amount)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun openUrl(url: String) {
        try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url)
            )
            startActivity(intent)
        } catch (e: Exception) {
            toast(getString(R.string.cannot_open_browser))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ---- Inline adapter ----

class TransactionAdapter :
    RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {

    private val items = mutableListOf<TransactionData>()

    fun submitList(newItems: List<TransactionData>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = com.ethiouber.customer.databinding.ItemTransactionBinding
            .inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(
        private val binding: com.ethiouber.customer.databinding.ItemTransactionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(transaction: TransactionData) {
            binding.tvType.text = transaction.description
            val fmt = NumberFormat.getNumberInstance(Locale.US)
            val isCredit = transaction.transactionType in listOf("CREDIT", "REFUND", "TOP_UP")
            if (isCredit) {
                binding.tvAmount.text = "+ ETB ${fmt.format(transaction.amount)}"
                binding.tvAmount.setTextColor(Color.parseColor("#078C03"))
            } else {
                binding.tvAmount.text = "- ETB ${fmt.format(transaction.amount)}"
                binding.tvAmount.setTextColor(Color.parseColor("#D32F2F"))
            }
            // Format ISO date string to readable format
            binding.tvDate.text = transaction.createdAt.take(10)
        }
    }
}
