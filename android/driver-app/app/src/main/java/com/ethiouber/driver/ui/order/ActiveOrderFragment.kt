package com.ethiouber.driver.ui.order

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.ethiouber.driver.R
import com.ethiouber.driver.data.model.OrderData
import com.ethiouber.driver.data.model.OrderStatus
import com.ethiouber.driver.data.model.ParcelSize
import com.ethiouber.driver.databinding.FragmentActiveOrderBinding
import com.ethiouber.driver.utils.Extensions.hide
import com.ethiouber.driver.utils.Extensions.show
import com.ethiouber.driver.utils.Extensions.toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ActiveOrderFragment : Fragment() {

    private var _binding: FragmentActiveOrderBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OrderViewModel by activityViewModels()
    private val args: ActiveOrderFragmentArgs by navArgs()

    private var currentOrder: OrderData? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActiveOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadActiveOrder(args.orderId)
        observeActiveOrder()
        observeStatusUpdateState()
        observeOtpVerifyState()
        setupListeners()
    }

    private fun observeActiveOrder() {
        lifecycleScope.launch {
            viewModel.activeOrder.collect { order ->
                order?.let {
                    currentOrder = it
                    populateOrderDetails(it)
                    updateUiForStatus(it.status)
                }
            }
        }
    }

    private fun populateOrderDetails(order: OrderData) {
        binding.tvPickup.text = getString(R.string.pickup_address_format, order.pickupAddress)
        binding.tvDestination.text =
            getString(R.string.destination_address_format, order.destinationAddress)
        binding.tvParcel.text = getString(
            R.string.parcel_format,
            ParcelSize.getDisplayName(order.parcelSize)
        )
        binding.tvCustomerPhone.text =
            getString(R.string.customer_phone_format, order.customerPhone)
    }

    private fun updateUiForStatus(status: String) {
        val buttonLabel = OrderStatus.getButtonLabel(status)
        binding.btnAction.text = buttonLabel

        if (status == OrderStatus.ARRIVED_AT_DESTINATION) {
            binding.layoutOtp.show()
            binding.btnAction.hide()
        } else {
            binding.layoutOtp.hide()
            binding.btnAction.show()
            binding.btnAction.isEnabled = buttonLabel.isNotEmpty()
        }
    }

    private fun setupListeners() {
        binding.btnAction.setOnClickListener {
            val order = currentOrder ?: return@setOnClickListener
            val nextStatus = OrderStatus.getNextStatus(order.status) ?: return@setOnClickListener
            viewModel.updateOrderStatus(order.id, nextStatus)
        }

        binding.btnConfirmDelivery.setOnClickListener {
            val order = currentOrder ?: return@setOnClickListener
            val otp = binding.etOtp.text.toString().trim()
            when {
                otp.isEmpty() -> toast(getString(R.string.enter_otp))
                otp.length != 6 -> toast(getString(R.string.otp_length_error))
                else -> viewModel.verifyDeliveryOtp(order.id, otp)
            }
        }

        binding.btnNavigate.setOnClickListener {
            val order = currentOrder ?: return@setOnClickListener
            val geoUri = Uri.parse(
                "google.navigation:q=${order.destinationLat},${order.destinationLng}&mode=d"
            )
            val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                setPackage("com.google.android.apps.maps")
            }
            if (mapIntent.resolveActivity(requireActivity().packageManager) != null) {
                startActivity(mapIntent)
            } else {
                val browserUri = Uri.parse(
                    "https://maps.google.com/?daddr=${order.destinationLat},${order.destinationLng}"
                )
                startActivity(Intent(Intent.ACTION_VIEW, browserUri))
            }
        }
    }

    private fun observeStatusUpdateState() {
        lifecycleScope.launch {
            viewModel.statusUpdateState.collect { state ->
                when (state) {
                    is OrderUiState.Loading -> {
                        binding.btnAction.isEnabled = false
                    }
                    is OrderUiState.Success -> {
                        binding.btnAction.isEnabled = true
                        viewModel.resetStatusUpdateState()
                    }
                    is OrderUiState.Error -> {
                        binding.btnAction.isEnabled = true
                        toast(state.message)
                        viewModel.resetStatusUpdateState()
                    }
                    is OrderUiState.Idle -> {
                        binding.btnAction.isEnabled = true
                    }
                }
            }
        }
    }

    private fun observeOtpVerifyState() {
        lifecycleScope.launch {
            viewModel.otpVerifyState.collect { state ->
                when (state) {
                    is OrderUiState.Loading -> {
                        binding.btnConfirmDelivery.isEnabled = false
                    }
                    is OrderUiState.Success -> {
                        binding.btnConfirmDelivery.isEnabled = true
                        toast(getString(R.string.delivery_confirmed))
                        viewModel.resetOtpVerifyState()
                        findNavController().navigate(R.id.action_activeOrderFragment_to_orderHistoryFragment)
                    }
                    is OrderUiState.Error -> {
                        binding.btnConfirmDelivery.isEnabled = true
                        toast(state.message)
                        viewModel.resetOtpVerifyState()
                    }
                    is OrderUiState.Idle -> {
                        binding.btnConfirmDelivery.isEnabled = true
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
