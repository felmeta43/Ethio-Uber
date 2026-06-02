package com.ethiouber.driver.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.ethiouber.driver.R
import com.ethiouber.driver.databinding.FragmentHomeBinding
import com.ethiouber.driver.service.LocationTrackingService
import com.ethiouber.driver.ui.order.NewOrderBottomSheet
import com.ethiouber.driver.utils.Constants
import com.ethiouber.driver.utils.Extensions.hide
import com.ethiouber.driver.utils.Extensions.show
import com.ethiouber.driver.utils.Extensions.toast
import com.ethiouber.driver.utils.Extensions.toEtb
import com.ethiouber.driver.utils.LocationHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private var googleMap: GoogleMap? = null
    private var currentLatLng: LatLng? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        setupListeners()
        observeViewModelState()
        requestLocationPermission()
    }

    private fun setupMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        if (LocationHelper.hasLocationPermission(requireContext())) {
            try {
                map.isMyLocationEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = false
            } catch (e: SecurityException) {
                // permission revoked mid-session
            }
        }
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isCompassEnabled = true
        map.uiSettings.isMapToolbarEnabled = false

        // Move to last known location
        lifecycleScope.launch {
            val location = LocationHelper.getLastKnownLocation(requireContext())
            location?.let {
                currentLatLng = LatLng(it.latitude, it.longitude)
                map.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(currentLatLng!!, Constants.MAP_ZOOM_DEFAULT)
                )
            }
        }

        // Track driver's real-time location updates
        lifecycleScope.launch {
            LocationTrackingService.currentLocation.collect { locationData ->
                locationData?.let {
                    val latLng = LatLng(it.lat, it.lng)
                    currentLatLng = latLng
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnOnlineToggle.setOnClickListener {
            val lat = currentLatLng?.latitude
            val lng = currentLatLng?.longitude
            viewModel.toggleOnlineStatus(lat, lng)
        }

        binding.fabMyLocation.setOnClickListener {
            currentLatLng?.let { latLng ->
                googleMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(latLng, Constants.MAP_ZOOM_DEFAULT)
                )
            }
        }

        binding.cardEarnings.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_earningsFragment)
        }

        binding.ivProfile.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_profileFragment)
        }

        binding.ivOrderHistory.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_orderHistoryFragment)
        }
    }

    private fun observeViewModelState() {
        lifecycleScope.launch {
            viewModel.isOnline.collect { isOnline ->
                updateOnlineToggle(isOnline)
                if (isOnline) {
                    startLocationService()
                } else {
                    stopLocationService()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.profile.collect { user ->
                user?.let {
                    binding.tvDriverName.text = it.fullName
                    binding.tvRating.text = String.format("%.1f", it.rating)
                    if (!it.profilePhoto.isNullOrEmpty()) {
                        Glide.with(this@HomeFragment)
                            .load(it.profilePhoto)
                            .placeholder(R.drawable.ic_launcher_foreground)
                            .circleCrop()
                            .into(binding.ivProfile)
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.wallet.collect { wallet ->
                wallet?.let {
                    binding.tvTodayEarnings.text = it.todayEarnings.toEtb()
                    binding.tvTotalDeliveries.text = "${it.totalDeliveriesToday} deliveries"
                }
            }
        }

        lifecycleScope.launch {
            viewModel.statusUpdateLoading.collect { loading ->
                binding.btnOnlineToggle.isEnabled = !loading
                if (loading) {
                    binding.progressStatus.show()
                } else {
                    binding.progressStatus.hide()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    toast(it)
                    viewModel.clearError()
                }
            }
        }

        // Observe new orders from WebSocket
        lifecycleScope.launch {
            viewModel.webSocket.newOrderEvent.collect { orderData ->
                if (viewModel.isOnline.value) {
                    val bottomSheet = NewOrderBottomSheet.newInstance(orderData)
                    bottomSheet.show(childFragmentManager, NewOrderBottomSheet.TAG)
                }
            }
        }

        // Observe order cancellations
        lifecycleScope.launch {
            viewModel.webSocket.orderCancelledEvent.collect { orderId ->
                toast("Order #$orderId was cancelled")
            }
        }
    }

    private fun updateOnlineToggle(isOnline: Boolean) {
        if (isOnline) {
            binding.btnOnlineToggle.text = "GO OFFLINE"
            binding.btnOnlineToggle.setBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.error)
            )
            binding.tvOnlineStatus.text = "You are ONLINE"
            binding.tvOnlineStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.primary)
            )
        } else {
            binding.btnOnlineToggle.text = "GO ONLINE"
            binding.btnOnlineToggle.setBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.primary)
            )
            binding.tvOnlineStatus.text = "You are OFFLINE"
            binding.tvOnlineStatus.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
            )
        }
    }

    private fun startLocationService() {
        val intent = Intent(requireContext(), LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
        }
        ContextCompat.startForegroundService(requireContext(), intent)
    }

    private fun stopLocationService() {
        val intent = Intent(requireContext(), LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        requireContext().startService(intent)
    }

    private fun requestLocationPermission() {
        if (!LocationHelper.hasLocationPermission(requireContext())) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                Constants.REQUEST_LOCATION_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupMap()
            } else {
                toast("Location permission is required for delivery tracking")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
