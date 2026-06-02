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
import com.ethiouber.customer.R
import com.ethiouber.customer.databinding.FragmentRatingBinding
import com.ethiouber.customer.utils.hide
import com.ethiouber.customer.utils.show
import com.ethiouber.customer.utils.toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RatingFragment : Fragment() {

    private var _binding: FragmentRatingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OrderViewModel by viewModels()
    private val args: RatingFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRatingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSubmit.setOnClickListener {
            val rating = binding.ratingBar.rating.toInt()
            if (rating == 0) {
                toast(getString(R.string.please_select_rating))
                return@setOnClickListener
            }
            val feedback = binding.etFeedback.text.toString().trim()
            viewModel.rateDriver(args.orderId, rating, feedback)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is OrderUiState.Loading -> {
                        binding.btnSubmit.isEnabled = false
                    }
                    is OrderUiState.Success -> {
                        binding.btnSubmit.isEnabled = true
                        toast(state.message.ifEmpty { getString(R.string.rating_submitted) })
                        viewModel.resetState()
                        // Navigate back to home, clearing the back stack up to homeFragment
                        findNavController().navigate(
                            R.id.action_ratingFragment_to_homeFragment
                        )
                    }
                    is OrderUiState.Error -> {
                        binding.btnSubmit.isEnabled = true
                        toast(state.message)
                        viewModel.resetState()
                    }
                    else -> {
                        binding.btnSubmit.isEnabled = true
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
