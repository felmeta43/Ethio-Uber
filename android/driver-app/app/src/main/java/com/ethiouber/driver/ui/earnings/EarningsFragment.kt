package com.ethiouber.driver.ui.earnings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ethiouber.driver.R
import com.ethiouber.driver.data.model.TransactionData
import com.ethiouber.driver.data.model.TransactionType
import com.ethiouber.driver.databinding.FragmentEarningsBinding
import com.ethiouber.driver.databinding.ItemEarningBinding
import com.ethiouber.driver.utils.Extensions.hide
import com.ethiouber.driver.utils.Extensions.show
import com.ethiouber.driver.utils.Extensions.toDisplayDate
import com.ethiouber.driver.utils.Extensions.toEtb
import com.ethiouber.driver.utils.Extensions.toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EarningsFragment : Fragment() {

    private var _binding: FragmentEarningsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EarningsViewModel by viewModels()
    private lateinit var adapter: EarningsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEarningsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        observeEarningsState()
    }

    private fun setupRecyclerView() {
        adapter = EarningsAdapter()
        binding.rvEarnings.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEarnings.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnWithdrawal.setOnClickListener {
            findNavController().navigate(R.id.action_earningsFragment_to_withdrawalFragment)
        }
    }

    private fun observeEarningsState() {
        lifecycleScope.launch {
            viewModel.earningsState.collect { state ->
                when (state) {
                    is EarningsUiState.Loading -> {
                        binding.progressBar.show()
                    }
                    is EarningsUiState.Success -> {
                        binding.progressBar.hide()
                        val wallet = state.wallet
                        binding.tvTotalEarnings.text = wallet.balance.toEtb()
                        binding.tvToday.text = wallet.todayEarnings.toEtb()
                        binding.tvPending.text = getString(
                            R.string.pending_withdrawal_format,
                            wallet.weekEarnings.toEtb()
                        )
                        adapter.submitList(state.transactions)
                    }
                    is EarningsUiState.Error -> {
                        binding.progressBar.hide()
                        toast(state.message)
                    }
                    is EarningsUiState.Idle -> {
                        binding.progressBar.hide()
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

class EarningsAdapter :
    ListAdapter<TransactionData, EarningsAdapter.EarningViewHolder>(EarningDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EarningViewHolder {
        val binding = ItemEarningBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return EarningViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EarningViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class EarningViewHolder(private val binding: ItemEarningBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(transaction: TransactionData) {
            binding.tvOrderId.text = transaction.reference ?: "#${transaction.id}"
            val prefix = if (TransactionType.isCredit(transaction.type)) "+" else "-"
            binding.tvAmount.text = "$prefix${transaction.amount.toEtb()}"
            binding.tvDate.text = transaction.createdAt.toDisplayDate()
        }
    }

    class EarningDiffCallback : DiffUtil.ItemCallback<TransactionData>() {
        override fun areItemsTheSame(oldItem: TransactionData, newItem: TransactionData) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: TransactionData, newItem: TransactionData) =
            oldItem == newItem
    }
}
