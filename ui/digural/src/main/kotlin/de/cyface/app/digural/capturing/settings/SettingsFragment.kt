/*
 * Copyright 2023-2024 Cyface GmbH
 *
 * This file is part of the Cyface App for Android.
 *
 * The Cyface App for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface App for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface App for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.app.digural.capturing.settings

import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import de.cyface.app.digural.CameraServiceProvider
import de.cyface.app.digural.Application
import de.cyface.app.digural.databinding.FragmentSettingsBinding
import de.cyface.app.digural.dialog.ExposureTimeDialog
import de.cyface.app.digural.dialog.ExposureTimeDialog.Companion.CAMERA_STATIC_EXPOSURE_TIME_KEY
import de.cyface.app.utils.ServiceProvider
import de.cyface.camera_service.CameraInfo
import de.cyface.camera_service.Utils
import de.cyface.camera_service.background.camera.CameraModeDialog
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.utils.Validate
import java.util.TreeMap
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The [Fragment] which shows the settings to the user.
 *
 * @author Armin Schnabel
 */
class SettingsFragment : Fragment() {

    /**
     * This property is only valid between onCreateView and onDestroyView.
     */
    private var _binding: FragmentSettingsBinding? = null

    /**
     * The generated class which holds all bindings from the layout file.
     */
    private val binding get() = _binding!!

    /**
     * The capturing service object which controls data capturing and synchronization.
     */
    private lateinit var capturing: CyfaceDataCapturingService

    /**
     * The [SettingsViewModel] for this fragment.
     */
    internal lateinit var viewModel: SettingsViewModel

