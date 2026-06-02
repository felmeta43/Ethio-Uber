package com.ethiouber.customer.ui.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.ethiouber.customer.databinding.FragmentTrackingBinding
import com.ethiouber.customer.service.TrackingWebSocket
import com.ethiouber.customer.utils.hide
import com.ethiouber.customer.utils.show
import com.ethiouber.customer.utils.toStatusLabel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TrackingFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentTrackingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OrderViewModel by viewModels()
    private val args: TrackingFragmentArgs by navArgs()

    @Inject lateinit var trackingWebSocket: TrackingWebSocket

    private var googleMap: GoogleMap? = null
    private var driverMarker: Marker? = null
    private var pickupMarker: Marker? = null
    private var destinationMarker: Marker? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTrackingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)

        viewModel.loadOrder(args.orderId)

        // Show OTP code for customer to share with driver
        binding.tvDeliveryOtp.text = "Delivery Code: ${args.deliveryOtp}"

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        // Observe order updates
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.activeOrder.collect { order ->
                order ?: return@collect
                binding.tvStatus.text = order.status.toStatusLabel()
                binding.tvDriverName.text = order.driverName ?: "Finding driver..."
                binding.tvPickupAddress.text = order.pickupAddress
                binding.tvDestinationAddress.text = order.destinationAddress

                if (order.status == "DELIVERED") {
                    binding.cardDelivered.show()
                    binding.btnRateDriver.setOnClickListener {
                        findNavController().navigate(
                            TrackingFragmentDirections.actionTrackingFragmentToRatingFragment(order.id)
                        )
                    }
                }
            }
        }

        // Observe real-time driver location
        viewLifecycleOwner.lifecycleScope.launch {
            trackingWebSocket.locationUpdates.collect { update ->
                update ?: return@collect
                val driverPos = LatLng(update.lat, update.lng)
                if (driverMarker == null) {
                    driverMarker = googleMap?.addMarker(
                        MarkerOptions()
                            .position(driverPos)
                            .title(update.driverName ?: "Driver")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                    )
                } else {
                    driverMarker?.position = driverPos
                }
                googleMap?.animateCamera(CameraUpdateFactory.newLatLng(driverPos))
                update.orderStatus?.let { binding.tvStatus.text = it.toStatusLabel() }
            }
        }

        trackingWebSocket.connect(args.orderId)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = true
        // Place pickup / destination markers from args
        val pickup = LatLng(args.pickupLat.toDouble(), args.pickupLng.toDouble())
        val dest = LatLng(args.destLat.toDouble(), args.destLng.toDouble())
        pickupMarker = map.addMarker(MarkerOptions().position(pickup).title("Pickup")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))
        destinationMarker = map.addMarker(MarkerOptions().position(dest).title("Destination")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(pickup, 14f))
    }

    override fun onResume() { super.onResume(); binding.mapView.onResume() }
    override fun onPause() { super.onPause(); binding.mapView.onPause() }
    override fun onStart() { super.onStart(); binding.mapView.onStart() }
    override fun onStop() { super.onStop(); binding.mapView.onStop() }
    override fun onDestroy() { super.onDestroy(); trackingWebSocket.disconnect(); binding.mapView.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); binding.mapView.onLowMemory() }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
