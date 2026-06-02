package com.ethiouber.driver.ui.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ethiouber.driver.data.model.OrderData
import com.ethiouber.driver.data.model.OrderStatus
import com.ethiouber.driver.databinding.FragmentOrderHistoryBinding
import com.ethiouber.driver.databinding.ItemOrderBinding
import com.ethiouber.driver.utils.Extensions.hide
import com.ethiouber.driver.utils.Extensions.show
import com.ethiouber.driver.utils.Extensions.toDisplayDate
import com.ethiouber.driver.utils.Extensions.toEtb
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OrderHistoryFragment : Fragment() {

    private var _binding: FragmentOrderHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OrderViewModel by activityViewModels()
    private lateinit var adapter: OrderHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrderHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeOrders()
        viewModel.loadOrders()
    }

    private fun setupRecyclerView() {
        adapter = OrderHistoryAdapter()
        binding.rvOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOrders.adapter = adapter
    }

    private fun observeOrders() {
        lifecycleScope.launch {
            viewModel.ordersLoading.collect { loading ->
                if (loading) binding.progressBar.show() else binding.progressBar.hide()
            }
        }

        lifecycleScope.launch {
            viewModel.orders.collect { orders ->
                adapter.submitList(orders)
                if (orders.isEmpty()) {
                    binding.tvEmpty.show()
                    binding.rvOrders.hide()
                } else {
                    binding.tvEmpty.hide()
                    binding.rvOrders.show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class OrderHistoryAdapter : ListAdapter<OrderData, OrderHistoryAdapter.OrderViewHolder>(OrderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemOrderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class OrderViewHolder(private val binding: ItemOrderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(order: OrderData) {
            binding.tvOrderId.text = "#${order.orderNumber}"
            binding.tvStatus.text = OrderStatus.getStatusDisplayName(order.status)
            binding.tvRoute.text = "${order.pickupAddress} → ${order.destinationAddress}"
            binding.tvEarnings.text = order.driverFee.toEtb()
            binding.tvDate.text = order.createdAt.toDisplayDate()
        }
    }

    class OrderDiffCallback : DiffUtil.ItemCallback<OrderData>() {
        override fun areItemsTheSame(oldItem: OrderData, newItem: OrderData) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: OrderData, newItem: OrderData) =
            oldItem == newItem
    }
}
