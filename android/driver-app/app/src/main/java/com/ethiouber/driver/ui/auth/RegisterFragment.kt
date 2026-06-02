package com.ethiouber.driver.ui.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.ethiouber.driver.R
import com.ethiouber.driver.data.model.VehicleType
import com.ethiouber.driver.databinding.FragmentRegisterBinding
import com.ethiouber.driver.utils.Extensions.hide
import com.ethiouber.driver.utils.Extensions.show
import com.ethiouber.driver.utils.Extensions.toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by activityViewModels()

    // Step tracking (1 = personal info, 2 = vehicle info, 3 = documents)
    private var currentStep = 1

    private var nationalIdFile: File? = null
    private var licenseFile: File? = null
    private var vehiclePhotoFile: File? = null

    private var currentPhotoTarget: String = ""

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            when (currentPhotoTarget) {
                "national_id" -> {
                    nationalIdFile = uriToFile(uri, "national_id.jpg")
                    Glide.with(this).load(uri).into(binding.ivNationalId)
                    binding.tvNationalIdFileName.text = "national_id.jpg"
                }
                "license" -> {
                    licenseFile = uriToFile(uri, "driver_license.jpg")
                    Glide.with(this).load(uri).into(binding.ivLicense)
                    binding.tvLicenseFileName.text = "driver_license.jpg"
                }
                "vehicle_photo" -> {
                    vehiclePhotoFile = uriToFile(uri, "vehicle_photo.jpg")
                    Glide.with(this).load(uri).into(binding.ivVehiclePhoto)
                    binding.tvVehiclePhotoFileName.text = "vehicle_photo.jpg"
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupVehicleTypeSpinner()
        showStep(1)
        setupListeners()
        observeRegisterState()
    }

    private fun setupVehicleTypeSpinner() {
        val vehicleTypes = VehicleType.getAllTypes().map { VehicleType.getDisplayName(it) }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, vehicleTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerVehicleType.adapter = adapter
    }

    private fun showStep(step: Int) {
        currentStep = step
        binding.stepLayout1.showIf(step == 1)
        binding.stepLayout2.showIf(step == 2)
        binding.stepLayout3.showIf(step == 3)
        binding.tvStep.text = "Step $step of 3"
        binding.progressStep.progress = step * 33

        binding.btnBack.showIf(step > 1)
        binding.btnNext.text = if (step == 3) "Register" else "Next"
    }

    private fun View.showIf(condition: Boolean) {
        visibility = if (condition) View.VISIBLE else View.GONE
    }

    private fun setupListeners() {
        binding.btnNext.setOnClickListener {
            when (currentStep) {
                1 -> validateAndMoveToStep2()
                2 -> validateAndMoveToStep3()
                3 -> submitRegistration()
            }
        }

        binding.btnBack.setOnClickListener {
            if (currentStep > 1) showStep(currentStep - 1)
        }

        binding.tvLogin.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }

        binding.btnUploadNationalId.setOnClickListener {
            currentPhotoTarget = "national_id"
            openImagePicker()
        }

        binding.btnUploadLicense.setOnClickListener {
            currentPhotoTarget = "license"
            openImagePicker()
        }

        binding.btnUploadVehiclePhoto.setOnClickListener {
            currentPhotoTarget = "vehicle_photo"
            openImagePicker()
        }
    }

    private fun validateAndMoveToStep2() {
        val fullName = binding.etFullName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        binding.tilFullName.error = null
        binding.tilPhone.error = null
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null

        when {
            fullName.isEmpty() -> binding.tilFullName.error = "Full name is required"
            fullName.length < 3 -> binding.tilFullName.error = "Enter a valid full name"
            phone.isEmpty() -> binding.tilPhone.error = "Phone number is required"
            phone.length < 9 -> binding.tilPhone.error = "Enter a valid phone number"
            password.isEmpty() -> binding.tilPassword.error = "Password is required"
            password.length < 8 -> binding.tilPassword.error = "Password must be at least 8 characters"
            confirmPassword != password -> binding.tilConfirmPassword.error = "Passwords do not match"
            else -> showStep(2)
        }
    }

    private fun validateAndMoveToStep3() {
        val nationalId = binding.etNationalId.text.toString().trim()
        val licenseNumber = binding.etLicenseNumber.text.toString().trim()
        val vehiclePlate = binding.etVehiclePlate.text.toString().trim()
        val vehicleModel = binding.etVehicleModel.text.toString().trim()

        binding.tilNationalId.error = null
        binding.tilLicenseNumber.error = null
        binding.tilVehiclePlate.error = null
        binding.tilVehicleModel.error = null

        when {
            nationalId.isEmpty() -> binding.tilNationalId.error = "National ID is required"
            licenseNumber.isEmpty() -> binding.tilLicenseNumber.error = "License number is required"
            vehiclePlate.isEmpty() -> binding.tilVehiclePlate.error = "Vehicle plate is required"
            vehicleModel.isEmpty() -> binding.tilVehicleModel.error = "Vehicle model is required"
            else -> showStep(3)
        }
    }

    private fun submitRegistration() {
        val nationalId = nationalIdFile
        val license = licenseFile
        val vehiclePhoto = vehiclePhotoFile

        when {
            nationalId == null -> toast("Please upload your National ID photo")
            license == null -> toast("Please upload your Driver's License photo")
            vehiclePhoto == null -> toast("Please upload your Vehicle photo")
            else -> {
                val vehicleTypes = VehicleType.getAllTypes()
                val selectedVehicleType = vehicleTypes[binding.spinnerVehicleType.selectedItemPosition]

                viewModel.registerDriver(
                    fullName = binding.etFullName.text.toString().trim(),
                    phone = binding.etPhone.text.toString().trim(),
                    password = binding.etPassword.text.toString(),
                    nationalIdNumber = binding.etNationalId.text.toString().trim(),
                    licenseNumber = binding.etLicenseNumber.text.toString().trim(),
                    vehicleType = selectedVehicleType,
                    vehiclePlate = binding.etVehiclePlate.text.toString().trim().uppercase(),
                    vehicleModel = binding.etVehicleModel.text.toString().trim(),
                    nationalIdFile = nationalId,
                    licenseFile = license,
                    vehiclePhotoFile = vehiclePhoto
                )
            }
        }
    }

    private fun observeRegisterState() {
        lifecycleScope.launch {
            viewModel.registerState.collect { state ->
                when (state) {
                    is AuthUiState.Loading -> {
                        binding.progressBar.show()
                        binding.btnNext.isEnabled = false
                    }
                    is AuthUiState.Success -> {
                        binding.progressBar.hide()
                        binding.btnNext.isEnabled = true
                        viewModel.pendingPhone = binding.etPhone.text.toString().trim()
                        toast("Registration successful! Please verify your phone number.")
                        findNavController().navigate(R.id.action_registerFragment_to_otpFragment)
                        viewModel.resetRegisterState()
                    }
                    is AuthUiState.Error -> {
                        binding.progressBar.hide()
                        binding.btnNext.isEnabled = true
                        toast(state.message)
                        viewModel.resetRegisterState()
                    }
                    is AuthUiState.Idle -> {
                        binding.progressBar.hide()
                        binding.btnNext.isEnabled = true
                    }
                }
            }
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
        photoPickerLauncher.launch(intent)
    }

    private fun uriToFile(uri: Uri, fileName: String): File {
        val file = File(requireContext().cacheDir, fileName)
        requireContext().contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
