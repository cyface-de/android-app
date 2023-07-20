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
package de.cyface.app.capturing

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.slider.Slider
import de.cyface.app.R
import de.cyface.app.capturing.SettingsFragment.Companion.DIALOG_EXPOSURE_TIME_SELECTION_REQUEST_CODE
import de.cyface.app.capturing.SettingsFragment.Companion.EXPOSURE_VALUES
import de.cyface.app.capturing.SettingsFragment.Companion.TAG
import de.cyface.app.databinding.FragmentSettingsBinding
import de.cyface.app.dialog.ExposureTimeDialog
import de.cyface.app.utils.Constants
import de.cyface.app.utils.ServiceProvider
import de.cyface.app.utils.SharedConstants.DEFAULT_SENSOR_FREQUENCY
import de.cyface.app.utils.SharedConstants.PREFERENCES_CENTER_MAP_KEY
import de.cyface.app.utils.SharedConstants.PREFERENCES_SENSOR_FREQUENCY_KEY
import de.cyface.app.utils.SharedConstants.PREFERENCES_SYNCHRONIZATION_KEY
import de.cyface.camera_service.CameraModeDialog
import de.cyface.camera_service.Constants.DEFAULT_STATIC_EXPOSURE_TIME
import de.cyface.camera_service.Constants.DEFAULT_STATIC_EXPOSURE_VALUE_ISO_100
import de.cyface.camera_service.Constants.PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY
import de.cyface.camera_service.Constants.PREFERENCES_CAMERA_DISTANCE_BASED_TRIGGERING_ENABLED_KEY
import de.cyface.camera_service.Constants.PREFERENCES_CAMERA_RAW_MODE_ENABLED_KEY
import de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_ENABLED_KEY
import de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_EXPOSURE_VALUE_KEY
import de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_KEY
import de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_FOCUS_ENABLED_KEY
import de.cyface.camera_service.Constants.PREFERENCES_CAMERA_VIDEO_MODE_ENABLED_KEY
import de.cyface.camera_service.Utils
import de.cyface.camera_service.Utils.getExposureTimeFraction
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.utils.Validate
import java.util.TreeMap
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The [Fragment] which shows the settings to the user.
 *
 * @author Armin Schnabel
 * @version 1.0.0
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
     * The preferences used to store the user's preferred settings.
     */
    private lateinit var preferences: SharedPreferences

    /**
     * The {@code String} which represents the hardware camera focus distance calibration level.
     */
    @Suppress("PrivatePropertyName")
    private val FOCUS_DISTANCE_NOT_CALIBRATED = "uncalibrated"

    /**
     * The unit of the [.staticExposureTimePreference] value shown in the **UI**.
     */
    @Suppress("PrivatePropertyName")
    private val EXPOSURE_TIME_UNIT = "s (click on time to change)"

    /**
     * The unit of the [.triggerDistancePreference] value shown in the **UI**.
     */
    @Suppress("PrivatePropertyName")
    private val TRIGGER_DISTANCE_UNIT = "m"

    /**
     * {@code True} if the camera allows to control the sensors (focus, exposure, etc.) manually.
     */
    private var manualSensorSupported: Boolean = false

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
                    showCameraModeDialog(this)
                } else {
                    // Deactivate camera service and inform user about this
                    deactivateCameraService()
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
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context)

        binding.centerMapSwitch.setOnCheckedChangeListener(
            CenterMapSwitchHandler(
                preferences,
                context
            )
        )
        binding.uploadSwitch.setOnCheckedChangeListener(
            UploadSwitchHandler(
                preferences,
                context,
                capturing
            )
        )
        binding.cameraSwitch.setOnCheckedChangeListener(
            CameraSwitchHandler(
                preferences,
                this
            )
        )

        // Set manual sensor support
        val characteristics = loadCameraCharacteristics()
        manualSensorSupported = Utils.isFeatureSupported(
            characteristics,
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
        )

        // Static Focus Distance
        binding.staticFocusSwitcher.setOnCheckedChangeListener(
            StaticFocusSwitcherHandler(
                requireContext(),
                preferences,
                manualSensorSupported,
                binding.staticFocusSwitcher,
                binding.staticFocusDistanceSlider,
                binding.staticFocus,
                binding.staticFocusUnit
            )
        )
        binding.staticFocusDistanceSlider.addOnChangeListener(
            StaticFocusDistanceSliderHandler(
                preferences,
                binding.staticFocus
            )
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
            DistanceBasedSwitcherHandler(
                requireContext(),
                preferences,
                binding.distanceBasedSlider,
                binding.distanceBased,
                binding.distanceBasedUnit
            )
        )
        binding.distanceBasedSlider.addOnChangeListener(
            TriggerDistanceSliderHandler(
                preferences,
                binding.distanceBased
            )
        )
        // triggerDistanceSlider.setValueTo(minFocusDistance);
        binding.distanceBasedUnit.text = TRIGGER_DISTANCE_UNIT

        // Static Exposure Time
        binding.staticExposureTimeSwitcher.setOnCheckedChangeListener(
            StaticExposureTimeSwitcherHandler(
                preferences,
                requireContext(),
                manualSensorSupported,
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
            StaticExposureValueSliderHandler(
                preferences,
                binding.staticExposureValue,
                binding.staticExposureValueDescription
            )
        )
        binding.staticExposureValueSlider.valueFrom = EXPOSURE_VALUES.firstKey()!!.toFloat()
        binding.staticExposureValueSlider.valueTo = EXPOSURE_VALUES.lastKey()!!.toFloat()

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

        // Show supported camera features
        displaySupportLevelsAndUnits(characteristics, manualSensorSupported, minFocusDistance!!)

        // Sensor frequency
        binding.sensorFrequencySlider.addOnChangeListener(
            SensorFrequencySliderHandler(
                preferences,
                binding.sensorFrequency
            )
        )

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        updateViewToPreference()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Updates the view to the current preferences. This can be used to initialize the view.
     */
    private fun updateViewToPreference() {

        // Switchers at the very top
        binding.centerMapSwitch.isChecked =
            preferences.getBoolean(PREFERENCES_CENTER_MAP_KEY, true)
        binding.uploadSwitch.isChecked =
            preferences.getBoolean(PREFERENCES_SYNCHRONIZATION_KEY, true)
        val cameraModeEnabledPreferred = preferences.getBoolean(
            PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY,
            false
        )
        binding.cameraSwitch.isChecked = cameraModeEnabledPreferred

        // Update camera enabled and mode status view if incorrect
        val videoModePreferred = preferences.getBoolean(
            PREFERENCES_CAMERA_VIDEO_MODE_ENABLED_KEY,
            false
        )
        val rawModePreferred = preferences.getBoolean(
            PREFERENCES_CAMERA_RAW_MODE_ENABLED_KEY,
            false
        )
        /*val cameraModeText =
            if (videoModePreferred) "Video" else if (rawModePreferred) "DNG" else "JPEG"
        val cameraStatusText =
            if (!cameraModeEnabledPreferred) "disabled" else "enabled, $cameraModeText mode"
        if (binding.cameraStatus.text !== cameraStatusText) {
            Log.d(TAG, "updateView -> camera mode view $cameraStatusText")
            binding.cameraStatus.text = cameraStatusText
        }*/
        updateDistanceBasedTriggeringViewToPreference()

        // Only check manual sensor settings if it's supported or else the app crashes
        if (manualSensorSupported) {
            updateManualSensorViewToPreferences()
        } else {
            binding.staticFocusDistanceSlider.visibility = View.INVISIBLE
            // staticExposureTimeSlider.setVisibility(View.INVISIBLE);
            binding.staticExposureValueSlider.visibility = View.INVISIBLE
        }

        // Update sensor frequency slider view if incorrect
        val preferredSensorFrequency = preferences.getInt(
            PREFERENCES_SENSOR_FREQUENCY_KEY,
            DEFAULT_SENSOR_FREQUENCY
        )
        if (binding.sensorFrequencySlider.value != preferredSensorFrequency.toFloat()) {
            Log.d(TAG, "updateView -> sensor frequency slider $preferredSensorFrequency")
            binding.sensorFrequencySlider.value = preferredSensorFrequency.toFloat()
        }
        if (binding.sensorFrequency.text.isEmpty()
            || binding.sensorFrequency.text.toString().toInt() != preferredSensorFrequency
        ) {
            Log.d(TAG, "updateView -> sensor frequency text $preferredSensorFrequency")
            binding.sensorFrequency.text = preferredSensorFrequency.toString()
        }
    }

    /**
     * Displays a dialog for the user to select a camera mode (video- or picture mode).
     *
     */
    fun showCameraModeDialog(fragment: SettingsFragment?) {

        //homeSelected() // avoid crash DAT-69
        val cameraModeDialog = CameraModeDialog()
        cameraModeDialog.setTargetFragment(
            fragment,
            de.cyface.energy_settings.Constants.DIALOG_ENERGY_SAFER_WARNING_CODE
        )
        cameraModeDialog.isCancelable = false
        cameraModeDialog.show(requireFragmentManager(), "CAMERA_MODE_DIALOG")
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

    private fun deactivateCameraService() {
        binding.cameraSwitch.isChecked = false
        preferences.edit().putBoolean(PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY, false).apply()
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

    private fun updateDistanceBasedTriggeringViewToPreference() {
        // Update slider view if incorrect
        val preferredTriggerDistance = preferences.getFloat(
            de.cyface.camera_service.Constants.PREFERENCES_CAMERA_TRIGGERING_DISTANCE_KEY,
            de.cyface.camera_service.Constants.DEFAULT_TRIGGERING_DISTANCE
        )
        val roundedDistance = (preferredTriggerDistance * 100).roundToInt() / 100f
        if (binding.distanceBasedSlider.value != roundedDistance) {
            Log.d(
                TAG,
                "updateView -> triggering distance slider $roundedDistance"
            )
            binding.distanceBasedSlider.value = roundedDistance
        }
        if (binding.distanceBased.text.isEmpty()
            || binding.distanceBased.text.toString().toFloat() != roundedDistance
        ) {
            Log.d(
                TAG,
                "updateView -> triggering distance text $roundedDistance"
            )
            binding.distanceBased.text = roundedDistance.toString()
        }

        // Update switcher view if incorrect
        val distanceBasedTriggeringPreferred = preferences.getBoolean(
            PREFERENCES_CAMERA_DISTANCE_BASED_TRIGGERING_ENABLED_KEY,
            true
        )
        if (binding.distanceBasedSwitcher.isChecked != distanceBasedTriggeringPreferred) {
            Log.d(
                TAG,
                "updateView distance based triggering switcher -> $distanceBasedTriggeringPreferred"
            )
            binding.distanceBasedSwitcher.isChecked = distanceBasedTriggeringPreferred
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
        if (binding.distanceBasedSlider.visibility != expectedTriggerDistanceVisibility || binding.distanceBased.getVisibility() != expectedTriggerDistanceVisibility || binding.distanceBasedUnit.getVisibility() != expectedTriggerDistanceVisibility) {
            Log.d(
                TAG, "updateView -> distance triggering fields to "
                        + if (expectedTriggerDistanceVisibility == View.VISIBLE) "visible" else "invisible"
            )
            binding.distanceBasedSlider.visibility = expectedTriggerDistanceVisibility
            binding.distanceBased.visibility = expectedTriggerDistanceVisibility
            binding.distanceBasedUnit.visibility = expectedTriggerDistanceVisibility
        }
    }

    private fun updateManualSensorViewToPreferences() {
        // Update focus distance slider view if incorrect
        val preferredFocusDistance = preferences.getFloat(
            de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_FOCUS_DISTANCE_KEY,
            de.cyface.camera_service.Constants.DEFAULT_STATIC_FOCUS_DISTANCE
        )
        val roundedDistance = (preferredFocusDistance * 100).roundToInt() / 100f
        if (binding.staticFocusDistanceSlider.value != roundedDistance) {
            Log.d(
                TAG,
                "updateView -> focus distance slider $roundedDistance"
            )
            binding.staticFocusDistanceSlider.value = roundedDistance
        }
        if (binding.staticFocus.text.isEmpty()
            || binding.staticFocus.text.toString().toFloat() != roundedDistance
        ) {
            Log.d(
                TAG,
                "updateView -> focus distance text $roundedDistance"
            )
            binding.staticFocus.text = roundedDistance.toString()
        }

        // Update exposure time slider view if incorrect
        val preferredExposureTimeNanos = preferences.getLong(
            PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_KEY,
            DEFAULT_STATIC_EXPOSURE_TIME
        )
        /*
         * if (staticExposureTimeSlider.getValue() != preferredExposureTimeNanos) {
         * Log.d(TAG, "updateView -> exposure time slider " + preferredExposureTimeNanos);
         * staticExposureTimeSlider.setValue(preferredExposureTimeNanos);
         * }
        */if (binding.staticExposureTime.text.isEmpty()
            || binding.staticExposureTime.text.toString() != getExposureTimeFraction(
                preferredExposureTimeNanos
            )
        ) {
            Log.d(
                TAG,
                "updateView -> exposure time text $preferredExposureTimeNanos"
            )
            binding.staticExposureTime.text = getExposureTimeFraction(
                preferredExposureTimeNanos
            )
        }

        // Update exposure value slider view if incorrect
        val preferredExposureValue = preferences.getInt(
            PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_EXPOSURE_VALUE_KEY,
            DEFAULT_STATIC_EXPOSURE_VALUE_ISO_100
        )
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
            Log.d(
                TAG,
                "updateView -> exposure value description $expectedExposureValueDescription"
            )
            binding.staticExposureValueDescription.text = expectedExposureValueDescription
        }

        // Update focus distance switcher view if incorrect
        val focusDistancePreferred = preferences.getBoolean(
            PREFERENCES_CAMERA_STATIC_FOCUS_ENABLED_KEY,
            false
        )
        if (binding.staticFocusSwitcher.isChecked != focusDistancePreferred) {
            Log.d(
                TAG,
                "updateView focus distance switcher -> $focusDistancePreferred"
            )
            binding.staticFocusSwitcher.isChecked = focusDistancePreferred
        }

        // Update exposure time switcher view if incorrect
        val exposureTimePreferred = preferences
            .getBoolean(
                PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_ENABLED_KEY,
                false
            )
        if (binding.staticExposureTimeSwitcher.isChecked != exposureTimePreferred) {
            Log.d(
                TAG,
                "updateView exposure time switcher -> $exposureTimePreferred"
            )
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
    */binding.staticExposureTime.visibility != expectedExposureTimeVisibility || binding.staticExposureTimeUnit.getVisibility() != expectedExposureTimeVisibility || binding.staticExposureValueTitle.getVisibility() != expectedExposureTimeVisibility || binding.staticExposureValueSlider.getVisibility() != expectedExposureTimeVisibility || binding.staticExposureValue.getVisibility() != expectedExposureTimeVisibility || binding.staticExposureValueDescription.getVisibility() != expectedExposureTimeVisibility) {
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
                PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_KEY,
                -1L
            )
            Validate.isTrue(exposureTimeNanos != -1L)
            val fraction = getExposureTimeFraction(exposureTimeNanos)
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
        const val TAG = Constants.PACKAGE + ".sf"

        /**
         * The identifier for the [ExposureTimeDialog] request.
         */
        const val DIALOG_EXPOSURE_TIME_SELECTION_REQUEST_CODE = 202002170

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

/**
 * Handles when the user toggles the upload switch.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.2.0
 */
class UploadSwitchHandler(
    private val preferences: SharedPreferences,
    private val context: Context?,
    private val capturingService: CyfaceDataCapturingService
) : CompoundButton.OnCheckedChangeListener {
    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        val current = preferences.getBoolean(PREFERENCES_SYNCHRONIZATION_KEY, true)
        if (current != isChecked) {
            // Update both, the preferences and WifiSurveyor's synchronizationEnabled status
            capturingService.wiFiSurveyor.isSyncEnabled = isChecked
            preferences.edit().putBoolean(PREFERENCES_SYNCHRONIZATION_KEY, isChecked).apply()

            // Show warning to user (storage gets filled)
            if (!isChecked) {
                Toast.makeText(
                    context,
                    de.cyface.app.utils.R.string.sync_disabled_toast,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

/**
 * Handles when the user toggles the center map switch.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.2.0
 */
private class CenterMapSwitchHandler(
    private val preferences: SharedPreferences,
    private val context: Context?
) : CompoundButton.OnCheckedChangeListener {
    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        val current = preferences.getBoolean(PREFERENCES_CENTER_MAP_KEY, true)
        if (current != isChecked) {
            preferences.edit().putBoolean(PREFERENCES_CENTER_MAP_KEY, isChecked).apply()
            if (isChecked) {
                Toast.makeText(
                    context,
                    de.cyface.app.utils.R.string.zoom_to_location_enabled_toast,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

/**
 * Handles when the user toggles the camera switch.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 2.0.0
 */
class CameraSwitchHandler(
    private val preferences: SharedPreferences,
    private val fragment: SettingsFragment?
) : CompoundButton.OnCheckedChangeListener {
    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        val current = preferences.getBoolean(PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY, true)
        val editor = preferences.edit()
        val context = fragment!!.requireContext()
        if (current != isChecked) {
            if (isChecked) {
                // No rear camera found to be enabled - we explicitly only support rear camera for now
                @SuppressLint("UnsupportedChromeOsCameraSystemFeature")
                val noCameraFound =
                    !context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
                if (noCameraFound) {
                    buttonView!!.isChecked = false
                    Toast.makeText(context, R.string.no_camera_available_toast, Toast.LENGTH_LONG)
                        .show()
                    return
                }

                // Request permission for camera capturing
                val permissionsGranted = ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (!permissionsGranted) {
                    /*ActivityCompat.requestPermissions(
                        fragment.requireActivity(),
                        arrayOf(Manifest.permission.CAMERA),
                        PERMISSION_REQUEST_CAMERA_AND_STORAGE_PERMISSION
                    )*/
                    fragment.permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                } else {
                    // Ask user to select camera mode
                    fragment.showCameraModeDialog(fragment)
                }

            }
            // Update camera enable setting
            editor.putBoolean(PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY, isChecked)
                .apply()
        }
    }
}

/**
 * Handles UI changes of the 'slider' used to adjust the 'focus distance' setting.
 */
private class StaticFocusDistanceSliderHandler(
    private val preferences: SharedPreferences,
    private val staticFocus: TextView
) : Slider.OnChangeListener {
    override fun onValueChange(slider: Slider, newValue: Float, fromUser: Boolean) {
        val roundedDistance = (newValue * 100).roundToInt() / 100f
        Log.d(
            TAG,
            "Update preference to focus distance -> $roundedDistance"
        )
        val preference = preferences.getFloat(
            de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_FOCUS_DISTANCE_KEY,
            de.cyface.camera_service.Constants.DEFAULT_STATIC_FOCUS_DISTANCE
        )
        if (preference == roundedDistance) {
            Log.d(
                TAG,
                "Preference already $preference, doing nothing"
            )
            return
        }
        val editor = preferences.edit()
        editor.putFloat(
            de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_FOCUS_DISTANCE_KEY,
            roundedDistance
        ).apply()
        val text = StringBuilder(roundedDistance.toString())
        while (text.length < 4) {
            text.append("0")
        }
        staticFocus.text = text
    }
}

/**
 * Handles UI changes of the 'slider' used to adjust the 'triggering distance' setting.
 */
private class TriggerDistanceSliderHandler(
    private val preferences: SharedPreferences,
    private val distanceBased: TextView
) : Slider.OnChangeListener {
    override fun onValueChange(slider: Slider, newValue: Float, fromUser: Boolean) {
        val roundedDistance = (newValue * 100).roundToInt() / 100f
        Log.d(
            TAG,
            "Update preference to triggering distance -> $roundedDistance"
        )
        val preference: Float = preferences.getFloat(
            de.cyface.camera_service.Constants.PREFERENCES_CAMERA_TRIGGERING_DISTANCE_KEY,
            de.cyface.camera_service.Constants.DEFAULT_TRIGGERING_DISTANCE
        )
        if (preference == roundedDistance) {
            Log.d(
                TAG,
                "Preference already $preference, doing nothing"
            )
            return
        }
        val editor = preferences.edit()
        editor.putFloat(
            de.cyface.camera_service.Constants.PREFERENCES_CAMERA_TRIGGERING_DISTANCE_KEY,
            roundedDistance
        ).apply()
        val text = StringBuilder(roundedDistance.toString())
        while (text.length < 4) {
            text.append("0")
        }
        distanceBased.text = text
    }
}

/**
 * Handles UI changes of the 'switcher' used to en-/disable 'static focus' feature.
 */
private class StaticFocusSwitcherHandler(
    private val context: Context,
    private val preferences: SharedPreferences,
    private val manualSensorSupported: Boolean,
    private val staticFocusSwitcher: SwitchCompat,
    private val staticFocusDistanceSlider: Slider,
    private val staticFocus: TextView,
    private val staticFocusUnit: TextView
) : CompoundButton.OnCheckedChangeListener {
    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (!manualSensorSupported && isChecked) {
            Toast.makeText(
                context,
                "This device does not support manual focus control",
                Toast.LENGTH_LONG
            )
                .show()
            staticFocusSwitcher.isChecked = false
            return
        }
        Log.d(TAG, "Update preference to focus -> " + if (isChecked) "manual" else "auto")
        val preference = preferences.getBoolean(
            PREFERENCES_CAMERA_STATIC_FOCUS_ENABLED_KEY,
            false
        )
        if (preference == isChecked) {
            Log.d(TAG, "Preference already $preference, doing nothing")
            return
        }
        val editor = preferences.edit()
        editor.putBoolean(PREFERENCES_CAMERA_STATIC_FOCUS_ENABLED_KEY, isChecked).apply()
        if (isChecked) {
            Toast.makeText(context, R.string.experimental_feature_warning, Toast.LENGTH_LONG)
                .show()
        }
// Update visibility of slider
        val expectedVisibility = if (isChecked) View.VISIBLE else View.INVISIBLE
        val sliderVisibilityOutOfSync = staticFocusDistanceSlider.visibility != expectedVisibility
        val preferenceVisibilityOutOfSync = staticFocus.visibility != expectedVisibility
        val unitVisibilityOutOfSync = staticFocusUnit.visibility != expectedVisibility
        if (sliderVisibilityOutOfSync || preferenceVisibilityOutOfSync || unitVisibilityOutOfSync) {
            Log.d(
                TAG,
                "updateView -> " + if (expectedVisibility == View.VISIBLE) "visible" else "invisible"
            )
            staticFocusDistanceSlider.visibility = expectedVisibility
            staticFocus.visibility = expectedVisibility
            staticFocusUnit.visibility = expectedVisibility
        }
    }
}

/**
 * Handles UI changes of the 'switcher' used to en-/disable 'distance based triggering' feature.
 */
private class DistanceBasedSwitcherHandler(
    private val context: Context,
    private val preferences: SharedPreferences,
    private val distanceBasedSlider: Slider,
    private val distanceBased: TextView,
    private val distanceBasedUnit: TextView
) : CompoundButton.OnCheckedChangeListener {
    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        Log.d(TAG, "Update preference to distance-based-trigger -> $isChecked")
        val preference = preferences.getBoolean(
            PREFERENCES_CAMERA_DISTANCE_BASED_TRIGGERING_ENABLED_KEY,
            true
        )
        if (preference == isChecked) {
            Log.d(TAG, "Preference already $preference, doing nothing")
            return
        }
        val editor = preferences.edit()
        editor.putBoolean(PREFERENCES_CAMERA_DISTANCE_BASED_TRIGGERING_ENABLED_KEY, isChecked)
            .apply()
        if (isChecked) {
            Toast.makeText(context, R.string.experimental_feature_warning, Toast.LENGTH_LONG).show()
        }
// Update visibility of slider
        val expectedVisibility = if (isChecked) View.VISIBLE else View.INVISIBLE
        val sliderVisibilityOutOfSync = distanceBasedSlider.visibility != expectedVisibility
        val preferenceVisibilityOutOfSync = distanceBased.visibility != expectedVisibility
        val unitVisibilityOutOfSync = distanceBasedUnit.visibility != expectedVisibility
        if (sliderVisibilityOutOfSync || preferenceVisibilityOutOfSync || unitVisibilityOutOfSync) {
            Log.d(
                TAG,
                "updateView -> " + if (expectedVisibility == View.VISIBLE) "visible" else "invisible"
            )
            distanceBasedSlider.visibility = expectedVisibility
            distanceBased.visibility = expectedVisibility
            distanceBasedUnit.visibility = expectedVisibility
        }
    }
}

/**
 * Handles UI clicks on the exposure time used to adjust the 'exposure time' setting.
 */
private class StaticExposureTimeClickHandler(
    private val fragmentManager: FragmentManager?,
    private val settingsFragment: SettingsFragment
) : View.OnClickListener {
    override fun onClick(v: View) {
        Log.d(
            TAG,
            "StaticExposureTimeClickHandler triggered, showing ExposureTimeDialog"
        )
        Validate.notNull(fragmentManager)
        val dialog = ExposureTimeDialog()
        dialog.setTargetFragment(
            settingsFragment,
            DIALOG_EXPOSURE_TIME_SELECTION_REQUEST_CODE
        )
        dialog.isCancelable = true
        dialog.show(fragmentManager!!, "EXPOSURE_TIME_DIALOG")
    }
}

/*
 * Handles UI changes of the 'slider' used to adjust the 'exposure time' setting.
 */
private class StaticExposureTimeSliderHandler(
    private val preferences: SharedPreferences,
    private val staticExposureValue: TextView
) :
    Slider.OnChangeListener {
    override fun onValueChange(slider: Slider, newValue: Float, fromUser: Boolean) {
        Log.d(TAG, "Update preference to exposure time -> $newValue")
        val preferenceNanos = preferences.getLong(
            PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_KEY,
            DEFAULT_STATIC_EXPOSURE_TIME
        )/*.roundToInt()*/
        if (preferenceNanos.toFloat() == newValue) {
            Log.d(TAG, "Preference already $preferenceNanos ns, doing nothing")
            return
        }
        val value = newValue.toLong()
        val editor: SharedPreferences.Editor = preferences.edit()
        editor.putLong(PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_KEY, value).apply()
        staticExposureValue.text = getExposureTimeFraction(value)
    }
}

/**
 * Handles UI changes of the 'switcher' used to en-/disable 'static exposure time' feature.
 */
private class StaticExposureTimeSwitcherHandler(
    private val preferences: SharedPreferences,
    private val context: Context,
    private val manualSensorSupported: Boolean,
    private val staticExposureTimeSwitcher: SwitchCompat,
    private val staticExposureTime: TextView,
    private val staticExposureTimeUnit: TextView,
    private val staticExposureValueTitle: TextView,
    private val staticExposureValueSlider: Slider,
    private val staticExposureValue: TextView,
    private val staticExposureValueDescription: TextView
) : CompoundButton.OnCheckedChangeListener {
    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (!manualSensorSupported && isChecked) {
            Toast.makeText(
                context,
                "This device does not support manual exposure control",
                Toast.LENGTH_LONG
            ).show()
            staticExposureTimeSwitcher.isChecked = false
            return
        }
        Log.d(TAG, "Update preference to exposure -> " + if (isChecked) "Tv-/S-Mode" else "auto")
        val preference =
            preferences.getBoolean(PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_ENABLED_KEY, false)
        if (preference == isChecked) {
            Log.d(TAG, "Preference already $preference, doing nothing")
            return
        }
        val editor = preferences.edit()
        editor.putBoolean(PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_ENABLED_KEY, isChecked).apply()
        if (isChecked) {
            Toast.makeText(context, R.string.experimental_feature_warning, Toast.LENGTH_LONG).show()
        }
        // Update visibility of slider
        val expectedVisibility = if (isChecked) View.VISIBLE else View.INVISIBLE
        // final boolean sliderVisibilityOutOfSync = staticExposureTimeSlider.getVisibility() != expectedVisibility;
        val preferenceVisibilityOutOfSync = staticExposureTime.visibility != expectedVisibility
        val unitVisibilityOutOfSync = staticExposureTimeUnit.visibility != expectedVisibility
        val evTitleVisibilityOutOfSync = staticExposureValueTitle.visibility != expectedVisibility
        val evSliderVisibilityOutOfSync = staticExposureValueSlider.visibility != expectedVisibility
        val evPreferenceVisibilityOutOfSync = staticExposureValue.visibility != expectedVisibility
        val evDescriptionVisibilityOutOfSync =
            staticExposureValueDescription.visibility != expectedVisibility
        if ( /* sliderVisibilityOutOfSync || */preferenceVisibilityOutOfSync || unitVisibilityOutOfSync
            || evTitleVisibilityOutOfSync || evSliderVisibilityOutOfSync || evPreferenceVisibilityOutOfSync
            || evDescriptionVisibilityOutOfSync
        ) {
            Log.d(
                TAG,
                "updateView -> " + if (expectedVisibility == View.VISIBLE) "visible" else "invisible"
            )
// staticExposureTimeSlider.setVisibility(expectedVisibility);
            staticExposureTime.visibility = expectedVisibility
            staticExposureTimeUnit.visibility = expectedVisibility
            staticExposureValueTitle.visibility = expectedVisibility
            staticExposureValueSlider.visibility = expectedVisibility
            staticExposureValue.visibility = expectedVisibility
            staticExposureValueDescription.visibility = expectedVisibility
        }
    }
}

/**
 * Handles UI changes of the 'slider' used to adjust the 'exposure value' setting.
 */
private class StaticExposureValueSliderHandler(
    private val preferences: SharedPreferences,
    private val staticExposureValue: TextView,
    private val staticExposureValueDescription: TextView
) : Slider.OnChangeListener {
    override fun onValueChange(slider: Slider, newValue: Float, fromUser: Boolean) {
        Log.d(
            TAG,
            "Update preference to exposure value -> $newValue"
        )
        val preference = preferences.getInt(
            PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_EXPOSURE_VALUE_KEY,
            DEFAULT_STATIC_EXPOSURE_VALUE_ISO_100
        )
        if (preference.toFloat() == newValue) {
            Log.d(TAG, "Preference already $preference, doing nothing")
            return
        }
        val value = newValue.toInt()
        val description = EXPOSURE_VALUES[value]
        val editor = preferences.edit()
        editor.putInt(PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_EXPOSURE_VALUE_KEY, value).apply()
        staticExposureValue.text = value.toString()
        staticExposureValueDescription.text = description
    }
}

/**
 * Handles UI changes of the 'slider' used to adjust the 'sensor frequency' setting.
 */
private class SensorFrequencySliderHandler(
    private val preferences: SharedPreferences,
    private val sensorFrequency: TextView
) : Slider.OnChangeListener {
    override fun onValueChange(slider: Slider, newValue: Float, fromUser: Boolean) {
        Log.d(TAG, "Update preference to sensor frequency -> $newValue")
        val newSensorFrequency = newValue.toInt()
        val preference =
            preferences.getInt(PREFERENCES_SENSOR_FREQUENCY_KEY, DEFAULT_SENSOR_FREQUENCY)
        if (preference == newSensorFrequency) {
            Log.d(TAG, "Preference already $preference, doing nothing")
            return
        }
        val editor = preferences.edit()
        editor.putInt(PREFERENCES_SENSOR_FREQUENCY_KEY, newSensorFrequency).apply()
        val text = StringBuilder(newSensorFrequency)
        sensorFrequency.text = text
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