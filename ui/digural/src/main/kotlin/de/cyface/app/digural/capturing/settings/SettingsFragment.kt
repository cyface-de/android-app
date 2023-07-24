/*
 * Copyright 2023 Cyface GmbH
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

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import de.cyface.app.digural.databinding.FragmentSettingsBinding
import de.cyface.app.digural.dialog.ExposureTimeDialog
import de.cyface.app.utils.ServiceProvider
import de.cyface.camera_service.CameraModeDialog
import de.cyface.camera_service.CameraPreferences
import de.cyface.camera_service.Constants
import de.cyface.camera_service.Utils
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.utils.AppPreferences
import de.cyface.utils.Validate
import java.util.TreeMap
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The [Fragment] which shows the settings to the user.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 3.2.0
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
    private lateinit var viewModel: SettingsViewModel

    /**
     * Can be launched to request permissions.
     */
    var permissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            /*val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED
            val unexpectedPermissionNumber = grantResults.size < 2
            val missingPermissions =
                !(granted && (unexpectedPermissionNumber || (grantResults[1] == PackageManager.PERMISSION_GRANTED)))*/

            if (result.isNotEmpty()) {
                val allGranted = result.values.none { !it }
                if (allGranted /*!missingPermissions*/) {
                    // Ask used which camera mode to use, video or default (shutter image)
                    onCameraEnabled(this)
                } else {
                    // Deactivate camera service and inform user about this
                    disabledCamera()
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

        // Initialize ViewModel
        val appPreferences = AppPreferences(requireContext().applicationContext)
        val cameraPreferences = CameraPreferences(requireContext().applicationContext)
        viewModel = ViewModelProvider(
            this,
            SettingsViewModelFactory(appPreferences, cameraPreferences)
        )[SettingsViewModel::class.java]

        // Initialize CapturingService
        if (activity is ServiceProvider) {
            capturing = (activity as ServiceProvider).capturing
        } else {
            throw RuntimeException("Context does not support the Fragment, implement ServiceProvider")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        // Register onClick listeners
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
                viewModel,
                binding.sensorFrequency
            )
        )
        /** camera settings **/
        binding.cameraSwitch.setOnCheckedChangeListener(
            CameraSwitchHandler(
                viewModel,
                this
            )
        )

        // Observe view model and update UI
        viewModel.centerMap.observe(viewLifecycleOwner) { centerMapValue ->
            run {
                binding.centerMapSwitch.isChecked = centerMapValue!!
            }
        }
        viewModel.upload.observe(viewLifecycleOwner) { uploadValue ->
            run {
                binding.uploadSwitch.isChecked = uploadValue!!
            }
        }
        viewModel.sensorFrequency.observe(viewLifecycleOwner) { sensorFrequencyValue ->
            run {
                Log.d(TAG, "updateView -> sensor frequency slider $sensorFrequencyValue")
                binding.sensorFrequency.text = sensorFrequencyValue.toString()
            }
        }
        viewModel.cameraEnabled.observe(viewLifecycleOwner) { cameraEnabledValue ->
            run {
                binding.cameraSwitch.isChecked = cameraEnabledValue!!
                if (cameraEnabledValue) {
                    updateCameraSettingsView(true) // FIXME: Refactor

                    updateDistanceBasedTriggeringViewToPreference() // FIXME: Refactor

                    // FIXME: Refactor
                    // Only check manual sensor settings if it's supported or else the app crashes
                    if (viewModel.manualSensorSupported) {
                        updateManualSensorViewToPreferences()
                    } else {
                        binding.staticFocusDistanceSlider.visibility = View.INVISIBLE
                        // staticExposureTimeSlider.setVisibility(View.INVISIBLE);
                        binding.staticExposureValueSlider.visibility = View.INVISIBLE
                    }
                } else {
                    updateCameraSettingsView(false)
                }
            }
        }

        // FIXME: Observe cameraMode (enum text) and update the camera mode text view
        // Update camera enabled and mode status view if incorrect
        /*
        val videoModePreferred = preferences.getBoolean(
            Constants.PREFERENCES_CAMERA_VIDEO_MODE_ENABLED_KEY,
            false
        )
        val rawModePreferred = preferences.getBoolean(
            Constants.PREFERENCES_CAMERA_RAW_MODE_ENABLED_KEY,
            false
        )
        val cameraModeText =
            if (videoModePreferred) "Video" else if (rawModePreferred) "DNG" else "JPEG"
        val cameraStatusText =
            if (!cameraModeEnabledPreferred) "disabled" else "enabled, $cameraModeText mode"
        if (binding.cameraStatus.text !== cameraStatusText) {
            Log.d(TAG, "updateView -> camera mode view $cameraStatusText")
            binding.cameraStatus.text = cameraStatusText
        }*/

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /*override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>, grantResults: IntArray
    ) {
        when (requestCode) {
            // Location permission request moved to `MapFragment` as it has to react to results

            PERMISSION_REQUEST_CAMERA_AND_STORAGE_PERMISSION -> {
                val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED
                val unexpectedPermissionNumber = grantResults.size < 2
                val missingPermissions =
                    !(granted && (unexpectedPermissionNumber || (grantResults[1] == PackageManager.PERMISSION_GRANTED)))

                if (missingPermissions) {
                    // Deactivate camera service and inform user about this
                    deactivateCameraService()
                    Toast.makeText(
                        context,
                        requireContext().getString(de.cyface.camera_service.R.string.camera_service_off_missing_permissions),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    // Ask used which camera mode to use, video or default (shutter image)
                    showCameraModeDialog(this)
                }

            } else -> {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
        }
    }*/

    /**
     * Displays a dialog for the user to select a camera mode (video- or picture mode).
     */
    fun onCameraEnabled(fragment: SettingsFragment) {

        // Ask for camera mode
        val cameraModeDialog = CameraModeDialog()
        cameraModeDialog.setTargetFragment(
            fragment,
            de.cyface.energy_settings.Constants.DIALOG_ENERGY_SAFER_WARNING_CODE
        )
        cameraModeDialog.isCancelable = false
        cameraModeDialog.show(requireFragmentManager(), "CAMERA_MODE_DIALOG")

        updateCameraSettingsView(true)
    }

    private fun disabledCamera() {
        //binding.cameraSwitch.isChecked = false    // Should be done via observe
        viewModel.setCameraEnabled(false)
        onCameraDisabled()
    }

    private fun onCameraDisabled() {
    }

    private fun updateCameraSettingsView(cameraEnabled: Boolean) {
        if (!cameraEnabled) {
            binding.cameraSettingsWrapper.visibility = View.GONE
            return
        }
        binding.cameraSettingsWrapper.visibility = View.VISIBLE

        // Set manual sensor support
        val characteristics = loadCameraCharacteristics()
        viewModel.manualSensorSupported = Utils.isFeatureSupported(
            characteristics,
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
        )

        // Static Focus Distance
        binding.staticFocusSwitcher.setOnCheckedChangeListener(
            StaticFocusSwitchHandler(
                requireContext(),
                viewModel,
                binding.staticFocusSwitcher,
                binding.staticFocusDistanceSlider,
                binding.staticFocus,
                binding.staticFocusUnit
            )
        )
        binding.staticFocusDistanceSlider.addOnChangeListener(
            StaticFocusDistanceSlideHandler(viewModel, binding.staticFocus)
        )
        val minFocusDistance =
            characteristics!!.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
        // is null when camera permissions are missing, is 0.0 when "lens is fixed-focus" (e.g. emulators)
        // It's ok when this is not set in those cases as the fragment informs about missing manual focus mode
        if (minFocusDistance != null && minFocusDistance.toDouble() != 0.0) {
            // Flooring to the next smaller 0.25 step as max focus distance to fix exception:
            // stepSize(0.25) must be a factor of range [0.25;9.523809] on Pixel 6
            binding.staticFocusDistanceSlider.valueTo = floor(minFocusDistance * 4) / 4
        }

        // Distance based triggering
        binding.distanceBasedSwitcher.setOnCheckedChangeListener(
            DistanceBasedSwitchHandler(
                requireContext(),
                viewModel,
                binding.distanceBasedSlider,
                binding.distanceBased,
                binding.distanceBasedUnit
            )
        )
        binding.distanceBasedSlider.addOnChangeListener(
            TriggerDistanceSlideHandler(viewModel, binding.distanceBased)
        )
        // triggerDistanceSlider.setValueTo(minFocusDistance);
        binding.distanceBasedUnit.text = TRIGGER_DISTANCE_UNIT

        // Static Exposure Time
        binding.staticExposureTimeSwitcher.setOnCheckedChangeListener(
            StaticExposureSwitchHandler(
                viewModel,
                requireContext(),
                binding.staticExposureTimeSwitcher,
                binding.staticExposureTime,
                binding.staticExposureTimeUnit,
                binding.staticExposureValueTitle,
                binding.staticExposureValueSlider,
                binding.staticExposureValue,
                binding.staticExposureValueDescription
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
        binding.staticExposureValueSlider.addOnChangeListener(
            StaticExposureValueSlideHandler(
                viewModel,
                binding.staticExposureValue,
                binding.staticExposureValueDescription
            )
        )
        binding.staticExposureValueSlider.valueFrom =
            EXPOSURE_VALUES.firstKey()!!.toFloat()
        binding.staticExposureValueSlider.valueTo =
            EXPOSURE_VALUES.lastKey()!!.toFloat()

        // The slider is not useful for a set of predefined values like 1E9/54444, 1/8000, 1/125
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

        // Show supported camera features (camera permissions required)
        displaySupportLevelsAndUnits(
            characteristics,
            viewModel.manualSensorSupported,
            minFocusDistance!!
        )
    }

    private fun updateDistanceBasedTriggeringViewToPreference() {
        // Update slider view if incorrect
        val preferredTriggerDistance = viewModel.triggeringDistance.value
        val roundedDistance = (preferredTriggerDistance!! * 100).roundToInt() / 100f
        if (binding.distanceBasedSlider.value != roundedDistance) {
            Log.d(TAG, "updateView -> triggering distance slider $roundedDistance")
            binding.distanceBasedSlider.value = roundedDistance
        }
        if (binding.distanceBased.text.isEmpty()
            || binding.distanceBased.text.toString().toFloat() != roundedDistance
        ) {
            Log.d(TAG, "updateView -> triggering distance text $roundedDistance")
            binding.distanceBased.text = roundedDistance.toString()
        }

        // Update switcher view if incorrect
        val distanceBasedTriggeringPreferred = viewModel.distanceBasedTriggering.value
        if (binding.distanceBasedSwitcher.isChecked != distanceBasedTriggeringPreferred) {
            Log.d(TAG, "distance based triggering -> $distanceBasedTriggeringPreferred")
            binding.distanceBasedSwitcher.isChecked = distanceBasedTriggeringPreferred!!
        }

        // Update visibility slider + distance field
        val expectedTriggerDistanceVisibility =
            if (distanceBasedTriggeringPreferred) View.VISIBLE else View.INVISIBLE
        Log.d(
            TAG,
            "view status -> distance triggering fields are " + binding.distanceBasedSlider.visibility
                    + "expected is: "
                    + if (expectedTriggerDistanceVisibility == View.VISIBLE) "visible" else "invisible"
        )
        if (binding.distanceBasedSlider.visibility != expectedTriggerDistanceVisibility || binding.distanceBased.visibility != expectedTriggerDistanceVisibility || binding.distanceBasedUnit.visibility != expectedTriggerDistanceVisibility) {
            Log.d(
                TAG, "updateView -> distance triggering fields to "
                        + if (expectedTriggerDistanceVisibility == View.VISIBLE) "visible" else "invisible"
            )
            binding.distanceBasedSlider.visibility = expectedTriggerDistanceVisibility
            binding.distanceBased.visibility = expectedTriggerDistanceVisibility
            binding.distanceBasedUnit.visibility = expectedTriggerDistanceVisibility
        }
    }


    /**
     * Returns the features supported by the camera hardware.
     *
     * @return The hardware feature support
     */
    private fun loadCameraCharacteristics(): CameraCharacteristics? {
        val cameraManager =
            requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            Validate.notNull(cameraManager)
            val cameraId = Utils.getMainRearCameraId(cameraManager, null)
            cameraManager.getCameraCharacteristics(cameraId)
        } catch (e: CameraAccessException) {
            throw IllegalStateException(e)
        }
    }

    private fun updateManualSensorViewToPreferences() {
        // Update focus distance slider view if incorrect
        val preferredFocusDistance = viewModel.staticFocusDistance.value!!
        val roundedDistance = (preferredFocusDistance * 100).roundToInt() / 100f
        if (binding.staticFocusDistanceSlider.value != roundedDistance) {
            Log.d(TAG, "updateView -> focus distance slider $roundedDistance")
            binding.staticFocusDistanceSlider.value = roundedDistance
        }
        if (binding.staticFocus.text.isEmpty()
            || binding.staticFocus.text.toString().toFloat() != roundedDistance
        ) {
            Log.d(TAG, "updateView -> focus distance text $roundedDistance")
            binding.staticFocus.text = roundedDistance.toString()
        }

        // Update exposure time slider view if incorrect
        val preferredExposureTimeNanos = viewModel.staticExposureTime.value!!
        /*
         * if (staticExposureTimeSlider.getValue() != preferredExposureTimeNanos) {
         * Log.d(TAG, "updateView -> exposure time slider " + preferredExposureTimeNanos);
         * staticExposureTimeSlider.setValue(preferredExposureTimeNanos);
         * }
        */if (binding.staticExposureTime.text.isEmpty()
            || binding.staticExposureTime.text.toString() != Utils.getExposureTimeFraction(
                preferredExposureTimeNanos
            )
        ) {
            Log.d(TAG, "updateView -> exposure time text $preferredExposureTimeNanos")
            binding.staticExposureTime.text = Utils.getExposureTimeFraction(
                preferredExposureTimeNanos
            )
        }

        // Update exposure value slider view if incorrect
        val preferredExposureValue = viewModel.staticExposureValue.value!!
        if (binding.staticExposureValueSlider.value != preferredExposureValue.toFloat()) {
            Log.d(TAG, "updateView -> exposure value slider $preferredExposureValue")
            binding.staticExposureValueSlider.value = preferredExposureValue.toFloat()
        }
        if (binding.staticExposureValue.text.isEmpty() || binding.staticExposureValue.text.toString()
                .toInt() != preferredExposureValue
        ) {
            Log.d(TAG, "updateView -> exposure value text $preferredExposureValue")
            binding.staticExposureValue.text = preferredExposureValue.toString()
        }
        // Update exposure value description view if incorrect
        val expectedExposureValueDescription = EXPOSURE_VALUES[preferredExposureValue]
        if (binding.staticExposureValueDescription.text != expectedExposureValueDescription) {
            Log.d(TAG, "exposure value description -> $expectedExposureValueDescription")
            binding.staticExposureValueDescription.text = expectedExposureValueDescription
        }

        // Update focus distance switcher view if incorrect
        val focusDistancePreferred = viewModel.staticFocus.value!!
        if (binding.staticFocusSwitcher.isChecked != focusDistancePreferred) {
            Log.d(TAG, "updateView focus distance switcher -> $focusDistancePreferred")
            binding.staticFocusSwitcher.isChecked = focusDistancePreferred
        }

        // Update exposure time switcher view if incorrect
        val exposureTimePreferred = viewModel.staticExposure.value!!
        if (binding.staticExposureTimeSwitcher.isChecked != exposureTimePreferred) {
            Log.d(TAG, "updateView exposure time switcher -> $exposureTimePreferred")
            binding.staticExposureTimeSwitcher.isChecked = exposureTimePreferred
        }

        // Update visibility of focus distance slider + distance field
        val expectedFocusDistanceVisibility =
            if (focusDistancePreferred) View.VISIBLE else View.INVISIBLE
        if (binding.staticFocusDistanceSlider.visibility != expectedFocusDistanceVisibility ||
            binding.staticFocus.visibility != expectedFocusDistanceVisibility ||
            binding.staticFocusUnit.visibility != expectedFocusDistanceVisibility
        ) {
            Log.d(
                TAG, "updateView -> focus distance fields to "
                        + if (expectedFocusDistanceVisibility == View.VISIBLE) "visible" else "invisible"
            )
            binding.staticFocusDistanceSlider.visibility = expectedFocusDistanceVisibility
            binding.staticFocus.visibility = expectedFocusDistanceVisibility
            binding.staticFocusUnit.visibility = expectedFocusDistanceVisibility
        }

        // Update visibility of exposure time and value slider + time and value fields
        val expectedExposureTimeVisibility =
            if (exposureTimePreferred) View.VISIBLE else View.INVISIBLE
        if ( /*
     * binding.staticExposureTimeSlider.getVisibility() != expectedExposureTimeVisibility
     * ||
    */binding.staticExposureTime.visibility != expectedExposureTimeVisibility || binding.staticExposureTimeUnit.visibility != expectedExposureTimeVisibility || binding.staticExposureValueTitle.visibility != expectedExposureTimeVisibility || binding.staticExposureValueSlider.visibility != expectedExposureTimeVisibility || binding.staticExposureValue.visibility != expectedExposureTimeVisibility || binding.staticExposureValueDescription.visibility != expectedExposureTimeVisibility) {
            Log.d(
                TAG, "updateView -> exposure time fields to "
                        + if (expectedExposureTimeVisibility == View.VISIBLE) "visible" else "invisible"
            )
            // binding.staticExposureTimeSlider.setVisibility(expectedExposureTimeVisibility);
            binding.staticExposureTime.visibility = expectedExposureTimeVisibility
            binding.staticExposureTimeUnit.visibility = expectedExposureTimeVisibility
            binding.staticExposureValueTitle.visibility = expectedExposureTimeVisibility
            binding.staticExposureValueSlider.visibility = expectedExposureTimeVisibility
            binding.staticExposureValue.visibility = expectedExposureTimeVisibility
            binding.staticExposureValueDescription.visibility = expectedExposureTimeVisibility
        }
    }

    /**
     * Displays the supported camera2 features in the view.
     *
     * @param characteristics the features to check
     * @param isManualSensorSupported `True` if the 'manual sensor' feature is supported
     * @param minFocusDistance the minimum focus distance supported
     */
    private fun displaySupportLevelsAndUnits(
        characteristics: CameraCharacteristics,
        isManualSensorSupported: Boolean, minFocusDistance: Float
    ) {

        // Display camera2 API support level
        val camera2Level = getCamera2SupportLevel(characteristics)
        binding.hardwareSupport.text = camera2Level

        // Display 'manual sensor' support
        binding.manualSensorSupport.text =
            if (isManualSensorSupported) "supported" else "no supported"

        // Display whether the focus distance setting is calibrated (i.e. has a unit)
        val calibrationLevel = getFocusDistanceCalibration(characteristics)
        val unitDetails =
            if (calibrationLevel == FOCUS_DISTANCE_NOT_CALIBRATED) "uncalibrated" else "dioptre"
        val unit =
            if (calibrationLevel == FOCUS_DISTANCE_NOT_CALIBRATED) "" else " [dioptre]"
        binding.focusDistance.text = calibrationLevel
        binding.staticFocusUnit.text = unitDetails

        // Display focus distance ranges
        val hyperFocalDistance =
            characteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE)
        val hyperFocalDistanceText = hyperFocalDistance.toString() + unit
        val shortestFocalDistanceText = minFocusDistance.toString() + unit
        binding.hyperFocalDistance.text = hyperFocalDistanceText
        binding.minimumFocusDistance.text = shortestFocalDistanceText
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

    /**
     * Returns the support level for the 'camera2' API, see http://stackoverflow.com/a/31240881/5815054.
     *
     * @param characteristics the camera hardware features to check
     * @return the support level as simple `String`
     */
    private fun getCamera2SupportLevel(characteristics: CameraCharacteristics): String {
        val supportedHardwareLevel = characteristics
            .get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        var supportLevel = "unknown"
        if (supportedHardwareLevel != null) {
            when (supportedHardwareLevel) {
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> supportLevel = "legacy"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> supportLevel = "limited"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> supportLevel = "full"
            }
        }
        return supportLevel
    }


    /**
     * Called when an exposure time was selected in the [ExposureTimeDialog].
     *
     * @param data an intent which may contain result data
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DIALOG_EXPOSURE_TIME_SELECTION_REQUEST_CODE) {
            val exposureTimeNanos = data!!.getLongExtra(
                Constants.PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_KEY,
                -1L
            )
            Validate.isTrue(exposureTimeNanos != -1L)
            val fraction = Utils.getExposureTimeFraction(exposureTimeNanos)
            Log.d(
                TAG,
                "Update view to exposure time -> $exposureTimeNanos ns - fraction: $fraction s"
            )
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


// final SwitchCompat connectToExternalSpeedSensorToggle = (SwitchCompat)view.getMenu()
// .findItem(R.id.drawer_setting_speed_sensor).getActionView();

/*
final boolean bluetoothIsConfigured = preferences.getString(BLUETOOTHLE_DEVICE_MAC_KEY, null) != null
&& preferences.getFloat(BLUETOOTHLE_WHEEL_CIRCUMFERENCE, 0.0F) > 0.0F;
connectToExternalSpeedSensorToggle.setChecked(bluetoothIsConfigured);

// connectToExternalSpeedSensorToggle.setOnClickListener(new ConnectToExternalSpeedSensorToggleListener());


/*
 * A listener which is called when the external bluetooth sensor toggle in the {@link NavDrawer} is clicked.
 * /
 * private class ConnectToExternalSpeedSensorToggleListener implements CompoundButton.OnCheckedChangeListener {
 *
 * @Override
 * public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
 * final CompoundButton compoundButton = (CompoundButton)view;
 * final Context applicationContext = view.getContext().getApplicationContext();
 * if (compoundButton.isChecked()) {
 * final BluetoothLeSetup bluetoothLeSetup = new BluetoothLeSetup(new BluetoothLeSetupListener() {
 *
 * @Override
 * public void onDeviceSelected(final BluetoothDevice device, final double wheelCircumference) {
 * final SharedPreferences.Editor editor = preferences.edit();
 * editor.putString(BLUETOOTHLE_DEVICE_MAC_KEY, device.getAddress());
 * editor.putFloat(BLUETOOTHLE_WHEEL_CIRCUMFERENCE,
 * Double.valueOf(wheelCircumference).floatValue());
 * editor.apply();
 * }
 *
 * @Override
 * public void onSetupProcessFailed(final Reason reason) {
 * compoundButton.setChecked(false);
 * if (reason.equals(Reason.NOT_SUPPORTED)) {
 * Toast.makeText(applicationContext, R.string.ble_not_supported, Toast.LENGTH_SHORT)
 * .show();
 * } else {
 * Log.e(TAG, "Setup process of bluetooth failed: " + reason);
 * Toast.makeText(applicationContext, R.string.bluetooth_setup_failed, Toast.LENGTH_SHORT)
 * .show();
 * }
 * }
 * });
 * bluetoothLeSetup.setup(mainActivity);
 * } else {
 * final SharedPreferences.Editor editor = preferences.edit();
 * editor.remove(BLUETOOTHLE_DEVICE_MAC_KEY);
 * editor.remove(BLUETOOTHLE_WHEEL_CIRCUMFERENCE);
 * editor.apply();
 * }
 * }
 * }
 */