    /**
     * Can be launched to request permissions.
     */
    var permissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result.isNotEmpty()) {
                val allGranted = result.values.none { !it }
                if (allGranted) {
                    showCameraModeDialog(this)
                } else {
                    Toast.makeText(
                        context,
                        requireContext().getString(de.cyface.camera_service.R.string.camera_service_off_missing_permissions),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get dependencies
        if (activity is ServiceProvider) {
            capturing = (activity as ServiceProvider).capturing
        } else {
            throw RuntimeException("Context does not support the Fragment, implement ServiceProvider")
        }
        val cameraSettings =
            if (activity is CameraServiceProvider) {
                (activity as CameraServiceProvider).cameraSettings
            } else {
                throw RuntimeException("Context doesn't support the Fragment, implement `CameraServiceProvider`")
            }
        val customSettings: CustomSettings =
            if (activity is SettingsProvider) {
                (activity as SettingsProvider).customSettings
            } else {
                throw RuntimeException("Context doesn't support the Fragment, implement `CustomProvider`")
            }

        // Initialize ViewModel
        viewModel = ViewModelProvider(
            this,
            SettingsViewModelFactory(Application.appSettings, cameraSettings, customSettings)
        )[SettingsViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        // Observe UI changes
        /** app settings **/
        binding.centerMapSwitch.setOnCheckedChangeListener(
            CenterMapSwitchHandler(
                viewModel,
                context
            )
        )
        binding.uploadSwitch.setOnCheckedChangeListener(
            UploadSwitchHandler(
                viewModel,
                context,
                capturing
            )
        )
        binding.sensorFrequencySlider.addOnChangeListener(
            SensorFrequencySlideHandler(
                viewModel
            )
        )
        /** camera settings **/
        binding.cameraEnabledSwitch.setOnCheckedChangeListener(
            CameraSwitchHandler(
                viewModel,
                this
            )
        )
        binding.distanceBasedSwitcher.setOnCheckedChangeListener(
            DistanceBasedSwitchHandler(
                requireContext(),
                viewModel
            )
        )
        binding.distanceBasedSlider.addOnChangeListener(
            TriggerDistanceSlideHandler(viewModel)
        )
        binding.distanceBasedUnit.text = TRIGGER_DISTANCE_UNIT
        binding.staticFocusSwitcher.setOnCheckedChangeListener(
            StaticFocusSwitchHandler(
                requireContext(),
                viewModel
            )
        )
        binding.staticFocusDistanceSlider.addOnChangeListener(
            StaticFocusDistanceSlideHandler(viewModel)
        )
        // In case we have a device which does not support focus distance 0.25:
        // triggerDistanceSlider.setValueTo(minFocusDistance);
        binding.staticExposureTimeSwitcher.setOnCheckedChangeListener(
            StaticExposureSwitchHandler(
                viewModel,
                requireContext()
            )
        )
        binding.staticExposureTime.setOnClickListener(
            StaticExposureTimeClickHandler(
                parentFragmentManager,
                this
            )
        )
        binding.staticExposureTimeUnit.text = EXPOSURE_TIME_UNIT
        // Static Exposure Value (Dialog)
        binding.staticExposureValueSlider.valueFrom = EXPOSURE_VALUES.firstKey()!!.toFloat()
        binding.staticExposureValueSlider.valueTo = EXPOSURE_VALUES.lastKey()!!.toFloat()
        // The exposure value slider is not useful for a set of predefined values like 1E9/54444, 1/8000, 1/125
        /*val exposureTimeRangeNanos =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        if (exposureTimeRangeNanos != null) { // when camera permissions are missing
            // Limit maximal time to 1/100s for easy motion-blur-reduced ranges: 1/125s and less
            val toValue = /*10_000_000*/ /*10_000_000L.coerceAtMost(*/
                exposureTimeRangeNanos.upper//)
            val fromValue = /*1_250_000*/exposureTimeRangeNanos.lower
            binding.staticExposureTimeSlider.valueTo = toValue.toFloat()
            binding.staticExposureTimeSlider.valueFrom = fromValue.toFloat()
            Log.d(TAG,"exposureTimeRange: $exposureTimeRangeNanos ns -> set slide range: $fromValue - $toValue")
        }
        */
        binding.staticExposureValueSlider.addOnChangeListener(
            StaticExposureValueSlideHandler(
                viewModel
            )
        )
        /** custom settings **/
        binding.diguralServerAddressWrapper.setEndIconOnClickListener(
            DiguralUrlChangeHandler(
                viewModel,
                this,
                binding.diguralServerAddress
            )
        )

        // Observe view model, update UI
        /** app settings **/
        viewModel.centerMap.observe(viewLifecycleOwner) { centerMapValue ->
            run {
                binding.centerMapSwitch.isChecked = centerMapValue!!
            }
        }
        viewModel.uploadEnabled.observe(viewLifecycleOwner) { uploadValue ->
            run {
                binding.uploadSwitch.isChecked = uploadValue!!
            }
        }
        viewModel.sensorFrequency.observe(viewLifecycleOwner) { sensorFrequencyValue ->
            run {
                binding.sensorFrequencySlider.value = sensorFrequencyValue.toFloat()
                binding.sensorFrequency.text = sensorFrequencyValue.toString()
            }
        }
        /** camera settings **/
        viewModel.cameraEnabled.observe(viewLifecycleOwner) { cameraEnabled ->
            run {
                binding.cameraEnabledSwitch.isChecked = cameraEnabled
                binding.cameraSettingsWrapper.visibility = if (cameraEnabled) VISIBLE else GONE

                // Manual Sensor support and features
                if (cameraEnabled) {
                    val cameraInfo = CameraInfo(requireContext())
                    viewModel.manualSensorSupported = cameraInfo.manualSensorSupported

                    if (cameraInfo.manualSensorSupported) {
                        setManualSensorSupport(cameraInfo)
                    } else {
                        //binding.staticFocusDistanceSlider.visibility = INVISIBLE
                        binding.staticFocusWrapper.visibility = INVISIBLE
                        // binding.staticExposureTimeSlider.setVisibility(View.INVISIBLE);
                        // binding.staticExposureValueSlider.visibility = INVISIBLE
                        binding.staticExposureTimeWrapper.visibility = INVISIBLE
                        binding.staticExposureValueWrapper.visibility = INVISIBLE
                    }
                }
            }
        }
        viewModel.videoMode.observe(viewLifecycleOwner) { videoMode ->
            if (videoMode) {
                binding.cameraMode.text = getText(de.cyface.camera_service.R.string.video)
            } else if (viewModel.rawMode.value == false) {
                binding.cameraMode.text =
                    getText(de.cyface.camera_service.R.string.compressed_images)
            }
        }
        viewModel.rawMode.observe(viewLifecycleOwner) { rawMode ->
            if (rawMode) {
                binding.cameraMode.text =
                    getText(de.cyface.camera_service.R.string.uncompressed_images)
            } else if (viewModel.videoMode.value == false) {
                binding.cameraMode.text =
                    getText(de.cyface.camera_service.R.string.compressed_images)
            }
        }
        viewModel.distanceBasedTriggering.observe(viewLifecycleOwner) { distanceBased ->
            run {
                binding.distanceBasedSwitcher.isChecked = distanceBased
                binding.distanceBasedWrapper.visibility = if (distanceBased) VISIBLE else INVISIBLE
            }
        }
        viewModel.triggeringDistance.observe(viewLifecycleOwner) { triggeringDistance ->
            run {
                val roundedDistance = (triggeringDistance * 100).roundToInt() / 100f
                Log.d(TAG, "updateView -> triggering distance to $roundedDistance")
                binding.distanceBasedSlider.value = roundedDistance

                val text = StringBuilder(roundedDistance.toString())
                while (text.length < 4) {
                    text.append("0")
                }
                binding.distanceBased.text = text
            }
        }
        viewModel.staticFocus.observe(viewLifecycleOwner) { staticFocus ->
            run {
                Log.d(TAG, "updateView -> static focus to $staticFocus")
                binding.staticFocusSwitcher.isChecked = staticFocus
                binding.staticFocusWrapper.visibility = if (staticFocus) VISIBLE else INVISIBLE
            }
        }
        viewModel.staticFocusDistance.observe(viewLifecycleOwner) { distance ->
            run {
                val roundedDistance = (distance * 100).roundToInt() / 100f
                Log.d(TAG, "updateView -> focus distance to $roundedDistance")
                binding.staticFocusDistanceSlider.value = roundedDistance

                val text = StringBuilder(roundedDistance.toString())
                while (text.length < 4) {
                    text.append("0")
                }
                binding.staticFocus.text = text
            }
        }
        viewModel.staticExposure.observe(viewLifecycleOwner) { staticExposure ->
            run {
                Log.d(TAG, "updateView -> static exposure to $staticExposure")
                binding.staticExposureTimeSwitcher.isChecked = staticExposure
                val visibility = if (staticExposure) VISIBLE else INVISIBLE
                binding.staticExposureTimeWrapper.visibility = visibility
                binding.staticExposureValueWrapper.visibility = visibility
            }
        }
        viewModel.staticExposureTime.observe(viewLifecycleOwner) { exposureTime ->
            run {
                Log.d(TAG, "updateView -> exposure time to $exposureTime")
                binding.staticExposureTime.text = Utils.getExposureTimeFraction(exposureTime)
            }
        }
        viewModel.staticExposureValue.observe(viewLifecycleOwner) { exposureValue ->
            run {
                Log.d(TAG, "updateView -> exposure value to $exposureValue")
                binding.staticExposureValueSlider.value = exposureValue.toFloat()
                binding.staticExposureValue.text = exposureValue.toString()
                binding.staticExposureValueDescription.text = EXPOSURE_VALUES[exposureValue]
            }
        }
        /** custom settings **/
        viewModel.diguralServerUrl.observe(viewLifecycleOwner) { serverAddress ->
            run {
                binding.diguralServerAddress.setText(serverAddress.toExternalForm())
            }
        }

        return binding.root
    }

    private fun setManualSensorSupport(cameraInfo: CameraInfo) {
        displaySupportLevelsAndUnits(cameraInfo)

        // is null when camera permissions are missing, is 0.0 when "lens is fixed-focus" (e.g. emulators)
        // It's ok when this is not set in those cases as the fragment informs about missing manual focus mode
        if (cameraInfo.minFocusDistance != null && cameraInfo.minFocusDistance != 0.0f) {
            // Flooring to the next smaller 0.25 step as max focus distance to fix exception:
            // stepSize(0.25) must be a factor of range [0.25;9.523809] on Pixel 6
            binding.staticFocusDistanceSlider.valueTo = floor(cameraInfo.minFocusDistance!! * 4) / 4
        }
    }

    /**
     * Displays a dialog for the user to select a camera mode (video- or picture mode).
     */
    fun showCameraModeDialog(fragment: SettingsFragment) {
        val cameraModeDialog = CameraModeDialog(viewModel.cameraSettings, lifecycleScope)
        cameraModeDialog.setTargetFragment(fragment, 0)
        cameraModeDialog.isCancelable = false
        cameraModeDialog.show(requireFragmentManager(), "CAMERA_MODE_DIALOG")
    }

    /**
     * Displays the supported camera2 features in the view.
     *
     * @param cameraInfo The hardware info about the camera available for capturing.
     */
    private fun displaySupportLevelsAndUnits(cameraInfo: CameraInfo) {
        binding.hardwareSupport.text = cameraInfo.supportedHardwareLevel

        binding.manualSensorSupport.text =
            if (cameraInfo.manualSensorSupported) "supported" else "no supported"

        val isFocusCalibrated = cameraInfo.focusDistanceCalibration != FOCUS_DISTANCE_NOT_CALIBRATED
        binding.focusDistance.text = cameraInfo.focusDistanceCalibration
        binding.staticFocusUnit.text = if (isFocusCalibrated) "dioptre" else "uncalibrated"

        // Display focus distance ranges
        binding.hyperFocalDistance.text =
            "${cameraInfo.hyperFocalDistance}${if (isFocusCalibrated) " [dioptre]" else ""}"
        binding.minimumFocusDistance.text =
            "${cameraInfo.minFocusDistance}${if (isFocusCalibrated) " [dioptre]" else ""}"
    }

    /**
     * Returns the hardware calibration of the focus distance feature as simple `String`.
     *
     * If "uncalibrated" the focus distance does not have a unit, else it's in dioptre [1/m], more details:
     * https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#LENS_INFO_FOCUS_DISTANCE_CALIBRATION
     *
     * @param characteristics the hardware feature set to check
     * @return the calibration level as `String`
     */
    private fun getFocusDistanceCalibration(characteristics: CameraCharacteristics): String {
        val focusDistanceCalibration = characteristics
            .get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION) ?: return "N/A"
        return when (focusDistanceCalibration) {
            CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED -> "calibrated"
            CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE -> "approximate"
            CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED -> FOCUS_DISTANCE_NOT_CALIBRATED
            else -> "unknown: $focusDistanceCalibration"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    /**
     * Called when an exposure time was selected in the [ExposureTimeDialog].
     *
     * @param data an intent which may contain result data
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DIALOG_EXPOSURE_TIME_SELECTION_REQUEST_CODE) {
            val nanos = data!!.getLongExtra(CAMERA_STATIC_EXPOSURE_TIME_KEY, -1L)
            Validate.isTrue(nanos != -1L)
            val fraction = Utils.getExposureTimeFraction(nanos)
            Log.d(TAG, "Update view -> exposure time to $nanos ns - fraction: $fraction s")
            binding.staticExposureTime.text = fraction
        }
    }

    companion object {
        /**
         * The tag used to identify logging from this class.
         */
        const val TAG = de.cyface.app.digural.utils.Constants.PACKAGE + ".s"

        /**
         * The identifier for the [ExposureTimeDialog] request.
         */
        const val DIALOG_EXPOSURE_TIME_SELECTION_REQUEST_CODE = 202002170

        /**
         * The {@code String} which represents the hardware camera focus distance calibration level.
         */
        private const val FOCUS_DISTANCE_NOT_CALIBRATED = "uncalibrated"

        /**
         * The unit of the [.staticExposureTimePreference] value shown in the **UI**.
         */
        private const val EXPOSURE_TIME_UNIT = "s (click on time to change)"

        /**
         * The unit of the [.triggerDistancePreference] value shown in the **UI**.
         */
        private const val TRIGGER_DISTANCE_UNIT = "m"

        /**
         * Exposure value from tabulates values (for iso 100) for outdoor environment light settings
         * see https://en.wikipedia.org/wiki/Exposure_value#Tabulated_exposure_values
         */
        val EXPOSURE_VALUES: TreeMap<Int?, String?> = object : TreeMap<Int?, String?>() {
            init {
                put(10, "twilight")
                put(11, "twilight")
                put(12, "deep shade")
                put(13, "cloudy, no shadows")
                put(14, "partly cloudy, soft shadows")
                put(15, "full sunlight")
                put(16, "sunny, snowy/sandy")
            }
        }
    }
}
