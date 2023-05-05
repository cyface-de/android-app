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

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import de.cyface.app.databinding.FragmentSettingsBinding
import de.cyface.app.dialog.ExposureTimeDialog
import de.cyface.app.utils.Constants
import de.cyface.app.utils.ServiceProvider
import de.cyface.app.utils.SharedConstants.PREFERENCES_CENTER_MAP_KEY
import de.cyface.app.utils.SharedConstants.PREFERENCES_SYNCHRONIZATION_KEY
import de.cyface.datacapturing.CyfaceDataCapturingService

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
     * /
    private val FOCUS_DISTANCE_NOT_CALIBRATED = "uncalibrated"

    /**
     * The unit of the [.staticExposureTimePreference] value shown in the **UI**.
    */
    private val EXPOSURE_TIME_UNIT = "s (click on time to change)"

    /**
     * The unit of the [.triggerDistancePreference] value shown in the **UI**.
    */
    private val TRIGGER_DISTANCE_UNIT = "m"

    /**
     * {@code True} if the camera allows to control the sensors (focus, exposure, etc.) manually.
    */
    private var manualSensorSupported: Boolean = false*/

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

        // Auto center map
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

        // Static Focus Distance
        /*binding.staticFocusSwitcher.setOnCheckedChangeListener(
            StaticFocusSwitcherHandler(
                requireContext(),
                preferences,
                manualSensorSupported,
                binding.staticFocusSwitcher,
                binding.staticFocusDistanceSlider,
                binding.staticFocusDistance,
                binding.staticFocusUnit
            )
        )
        binding.staticFocusDistanceSlider.addOnChangeListener(
            StaticFocusDistanceSliderHandler(
                preferences,
                binding.staticFocusDistance
            )
        )
        val characteristics = loadCameraCharacteristics()
        val minFocusDistance =
            characteristics!!.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
        // is null when camera permissions are missing, is 0.0 when "lens is fixed-focus" (e.g. emulators)
        // It's ok when this is not set in those cases as the fragment informs about missing manual focus mode
        if (minFocusDistance != null && minFocusDistance.toDouble() != 0.0) {
            binding.staticFocusDistanceSlider.valueTo = minFocusDistance
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
        // staticExposureTimeSlider = view.findViewById(R.id.camera_settings_static_exposure_time_slider);
        // staticExposureTimeSlider.setOnChangeListener(new StaticExposureTimeSliderHandler());
        binding.staticExposureTime.setOnClickListener(
            StaticExposureTimeClickHandler(
                parentFragmentManager,
                this
            )
        )
        binding.staticExposureTimeUnit.text = EXPOSURE_TIME_UNIT

        // Static Exposure Value
        /*
         * final Range<Long> exposureTimeRange = characteristics
         * .get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
         */
        /*
         * if (exposureTimeRange != null) { // when camera permissions are missing
         * // Limit maximal time to 1/100s for easy motion-blur-reduced ranges: 1/125s and less
         * final float toValue = 10_000_000;//Math.min(10_000_000, exposureTimeRange.getUpper());
         * final float fromValue = 1_250_000;//exposureTimeRange.getLower();
         * staticExposureTimeSlider.setValueTo(toValue);
         * staticExposureTimeSlider.setValueFrom(fromValue);
         * Log.d(TAG, "exposureTimeRange: " + exposureTimeRange + " ns -> set slide range: " + fromValue + " - "
         * + toValue);
         * }
         */
        binding.staticExposureValueSlider.addOnChangeListener(
            StaticExposureValueSliderHandler(
                preferences,
                binding.staticExposureValue,
                binding.staticExposureValueDescription
            )
        )
        binding.staticExposureValueSlider.valueFrom = EXPOSURE_VALUES.firstKey()!!.toFloat()
        binding.staticExposureValueSlider.valueTo = EXPOSURE_VALUES.lastKey()!!.toFloat()

        // Show supported camera features
        manualSensorSupported = Utils.isFeatureSupported(
            characteristics,
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
        )
        displaySupportLevelsAndUnits(characteristics, manualSensorSupported, minFocusDistance!!)

        // Sensor frequency
        binding.sensorFrequencySlider.addOnChangeListener(
            SensorFrequencySliderHandler(
                preferences,
                binding.sensorFrequency
            )
        )*/

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

        // Center map- and synchronization switch
        binding.centerMapSwitch.isChecked =
            preferences.getBoolean(PREFERENCES_CENTER_MAP_KEY, false)
        binding.uploadSwitch.isChecked =
            preferences.getBoolean(PREFERENCES_SYNCHRONIZATION_KEY, true)

        /*
        // Update camera enabled and mode status view if incorrect
        val cameraModeEnabledPreferred = preferences.getBoolean(
            de.cyface.camera_service.Constants.PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY,
            false
        )
        val videoModePreferred = preferences.getBoolean(
            de.cyface.camera_service.Constants.PREFERENCES_CAMERA_VIDEO_MODE_ENABLED_KEY,
            false
        )
        val rawModePreferred = preferences.getBoolean(
            de.cyface.camera_service.Constants.PREFERENCES_CAMERA_RAW_MODE_ENABLED_KEY,
            false
        )
        val cameraModeText =
            if (videoModePreferred) "Video" else if (rawModePreferred) "DNG" else "JPEG"
        val cameraStatusText =
            if (!cameraModeEnabledPreferred) "disabled" else "enabled, $cameraModeText mode"
        if (binding.cameraStatus.text !== cameraStatusText) {
            Log.d(TAG, "updateView -> camera mode view $cameraStatusText")
            binding.cameraStatus.text = cameraStatusText
        }
        updateDistanceBasedTriggeringViewToPreference()

        // Update sensor frequency slider view if incorrect
        val preferredSensorFrequency = preferences.getInt(
            PREFERENCES_SENSOR_FREQUENCY_KEY,
            DEFAULT_SENSOR_FREQUENCY
        )
        if (binding.sensorFrequencySlider.value != preferredSensorFrequency.toFloat()) {
            Log.d(TAG,"updateView -> sensor frequency slider $preferredSensorFrequency")
            binding.sensorFrequencySlider.value = preferredSensorFrequency.toFloat()
        }
        if (binding.sensorFrequency.text.isEmpty()
            || binding.sensorFrequency.text.toString().toInt() != preferredSensorFrequency
        ) {
            Log.d(TAG,"updateView -> sensor frequency text $preferredSensorFrequency")
            binding.sensorFrequency.text = preferredSensorFrequency.toString()
        }

        // Only check manual sensor settings if it's supported or else the app crashes
        if (manualSensorSupported) {
            updateManualSensorViewToPreferences()
        } else {
            binding.staticFocusDistanceSlider.visibility = View.INVISIBLE
            // staticExposureTimeSlider.setVisibility(View.INVISIBLE);
            binding.staticExposureValueSlider.visibility = View.INVISIBLE
        }*/
    }

    /**
     * Returns the features supported by the camera hardware.
     *
     * @return The hardware feature support
     * /
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
    val roundedDistance = Math.round(preferredTriggerDistance * 100) / 100f
    if (binding.distanceBasedSlider.getValue() != roundedDistance) {
    Log.d(
    TAG,
    "updateView -> triggering distance slider $roundedDistance"
    )
    binding.distanceBasedSlider.setValue(roundedDistance)
    }
    if (binding.distanceBased.getText().length == 0
    || binding.distanceBased.getText().toString().toFloat() != roundedDistance
    ) {
    Log.d(
    TAG,
    "updateView -> triggering distance text $roundedDistance"
    )
    binding.distanceBased.setText(roundedDistance.toString())
    }

    // Update switcher view if incorrect
    val distanceBasedTriggeringPreferred = preferences.getBoolean(
    de.cyface.camera_service.Constants.PREFERENCES_CAMERA_DISTANCE_BASED_TRIGGERING_ENABLED_KEY,
    true
    )
    if (binding.distanceBasedSwitcher.isChecked() != distanceBasedTriggeringPreferred) {
    Log.d(
    TAG,
    "updateView distance based triggering switcher -> $distanceBasedTriggeringPreferred"
    )
    binding.distanceBasedSwitcher.setChecked(distanceBasedTriggeringPreferred)
    }

    // Update visibility slider + distance field
    val expectedTriggerDistanceVisibility =
    if (distanceBasedTriggeringPreferred) View.VISIBLE else View.INVISIBLE
    Log.d(
    TAG,
    "view status -> distance triggering fields are " + binding.distanceBasedSlider.getVisibility()
    + "expected is: "
    + if (expectedTriggerDistanceVisibility == View.VISIBLE) "visible" else "invisible"
    )
    if (binding.distanceBasedSlider.getVisibility() != expectedTriggerDistanceVisibility || binding.distanceBased.getVisibility() != expectedTriggerDistanceVisibility || binding.distanceBasedUnit.getVisibility() != expectedTriggerDistanceVisibility) {
    Log.d(
    TAG, "updateView -> distance triggering fields to "
    + if (expectedTriggerDistanceVisibility == View.VISIBLE) "visible" else "invisible"
    )
    binding.distanceBasedSlider.setVisibility(expectedTriggerDistanceVisibility)
    binding.distanceBased.setVisibility(expectedTriggerDistanceVisibility)
    binding.distanceBasedUnit.setVisibility(expectedTriggerDistanceVisibility)
    }
    }

    private fun updateManualSensorViewToPreferences() {
    // Update focus distance slider view if incorrect
    val preferredFocusDistance = preferences.getFloat(
    de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_FOCUS_DISTANCE_KEY,
    de.cyface.camera_service.Constants.DEFAULT_STATIC_FOCUS_DISTANCE
    )
    val roundedDistance = Math.round(preferredFocusDistance * 100) / 100f
    if (binding.staticFocusDistanceSlider.getValue() != roundedDistance) {
    Log.d(
    TAG,
    "updateView -> focus distance slider $roundedDistance"
    )
    binding.staticFocusDistanceSlider.setValue(roundedDistance)
    }
    if (binding.staticFocusDistance.getText().length == 0
    || binding.staticFocusDistance.getText().toString().toFloat() != roundedDistance
    ) {
    Log.d(
    TAG,
    "updateView -> focus distance text $roundedDistance"
    )
    binding.staticFocusDistance.setText(roundedDistance.toString())
    }

    // Update exposure time slider view if incorrect
    val preferredExposureTimeNanos = preferences.getLong(
    de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_KEY,
    de.cyface.camera_service.Constants.DEFAULT_STATIC_EXPOSURE_TIME
    )
    /*
     * if (staticExposureTimeSlider.getValue() != preferredExposureTimeNanos) {
     * Log.d(TAG, "updateView -> exposure time slider " + preferredExposureTimeNanos);
     * staticExposureTimeSlider.setValue(preferredExposureTimeNanos);
     * }
    */if (binding.staticExposureTime.getText().length == 0
    || binding.staticExposureTime.getText().toString() != Utils.getExposureTimeFraction(
    preferredExposureTimeNanos
    )
    ) {
    Log.d(
    TAG,
    "updateView -> exposure time text $preferredExposureTimeNanos"
    )
    binding.staticExposureTime.setText(
    Utils.getExposureTimeFraction(
    preferredExposureTimeNanos
    )
    )
    }

    // Update exposure value slider view if incorrect
    val preferredExposureValue = preferences.getInt(
    de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_EXPOSURE_VALUE_KEY,
    de.cyface.camera_service.Constants.DEFAULT_STATIC_EXPOSURE_VALUE_ISO_100
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
    val expectedExposureValueDescription = EXPOSURE_VALUES.get(preferredExposureValue)
    if (binding.staticExposureValueDescription.text != expectedExposureValueDescription) {
    Log.d(
    TAG,
    "updateView -> exposure value description $expectedExposureValueDescription"
    )
    binding.staticExposureValueDescription.text = expectedExposureValueDescription
    }

    // Update focus distance switcher view if incorrect
    val focusDistancePreferred = preferences.getBoolean(
    de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_FOCUS_ENABLED_KEY,
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
    de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_ENABLED_KEY,
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
    if (binding.staticFocusDistanceSlider.visibility != expectedFocusDistanceVisibility || binding.staticFocusDistance.getVisibility() != expectedFocusDistanceVisibility || binding.staticFocusUnit.getVisibility() != expectedFocusDistanceVisibility) {
    Log.d(
    TAG, "updateView -> focus distance fields to "
    + if (expectedFocusDistanceVisibility == View.VISIBLE) "visible" else "invisible"
    )
    binding.staticFocusDistanceSlider.visibility = expectedFocusDistanceVisibility
    binding.staticFocusDistance.visibility = expectedFocusDistanceVisibility
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
    binding.staticFocusUnit.setText(unitDetails)

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
    }*/

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
        * /
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
        }*/
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
        val current = preferences.getBoolean(PREFERENCES_CENTER_MAP_KEY, false)
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
 * Handles UI changes of the 'slider' used to adjust the 'focus distance' setting.
 * /
private class StaticFocusDistanceSliderHandler(
private val preferences: SharedPreferences,
private val staticFocusDistance: TextView
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
val editor: Editor = preferences.edit()
editor.putFloat(
de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_FOCUS_DISTANCE_KEY,
roundedDistance
).apply()
val text = StringBuilder(roundedDistance.toString())
while (text.length < 4) {
text.append("0")
}
staticFocusDistance.text = text
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
val editor: Editor = preferences.edit()
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
private val staticFocusDistance: TextView,
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
de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_FOCUS_ENABLED_KEY,
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
val preferenceVisibilityOutOfSync = staticFocusDistance.visibility != expectedVisibility
val unitVisibilityOutOfSync = staticFocusUnit.visibility != expectedVisibility
if (sliderVisibilityOutOfSync || preferenceVisibilityOutOfSync || unitVisibilityOutOfSync) {
Log.d(
TAG,
"updateView -> " + if (expectedVisibility == View.VISIBLE) "visible" else "invisible"
)
staticFocusDistanceSlider.visibility = expectedVisibility
staticFocusDistance.visibility = expectedVisibility
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
 * /
 * private class StaticExposureTimeSliderHandler implements Slider.OnChangeListener {
 *
 * @Override
 * public void onValueChange(Slider slider, float newValue) {
 * Log.d(TAG, "Update preference to exposure time -> " + newValue);
 *
 * final long preferenceNanos = Math.round(preferences.getLong(PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_KEY,
 * DEFAULT_STATIC_EXPOSURE_TIME));
 * if (preferenceNanos == newValue) {
 * Log.d(TAG, "Preference already " + preferenceNanos + " ns, doing nothing");
 * return;
 * }
 *
 * final long value = (long)newValue;
 * final SharedPreferences.Editor editor = preferences.edit();
 * editor.putLong(PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_KEY, value).apply();
 *
 * staticExposureTimePreference.setText(getExposureTimeFraction(value));
 * }
 * }
*/

/*
 * Handles UI changes of the 'slider' used to adjust the 'exposure time' setting.
 * /
 * private class StaticExposureTimeSliderHandler implements Slider.OnChangeListener {
 *
 * @Override
 * public void onValueChange(Slider slider, float newValue) {
 * Log.d(TAG, "Update preference to exposure time -> " + newValue);
 *
 * final long preferenceNanos = Math.round(preferences.getLong(PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_KEY,
 * DEFAULT_STATIC_EXPOSURE_TIME));
 * if (preferenceNanos == newValue) {
 * Log.d(TAG, "Preference already " + preferenceNanos + " ns, doing nothing");
 * return;
 * }
 *
 * final long value = (long)newValue;
 * final SharedPreferences.Editor editor = preferences.edit();
 * editor.putLong(PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_KEY, value).apply();
 *
 * staticExposureTimePreference.setText(getExposureTimeFraction(value));
 * }
 * }
*/

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

/**
 * A listener which is called when the camera switch is clicked.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 2.0.0
 * /
private class PicturesToggleListener : CompoundButton.OnCheckedChangeListener {
override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
val applicationContext: Context = view.getContext().getApplicationContext()
val editor: Editor = preferences.edit()

// Disable camera
if (!buttonView.isChecked) {
editor.putBoolean(
PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY,
false
).apply()
return
}

// No rear camera found to be enabled - we explicitly only support rear camera for now
@SuppressLint("UnsupportedChromeOsCameraSystemFeature") val noCameraFound =
!applicationContext.packageManager
.hasSystemFeature(PackageManager.FEATURE_CAMERA)
if (noCameraFound) {
buttonView.isChecked = false
Toast.makeText(
applicationContext,
R.string.no_camera_available_toast,
Toast.LENGTH_LONG
).show()
return
}

// Request permission for camera capturing
val permissionsGranted = ActivityCompat.checkSelfPermission(
applicationContext,
Manifest.permission.CAMERA
) == PackageManager.PERMISSION_GRANTED
/* TODO: if (!permissionsGranted) {
ActivityCompat.requestPermissions(mainActivity,
new String[] {Manifest.permission.CAMERA},
PERMISSION_REQUEST_CAMERA_AND_STORAGE_PERMISSION);
} else {
// Ask user to select camera mode
mainActivity.showCameraModeDialog();
}*/

// Enable camera capturing feature
editor.putBoolean(
PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY,
true
).apply()
}
}*/

/**
 * Called when an exposure time was selected in the [ExposureTimeDialog].
 *
 * @param data an intent which may contain result data
 * /
fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
super.onActivityResult(requestCode, resultCode, data)
if (requestCode == DIALOG_EXPOSURE_TIME_SELECTION_REQUEST_CODE) {
val exposureTimeNanos = data.getLongExtra(
PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_KEY,
-1L
)
Validate.isTrue(exposureTimeNanos != -1L)
val fraction = Utils.getExposureTimeFraction(exposureTimeNanos)
Log.d(
TAG,
"Update view to exposure time -> $exposureTimeNanos ns - fraction: $fraction s"
)
staticExposureTime.setText(fraction)
}
}* /

/*
 * override fun onRequestPermissionsResult(
 * requestCode: Int, permissions: Array<String>,
 * grantResults: IntArray
 * ) {
 * // noinspection SwitchStatementWithTooFewBranches
 * when (requestCode) {
 * PERMISSION_REQUEST_CAMERA_AND_STORAGE_PERMISSION -> {
 * val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED
 * val unexpectedPermissionNumber = grantResults.size < 2
 * val missingPermissions =
 * !(granted && (unexpectedPermissionNumber || (grantResults[1] == PackageManager.PERMISSION_GRANTED)))
 *
 * if (missingPermissions) {
 * // Deactivate camera service and inform user about this
 * //navDrawer!!.deactivateCameraService()
 * Toast.makeText(
 * applicationContext,
 * applicationContext.getString(
 * de.cyface.camera_service.R.string.camera_service_off_missing_permissions
 * ),
 * Toast.LENGTH_LONG
 * ).show()
 * } else {
 * // Ask used which camera mode to use, video or default (shutter image)
 * showCameraModeDialog()
 * }
 * }
 * else -> {
 * super.onRequestPermissionsResult(requestCode, permissions, grantResults)
 * }
 * }
 * }
 *
 * // final SwitchCompat connectToExternalSpeedSensorToggle = (SwitchCompat)view.getMenu()
 * // .findItem(R.id.drawer_setting_speed_sensor).getActionView();
 * cameraServiceToggle = (SwitchCompat)view.getMenu().findItem(R.id.drawer_setting_pictures).getActionView();
 *
 * /*
 * final boolean bluetoothIsConfigured = preferences.getString(BLUETOOTHLE_DEVICE_MAC_KEY, null) != null
 * && preferences.getFloat(BLUETOOTHLE_WHEEL_CIRCUMFERENCE, 0.0F) > 0.0F;
 * connectToExternalSpeedSensorToggle.setChecked(bluetoothIsConfigured);
 * /
 * cameraServiceToggle.setChecked(preferences.getBoolean(PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY, false));
 *
 * // connectToExternalSpeedSensorToggle.setOnClickListener(new ConnectToExternalSpeedSensorToggleListener());
 * cameraServiceToggle.setOnCheckedChangeListener(new PicturesToggleListener());
public void deactivateCameraService() {
cameraServiceToggle.setChecked(false);
final SharedPreferences.Editor editor = preferences.edit();
editor.putBoolean(PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY, false);
editor.apply();
}

private void cameraSettingsSelected(final MenuItem item) {
for (NavDrawerListener listener : this.listener) {
listener.cameraSettingsSelected();
}
finishSelection(item);
}

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

/**
 * Displays a dialog for the user to select a camera mode (video- or picture mode).
 *
 * /
fun showCameraModeDialog() {

// avoid crash DAT-69
/*homeSelected()
val cameraModeDialog: DialogFragment = CameraModeDialog()
cameraModeDialog.setTargetFragment(
capturingFragment,
de.cyface.energy_settings.Constants.DIALOG_ENERGY_SAFER_WARNING_CODE
)
cameraModeDialog.isCancelable = false
cameraModeDialog.show(fragmentManager!!, "CAMERA_MODE_DIALOG")* /
}*/

/*override fun onRequestPermissionsResult(
requestCode: Int, permissions: Array<String?>, grantResults: IntArray
) {
@Suppress("UNUSED_EXPRESSION")
when (requestCode) {
// Location permission request moved to `MapFragment` as it has to react to results

/*PERMISSION_REQUEST_CAMERA_AND_STORAGE_PERMISSION -> if (navDrawer != null && !(grantResults[0] == PackageManager.PERMISSION_GRANTED
&& (grantResults.size < 2 || grantResults[1] == PackageManager.PERMISSION_GRANTED))
) {
// Deactivate camera service and inform user about this
navDrawer.deactivateCameraService()
Toast.makeText(
applicationContext,
applicationContext.getString(de.cyface.camera_service.R.string.camera_service_off_missing_permissions),
Toast.LENGTH_LONG
).show()
} else {
// Ask used which camera mode to use, video or default (shutter image)
showCameraModeDialog()
}* /
else -> {
super.onRequestPermissionsResult(requestCode, permissions, grantResults)
}
}
}*/
*/

 */