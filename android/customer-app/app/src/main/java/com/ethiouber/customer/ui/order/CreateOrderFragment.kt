package com.ethiouber.customer.ui.order

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.ethiouber.customer.R
import com.ethiouber.customer.data.model.ParcelSize
import com.ethiouber.customer.data.model.PaymentMethod
import com.ethiouber.customer.databinding.FragmentCreateOrderBinding
import com.ethiouber.customer.utils.Constants
import com.ethiouber.customer.utils.LocationHelper
import com.ethiouber.customer.utils.hide
import com.ethiouber.customer.utils.show
import com.ethiouber.customer.utils.toast
import com.ethiouber.customer.utils.toEtb
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CreateOrderFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentCreateOrderBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OrderViewModel by viewModels()

    private var googleMap: GoogleMap? = null
    private var pickupLat: Double = 0.0
    private var pickupLng: Double = 0.0
    private var destinationLat: Double = 0.0
    private var destinationLng: Double = 0.0
    private var selectedParcelSize: String = ParcelSize.SMALL.apiValue
    private var selectedPaymentMethod: String = PaymentMethod.WALLET.apiValue
    private var isUrgent: Boolean = false
    private var isSettingPickup: Boolean = true
    private var feeEstimated: Boolean = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) autoDetectPickup()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mapViewCreate.onCreate(savedInstanceState)
        binding.mapViewCreate.getMapAsync(this)

        setupParcelSizeSpinner()
        setupPaymentMethodSpinner()
        setupListeners()
        observeViewModel()
    }

    private fun setupParcelSizeSpinner() {
        val sizes = ParcelSize.values().map { it.displayName }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, sizes)
        binding.spinnerParcelSize.setAdapter(adapter)
        binding.spinnerParcelSize.setText(sizes[0], false)
        selectedParcelSize = ParcelSize.SMALL.apiValue

        binding.spinnerParcelSize.setOnItemClickListener { _, _, position, _ ->
            selectedParcelSize = ParcelSize.values()[position].apiValue
            feeEstimated = false
            binding.cardFeeEstimate.hide()
        }
    }

    private fun setupPaymentMethodSpinner() {
        val methods = PaymentMethod.values().map { it.displayName }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, methods)
        binding.spinnerPaymentMethod.setAdapter(adapter)
        binding.spinnerPaymentMethod.setText(methods.find { it.contains("Wallet") } ?: methods[0], false)
        selectedPaymentMethod = PaymentMethod.WALLET.apiValue

        binding.spinnerPaymentMethod.setOnItemClickListener { _, _, position, _ ->
            selectedPaymentMethod = PaymentMethod.values()[position].apiValue
        }
    }

    private fun setupListeners() {
        binding.btnAutoDetectPickup.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                autoDetectPickup()
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        binding.btnSetPickup.setOnClickListener {
            isSettingPickup = true
            toast("Tap on the map to set pickup location")
        }

        binding.btnSetDestination.setOnClickListener {
            isSettingPickup = false
            toast("Tap on the map to set destination")
        }

        binding.switchUrgent.setOnCheckedChangeListener { _, checked ->
            isUrgent = checked
            feeEstimated = false
            binding.cardFeeEstimate.hide()
        }

        binding.btnEstimateFee.setOnClickListener {
            if (!validateLocations()) return@setOnClickListener
            viewModel.estimateFee(
                pickupLat, pickupLng,
                destinationLat, destinationLng,
                selectedParcelSize, isUrgent
            )
        }

        binding.btnCreateOrder.setOnClickListener {
            if (!validateLocations()) return@setOnClickListener
            if (!feeEstimated) {
                toast("Please estimate fee first")
                return@setOnClickListener
            }
            val description = binding.etParcelDescription.text.toString().trim()
            val pickupAddress = binding.etPickupAddress.text.toString().trim().ifEmpty { "Pickup Location" }
            val destinationAddress = binding.etDestinationAddress.text.toString().trim().ifEmpty { "Destination" }

            viewModel.createOrder(
                pickupAddress, pickupLat, pickupLng,
                destinationAddress, destinationLat, destinationLng,
                description, selectedParcelSize, isUrgent, selectedPaymentMethod
            )
        }

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is OrderUiState.Loading -> {
                        binding.progressBar.show()
                        binding.btnEstimateFee.isEnabled = false
                        binding.btnCreateOrder.isEnabled = false
                    }
                    is OrderUiState.FeeEstimated -> {
                        binding.progressBar.hide()
                        binding.btnEstimateFee.isEnabled = true
                        binding.btnCreateOrder.isEnabled = true
                        feeEstimated = true
                        val estimate = state.estimate
                        binding.tvBaseFee.text = estimate.baseFee.toEtb()
                        binding.tvDistanceFee.text = estimate.distanceFee.toEtb()
                        binding.tvSizeFee.text = estimate.sizeFee.toEtb()
                        binding.tvUrgencySurcharge.text = estimate.urgencySurcharge.toEtb()
                        binding.tvTotalFee.text = estimate.totalFee.toEtb()
                        binding.tvDistance.text = "%.1f km".format(estimate.distanceKm)
                        binding.tvDuration.text = "${estimate.estimatedDurationMinutes} min"
                        binding.cardFeeEstimate.show()
                        viewModel.resetState()
                    }
                    is OrderUiState.Success -> {
                        binding.progressBar.hide()
                        binding.btnEstimateFee.isEnabled = true
                        binding.btnCreateOrder.isEnabled = true
                        toast(state.message)
                        state.order?.let { order ->
                            val bundle = Bundle().apply {
                                putInt(Constants.KEY_ORDER_ID, order.id)
                            }
                            findNavController().navigate(R.id.action_createOrderFragment_to_trackingFragment, bundle)
                        }
                        viewModel.resetState()
                    }
                    is OrderUiState.Error -> {
                        binding.progressBar.hide()
                        binding.btnEstimateFee.isEnabled = true
                        binding.btnCreateOrder.isEnabled = true
                        toast(state.message)
                        viewModel.resetState()
                    }
                    else -> {
                        binding.progressBar.hide()
                        binding.btnEstimateFee.isEnabled = true
                        binding.btnCreateOrder.isEnabled = true
                    }
                }
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = true

        val defaultLocation = LatLng(Constants.DEFAULT_LAT, Constants.DEFAULT_LNG)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, Constants.DEFAULT_ZOOM))

        map.setOnMapClickListener { latLng ->
            if (isSettingPickup) {
                pickupLat = latLng.latitude
                pickupLng = latLng.longitude
                updatePickupMarker(latLng)
                binding.etPickupAddress.setText("${latLng.latitude}, ${latLng.longitude}")
            } else {
                destinationLat = latLng.latitude
                destinationLng = latLng.longitude
                updateDestinationMarker(latLng)
                binding.etDestinationAddress.setText("${latLng.latitude}, ${latLng.longitude}")
            }
            feeEstimated = false
            binding.cardFeeEstimate.hide()
        }
    }

    private fun updatePickupMarker(latLng: LatLng) {
        googleMap?.let { map ->
            map.clear()
            map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Pickup")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )
            if (destinationLat != 0.0 && destinationLng != 0.0) {
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(destinationLat, destinationLng))
                        .title("Destination")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                )
            }
        }
    }

    private fun updateDestinationMarker(latLng: LatLng) {
        googleMap?.let { map ->
            map.clear()
            if (pickupLat != 0.0 && pickupLng != 0.0) {
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(pickupLat, pickupLng))
                        .title("Pickup")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                )
            }
            map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Destination")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        }
    }

    private fun autoDetectPickup() {
        viewLifecycleOwner.lifecycleScope.launch {
            val location = LocationHelper.getCurrentLocation(requireContext())
            if (location != null) {
                pickupLat = location.latitude
                pickupLng = location.longitude
                val latLng = LatLng(location.latitude, location.longitude)
                binding.etPickupAddress.setText("${location.latitude}, ${location.longitude}")
                updatePickupMarker(latLng)
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, Constants.TRACKING_ZOOM))
            } else {
                toast("Unable to get current location")
            }
        }
    }

    private fun validateLocations(): Boolean {
        if (pickupLat == 0.0 && pickupLng == 0.0) {
            toast("Please set a pickup location")
            return false
        }
        if (destinationLat == 0.0 && destinationLng == 0.0) {
            toast("Please set a destination")
            return false
        }
        return true
    }

    override fun onResume() { super.onResume(); binding.mapViewCreate.onResume() }
    override fun onPause() { super.onPause(); binding.mapViewCreate.onPause() }
    override fun onStart() { super.onStart(); binding.mapViewCreate.onStart() }
    override fun onStop() { super.onStop(); binding.mapViewCreate.onStop() }
    override fun onDestroy() { super.onDestroy(); binding.mapViewCreate.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); binding.mapViewCreate.onLowMemory() }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
