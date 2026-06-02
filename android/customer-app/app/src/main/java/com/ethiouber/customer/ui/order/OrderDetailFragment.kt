package com.ethiouber.customer.ui.order

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.ethiouber.customer.R
import com.ethiouber.customer.data.model.OrderData
import com.ethiouber.customer.databinding.FragmentOrderDetailBinding
import com.ethiouber.customer.utils.Constants
import com.ethiouber.customer.utils.hide
import com.ethiouber.customer.utils.show
import com.ethiouber.customer.utils.toast
import com.ethiouber.customer.utils.toEtb
import com.ethiouber.customer.utils.toStatusLabel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OrderDetailFragment : Fragment() {

    private var _binding: FragmentOrderDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OrderViewModel by viewModels()
    private var orderId: Int = -1
    private var currentOrder: OrderData? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrderDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        orderId = arguments?.getInt(Constants.KEY_ORDER_ID, -1) ?: -1
        if (orderId == -1) {
            toast("Invalid order")
            findNavController().navigateUp()
            return
        }

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        viewModel.loadOrder(orderId)
        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.activeOrder.collect { order ->
                order?.let { displayOrder(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is OrderUiState.Loading -> binding.progressBar.show()
                    is OrderUiState.Success -> {
                        binding.progressBar.hide()
                        toast(state.message)
                        if (state.message.contains("cancelled", ignoreCase = true)) {
                            findNavController().navigateUp()
                        }
                        viewModel.resetState()
                    }
                    is OrderUiState.Error -> {
                        binding.progressBar.hide()
                        toast(state.message)
                        viewModel.resetState()
                    }
                    else -> binding.progressBar.hide()
                }
            }
        }
    }

    private fun displayOrder(order: OrderData) {
        currentOrder = order
        binding.tvOrderNumber.text = order.orderNumber
        binding.tvStatus.text = order.status.toStatusLabel()
        binding.tvPickupAddress.text = order.pickupAddress
        binding.tvDestinationAddress.text = order.destinationAddress
        binding.tvParcelSize.text = order.parcelSize
        binding.tvDeliveryFee.text = order.deliveryFee.toEtb()
        binding.tvPaymentMethod.text = order.paymentMethod
        binding.tvPaymentStatus.text = order.paymentStatus
        binding.tvCreatedAt.text = order.createdAt

        order.parcelDescription?.let {
            binding.tvParcelDescription.text = it
            binding.tvParcelDescription.show()
        }

        if (order.isUrgent) {
            binding.chipUrgent.show()
        } else {
            binding.chipUrgent.hide()
        }

        order.deliveryOtp?.let {
            binding.cardOtp.show()
            binding.tvDeliveryOtp.text = it
        }

        order.driver?.let { driver ->
            binding.cardDriver.show()
            binding.tvDriverName.text = driver.fullName
            binding.tvDriverVehicle.text = "${driver.vehicleType} - ${driver.vehiclePlate}"
            binding.tvDriverRating.text = "%.1f".format(driver.rating)

            driver.profilePhoto?.let { photoUrl ->
                Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .circleCrop()
                    .into(binding.ivDriverPhoto)
            }

            binding.btnCallDriver.setOnClickListener {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${driver.phoneNumber}"))
                startActivity(intent)
            }

            binding.btnTrackOrder.setOnClickListener {
                val bundle = Bundle().apply { putInt(Constants.KEY_ORDER_ID, order.id) }
                findNavController().navigate(R.id.action_orderDetailFragment_to_trackingFragment, bundle)
            }
        } ?: run {
            binding.cardDriver.hide()
            binding.btnTrackOrder.hide()
        }

        val cancellableStatuses = setOf(
            Constants.STATUS_PENDING,
            Constants.STATUS_DRIVER_ON_WAY
        )
        if (order.status in cancellableStatuses) {
            binding.btnCancelOrder.show()
            binding.btnCancelOrder.setOnClickListener { showCancelDialog() }
        } else {
            binding.btnCancelOrder.hide()
        }

        if (order.status == Constants.STATUS_DELIVERED && order.driverRating == null) {
            binding.cardRating.show()
            setupRating(order.id)
        } else {
            binding.cardRating.hide()
        }
    }

    private fun showCancelDialog() {
        val reasons = arrayOf(
            "Changed my mind",
            "Wrong destination",
            "Driver taking too long",
            "Found another option",
            "Other"
        )
        var selectedReason = reasons[0]

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cancel Order")
            .setSingleChoiceItems(reasons, 0) { _, which ->
                selectedReason = reasons[which]
            }
            .setPositiveButton("Cancel Order") { _, _ ->
                currentOrder?.let { viewModel.cancelOrder(it.id, selectedReason) }
            }
            .setNegativeButton("Keep Order", null)
            .show()
    }

    private fun setupRating(orderId: Int) {
        var selectedRating = 5
        binding.ratingBar.setOnRatingBarChangeListener { _, rating, _ ->
            selectedRating = rating.toInt()
        }
        binding.btnSubmitRating.setOnClickListener {
            val feedback = binding.etRatingComment.text.toString().trim()
            viewModel.rateDriver(orderId, selectedRating, feedback)
            binding.cardRating.hide()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
