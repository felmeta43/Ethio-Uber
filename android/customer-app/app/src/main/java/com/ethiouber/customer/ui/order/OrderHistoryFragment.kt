package com.ethiouber.customer.ui.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ethiouber.customer.R
import com.ethiouber.customer.data.model.OrderData
import com.ethiouber.customer.databinding.FragmentOrderHistoryBinding
import com.ethiouber.customer.databinding.ItemOrderBinding
import com.ethiouber.customer.utils.Constants
import com.ethiouber.customer.utils.hide
import com.ethiouber.customer.utils.show
import com.ethiouber.customer.utils.toast
import com.ethiouber.customer.utils.toEtb
import com.ethiouber.customer.utils.toStatusLabel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OrderHistoryFragment : Fragment() {

    private var _binding: FragmentOrderHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OrderViewModel by viewModels()
    private lateinit var adapter: OrderAdapter

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
        setupSwipeRefresh()
        observeViewModel()

        viewModel.loadOrders()
    }

    private fun setupRecyclerView() {
        adapter = OrderAdapter { order ->
            val bundle = Bundle().apply { putInt(Constants.KEY_ORDER_ID, order.id) }
            findNavController().navigate(R.id.action_orderHistoryFragment_to_orderDetailFragment, bundle)
        }
        binding.recyclerOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerOrders.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadOrders()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is OrderUiState.Loading -> {
                        if (!binding.swipeRefresh.isRefreshing) {
                            binding.progressBar.show()
                        }
                    }
                    is OrderUiState.OrderList -> {
                        binding.progressBar.hide()
                        binding.swipeRefresh.isRefreshing = false
                        if (state.orders.isEmpty()) {
                            binding.tvEmpty.show()
                            binding.recyclerOrders.hide()
                        } else {
                            binding.tvEmpty.hide()
                            binding.recyclerOrders.show()
                            adapter.submitList(state.orders)
                        }
                        viewModel.resetState()
                    }
                    is OrderUiState.Error -> {
                        binding.progressBar.hide()
                        binding.swipeRefresh.isRefreshing = false
                        toast(state.message)
                        viewModel.resetState()
                    }
                    else -> {
                        binding.progressBar.hide()
                        binding.swipeRefresh.isRefreshing = false
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

class OrderAdapter(
    private val onItemClick: (OrderData) -> Unit
) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    private val orders = mutableListOf<OrderData>()

    fun submitList(newOrders: List<OrderData>) {
        orders.clear()
        orders.addAll(newOrders)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemOrderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(orders[position])
    }

    override fun getItemCount() = orders.size

    inner class OrderViewHolder(private val binding: ItemOrderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(order: OrderData) {
            binding.tvOrderNumber.text = order.orderNumber
            binding.tvStatus.text = order.status.toStatusLabel()
            binding.tvPickupAddress.text = order.pickupAddress
            binding.tvDestinationAddress.text = order.destinationAddress
            binding.tvDeliveryFee.text = order.deliveryFee.toEtb()
            binding.tvDate.text = order.createdAt.take(10)

            val statusColor = when (order.status) {
                Constants.STATUS_DELIVERED -> android.R.color.holo_green_dark
                Constants.STATUS_CANCELLED -> android.R.color.holo_red_dark
                Constants.STATUS_PENDING -> android.R.color.holo_orange_dark
                else -> com.google.android.material.R.color.design_default_color_primary
            }
            binding.tvStatus.setTextColor(
                binding.root.context.getColor(statusColor)
            )

            binding.root.setOnClickListener { onItemClick(order) }
        }
    }
}
