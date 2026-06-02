package com.ethiouber.customer.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.ethiouber.customer.R
import com.ethiouber.customer.data.local.SessionManager
import com.ethiouber.customer.databinding.FragmentHomeBinding
import com.ethiouber.customer.utils.LocationHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private var googleMap: GoogleMap? = null

    @Inject lateinit var sessionManager: SessionManager

    // Shashemene, Ethiopia coordinates as default
    private val defaultLocation = LatLng(7.0621, 38.7468)

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) moveToCurrentLocation()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userName = sessionManager.getUser()?.fullName?.split(" ")?.firstOrNull() ?: "there"
        binding.tvUserName.text = "Hello, $userName!"
        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)

        binding.fabNewDelivery.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_createOrderFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.nearbyDrivers.collect { drivers ->
                showNearbyDriverMarkers(drivers)
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = true

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            map.isMyLocationEnabled = true
            moveToCurrentLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 13f))
        }
    }

    private fun moveToCurrentLocation() {
        viewLifecycleOwner.lifecycleScope.launch {
            val location = LocationHelper.getCurrentLocation(requireContext())
            if (location != null) {
                val latLng = LatLng(location.latitude, location.longitude)
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                viewModel.loadNearbyDrivers(location.latitude, location.longitude)
            }
        }
    }

    private fun showNearbyDriverMarkers(drivers: List<com.ethiouber.customer.data.model.NearbyDriver>) {
        googleMap?.clear()
        drivers.forEach { driver ->
            val pos = LatLng(driver.latitude, driver.longitude)
            googleMap?.addMarker(
                MarkerOptions()
                    .position(pos)
                    .title(driver.fullName)
                    .snippet(driver.vehicleType)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )
        }
    }

    override fun onResume() { super.onResume(); binding.mapView.onResume() }
    override fun onPause() { super.onPause(); binding.mapView.onPause() }
    override fun onStart() { super.onStart(); binding.mapView.onStart() }
    override fun onStop() { super.onStop(); binding.mapView.onStop() }
    override fun onDestroy() { super.onDestroy(); binding.mapView.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); binding.mapView.onLowMemory() }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
