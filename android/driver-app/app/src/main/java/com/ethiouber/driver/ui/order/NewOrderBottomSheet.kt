package com.ethiouber.driver.ui.order

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.ethiouber.driver.R
import com.ethiouber.driver.data.model.OrderData
import com.ethiouber.driver.data.model.ParcelSize
import com.ethiouber.driver.databinding.BottomSheetNewOrderBinding
import com.ethiouber.driver.utils.Constants
import com.ethiouber.driver.utils.Extensions.formatKm
import com.ethiouber.driver.utils.Extensions.toEtb
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NewOrderBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "NewOrderBottomSheet"
        private const val ARG_ORDER_ID = "order_id"
        private const val ARG_ORDER_NUMBER = "order_number"
        private const val ARG_PICKUP_ADDRESS = "pickup_address"
        private const val ARG_DESTINATION_ADDRESS = "destination_address"
        private const val ARG_DISTANCE_KM = "distance_km"
        private const val ARG_TOTAL_FEE = "total_fee"
        private const val ARG_DRIVER_FEE = "driver_fee"
        private const val ARG_PARCEL_SIZE = "parcel_size"
        private const val ARG_IS_URGENT = "is_urgent"
        private const val ARG_CUSTOMER_NAME = "customer_name"
        private const val ARG_CUSTOMER_RATING = "customer_rating"
        private const val ARG_NOTES = "notes"

        fun newInstance(order: OrderData): NewOrderBottomSheet {
            return NewOrderBottomSheet().apply {
                arguments = bundleOf(
                    ARG_ORDER_ID to order.id,
                    ARG_ORDER_NUMBER to order.orderNumber,
                    ARG_PICKUP_ADDRESS to order.pickupAddress,
                    ARG_DESTINATION_ADDRESS to order.destinationAddress,
                    ARG_DISTANCE_KM to order.distanceKm,
                    ARG_TOTAL_FEE to order.totalFee,
                    ARG_DRIVER_FEE to order.driverFee,
                    ARG_PARCEL_SIZE to order.parcelSize,
                    ARG_IS_URGENT to order.isUrgent,
                    ARG_CUSTOMER_NAME to order.customerName,
                    ARG_CUSTOMER_RATING to order.customerRating,
                    ARG_NOTES to order.notes
                )
            }
        }
    }

    private var _binding: BottomSheetNewOrderBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OrderViewModel by activityViewModels()
    private var countDownTimer: CountDownTimer? = null
    private var orderId: Int = -1
    private var dismissed = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetNewOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false

        orderId = arguments?.getInt(ARG_ORDER_ID) ?: -1
        populateOrderDetails()
        startCountdownTimer()
        setupListeners()
        observeAcceptState()
    }

    private fun populateOrderDetails() {
        val args = requireArguments()
        val isUrgent = args.getBoolean(ARG_IS_URGENT, false)
        val distanceKm = args.getDouble(ARG_DISTANCE_KM, 0.0)
        val totalFee = args.getDouble(ARG_TOTAL_FEE, 0.0)
        val driverFee = args.getDouble(ARG_DRIVER_FEE, 0.0)
        val parcelSize = args.getString(ARG_PARCEL_SIZE, "SMALL")
        val customerRating = args.getDouble(ARG_CUSTOMER_RATING, 0.0)

        binding.tvOrderNumber.text = "Order #${args.getString(ARG_ORDER_NUMBER)}"
        binding.tvPickupAddress.text = args.getString(ARG_PICKUP_ADDRESS)
        binding.tvDestinationAddress.text = args.getString(ARG_DESTINATION_ADDRESS)
        binding.tvDistance.text = distanceKm.formatKm()
        binding.tvTotalFee.text = driverFee.toEtb()
        binding.tvParcelSize.text = ParcelSize.getDisplayName(parcelSize ?: "SMALL")
        binding.tvCustomerName.text = args.getString(ARG_CUSTOMER_NAME)
        binding.tvCustomerRating.text = String.format("★ %.1f", customerRating)

        val notes = args.getString(ARG_NOTES)
        if (!notes.isNullOrEmpty()) {
            binding.tvNotes.visibility = View.VISIBLE
            binding.tvNotesLabel.visibility = View.VISIBLE
            binding.tvNotes.text = notes
        } else {
            binding.tvNotes.visibility = View.GONE
            binding.tvNotesLabel.visibility = View.GONE
        }

        if (isUrgent) {
            binding.chipUrgent.visibility = View.VISIBLE
            binding.chipUrgent.text = "URGENT"
            binding.chipUrgent.setChipBackgroundColorResource(R.color.error)
        } else {
            binding.chipUrgent.visibility = View.GONE
        }
    }

    private fun startCountdownTimer() {
        val totalSeconds = Constants.NEW_ORDER_TIMEOUT_SECONDS.toLong()
        binding.progressTimer.max = totalSeconds.toInt()
        binding.progressTimer.progress = totalSeconds.toInt()

        countDownTimer = object : CountDownTimer(totalSeconds * 1000, 1000L) {
            override fun onTick(millisRemaining: Long) {
                val secondsLeft = (millisRemaining / 1000).toInt()
                binding.tvCountdown.text = "${secondsLeft}s"
                binding.progressTimer.progress = secondsLeft

                val colorRes = when {
                    secondsLeft <= 10 -> R.color.error
                    secondsLeft <= 20 -> R.color.accent
                    else -> R.color.primary
                }
                binding.tvCountdown.setTextColor(
                    ContextCompat.getColor(requireContext(), colorRes)
                )
            }

            override fun onFinish() {
                if (!dismissed) {
                    dismissed = true
                    viewModel.rejectOrder(orderId)
                    dismiss()
                }
            }
        }.start()
    }

    private fun setupListeners() {
        binding.btnAccept.setOnClickListener {
            if (!dismissed) {
                countDownTimer?.cancel()
                viewModel.acceptOrder(orderId)
            }
        }

        binding.btnReject.setOnClickListener {
            if (!dismissed) {
                dismissed = true
                countDownTimer?.cancel()
                viewModel.rejectOrder(orderId)
                dismiss()
            }
        }
    }

    private fun observeAcceptState() {
        lifecycleScope.launch {
            viewModel.acceptOrderState.collect { state ->
                when (state) {
                    is OrderUiState.Loading -> {
                        binding.btnAccept.isEnabled = false
                        binding.btnReject.isEnabled = false
                        binding.progressAccept.visibility = View.VISIBLE
                    }
                    is OrderUiState.Success -> {
                        binding.progressAccept.visibility = View.GONE
                        dismissed = true
                        viewModel.setActiveOrder(state.data)
                        dismiss()
                        findNavController().navigate(
                            R.id.action_homeFragment_to_activeOrderFragment,
                            bundleOf("order_id" to state.data.id)
                        )
                        viewModel.resetAcceptState()
                    }
                    is OrderUiState.Error -> {
                        binding.progressAccept.visibility = View.GONE
                        binding.btnAccept.isEnabled = true
                        binding.btnReject.isEnabled = true
                        // Show error and auto-dismiss
                        dismissed = true
                        dismiss()
                        viewModel.resetAcceptState()
                    }
                    is OrderUiState.Idle -> {
                        binding.btnAccept.isEnabled = true
                        binding.btnReject.isEnabled = true
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        _binding = null
    }
}
