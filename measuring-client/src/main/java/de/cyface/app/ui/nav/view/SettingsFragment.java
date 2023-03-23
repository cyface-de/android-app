/*
 * Copyright 2017 Cyface GmbH
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
package de.cyface.app.ui.nav.view;

import static de.cyface.app.utils.SharedConstants.DEFAULT_SENSOR_FREQUENCY;
import static de.cyface.app.utils.Constants.PACKAGE;
import static de.cyface.app.utils.SharedConstants.PREFERENCES_SENSOR_FREQUENCY_KEY;
import static de.cyface.camera_service.Constants.DEFAULT_STATIC_EXPOSURE_TIME;
import static de.cyface.camera_service.Constants.DEFAULT_STATIC_EXPOSURE_VALUE_ISO_100;
import static de.cyface.camera_service.Constants.DEFAULT_STATIC_FOCUS_DISTANCE;
import static de.cyface.camera_service.Constants.DEFAULT_TRIGGERING_DISTANCE;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_DISTANCE_BASED_TRIGGERING_ENABLED_KEY;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_RAW_MODE_ENABLED_KEY;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_ENABLED_KEY;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_EXPOSURE_VALUE_KEY;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_KEY;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_FOCUS_DISTANCE_KEY;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_FOCUS_ENABLED_KEY;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_TRIGGERING_DISTANCE_KEY;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_VIDEO_MODE_ENABLED_KEY;
import static de.cyface.camera_service.Utils.getExposureTimeFraction;
import static de.cyface.camera_service.Utils.isFeatureSupported;

import java.util.SortedMap;
import java.util.TreeMap;

import com.google.android.material.slider.Slider;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import de.cyface.app.R;
import de.cyface.app.ui.dialog.ExposureTimeDialog;
import de.cyface.camera_service.Utils;
import de.cyface.utils.Validate;

/**
 * This {@link Fragment} is used to allow advanced users to configure the capturing modes.
 *
 * @author Armin Schnabel
 * @version 1.1.1
 * @since 2.7.0
 */
public class SettingsFragment extends Fragment {

    /**
     * The tag used to identify logging from this class.
     */
    private final static String TAG = PACKAGE + ".sf";
    /**
     * The {@code String} which represents the hardware camera focus distance calibration level.
     */
    private static final String FOCUS_DISTANCE_NOT_CALIBRATED = "uncalibrated";
    /**
     * Exposure value from tabulates values (for iso 100) for outdoor environment light settings
     * see https://en.wikipedia.org/wiki/Exposure_value#Tabulated_exposure_values
     */
    private static final SortedMap<Integer, String> EXPOSURE_VALUES = new TreeMap<Integer, String>() {
        {
            put(10, "twilight");
            put(11, "twilight");
            put(12, "deep shade");
            put(13, "cloudy, no shadows");
            put(14, "partly cloudy, soft shadows");
            put(15, "full sunlight");
            put(16, "sunny, snowy/sandy");
        }
    };
    /**
     * The unit of the {@link #staticExposureTimePreference} value shown in the <b>UI</b>.
     */
    private final static String EXPOSURE_TIME_UNIT = "s (click on time to change)";
    /**
     * The unit of the {@link #triggerDistancePreference} value shown in the <b>UI</b>.
     */
    private final static String TRIGGER_DISTANCE_UNIT = "m";
    /**
     * The current preference for camera enabled and camera mode (image, video).
     */
    private TextView cameraEnabledAndModePreference;
    /**
     * The {@code SwitchCompat} which allows the user to dis/enable the static camera focus feature.
     */
    private SwitchCompat staticFocusSwitcher;
    /**
     * The {@code Slider} which allows the user to adjust the static focus distance setting.
     */
    private Slider staticFocusDistanceSlider;
    /**
     * The current value of the preferred distance.
     */
    private TextView staticFocusDistancePreference;
    /**
     * The unit of the preferred distance.
     */
    private TextView staticFocusDistancePreferenceUnit;
    /**
     * The {@code SwitchCompat} which allows the user to dis/enable the distance based camera feature.
     */
    private SwitchCompat distanceBasedSwitcher;
    /**
     * The {@code Slider} which allows the user to adjust the triggering distance setting.
     */
    private Slider triggerDistanceSlider;
    /**
     * The current value of the preferred triggering distance.
     */
    private TextView triggerDistancePreference;
    /**
     * The unit of the preferred triggering distance.
     */
    private TextView triggerDistancePreferenceUnit;
    /**
     * The {@code SwitchCompat} which allows the user to dis/enable the static camera exposure time feature.
     */
    private SwitchCompat staticExposureTimeSwitcher;
    /*
     * The {@code Slider} which allows the user to adjust the static focus exposure time setting.
     */
    // private Slider staticExposureTimeSlider;
    /**
     * The current value of the preferred exposure time.
     */
    private TextView staticExposureTimePreference;
    /**
     * The unit of the preferred exposure time.
     */
    private TextView staticExposureTimePreferenceUnit;
    /**
     * The title for the preferred exposure value slider.
     */
    private TextView staticExposureValueTitle;
    /**
     * The {@code Slider} which allows the user to adjust the static exposure value setting.
     */
    private Slider staticExposureValueSlider;
    /**
     * The current value of the preferred exposure value.
     */
    private TextView staticExposureValuePreference;
    /**
     * The description of the preferred exposure value.
     */
    private TextView staticExposureValuePreferenceDescription;
    /**
     * The {@code Slider} which allows the user to adjust the sensor frequency setting.
     */
    private Slider sensorFrequencySlider;
    /**
     * The current value of the preferred sensor frequency.
     */
    private TextView sensorFrequencyPreference;
    /**
     * The preferences used to store the user's preferred settings.
     */
    private SharedPreferences preferences;
    /**
     * The {@code View} which is used to access the UI elements.
     */
    private View view;
    /**
     * {@code True} if the camera allows to control the sensors (focus, exposure, etc.) manually.
     */
    private boolean manualSensorSupported;
    /**
     * The identifier for the {@link ExposureTimeDialog} request.
     */
    public final static int DIALOG_EXPOSURE_TIME_SELECTION_REQUEST_CODE = 202002170;

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {

        view = inflater.inflate(R.layout.fragment_settings, container, false);
        final Context context = getContext();
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // Camera enable and mode preference
        cameraEnabledAndModePreference = view.findViewById(R.id.camera_settings_camera_status);

        // Static Focus Distance
        staticFocusSwitcher = view.findViewById(R.id.camera_settings_static_focus_switcher);
        staticFocusSwitcher.setOnCheckedChangeListener(new StaticFocusSwitcherHandler());
        staticFocusDistancePreference = view.findViewById(R.id.camera_settings_static_focus_preference);
        staticFocusDistanceSlider = view.findViewById(R.id.camera_settings_static_focus_distance_slider);
        staticFocusDistanceSlider.addOnChangeListener(new StaticFocusDistanceSliderHandler());
        staticFocusDistancePreferenceUnit = view.findViewById(R.id.camera_settings_static_focus_unit);
        final CameraCharacteristics characteristics = loadCameraCharacteristics(context);
        final Float minFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        // is null when camera permissions are missing, is 0.0 when "lens is fixed-focus" (e.g. emulators)
        // It's ok when this is not set in those cases as the fragment informs about missing manual focus mode
        if (minFocusDistance != null && minFocusDistance != 0.0) {
            staticFocusDistanceSlider.setValueTo(minFocusDistance);
        }

        // Distance based triggering
        distanceBasedSwitcher = view.findViewById(R.id.camera_settings_distance_based_switcher);
        distanceBasedSwitcher.setOnCheckedChangeListener(new DistanceBasedSwitcherHandler());
        triggerDistancePreference = view.findViewById(R.id.camera_settings_distance_based_preference);
        triggerDistanceSlider = view.findViewById(R.id.camera_settings_trigger_distance_slider);
        triggerDistanceSlider.addOnChangeListener(new TriggerDistanceSliderHandler());
        // triggerDistanceSlider.setValueTo(minFocusDistance);
        triggerDistancePreferenceUnit = view.findViewById(R.id.camera_settings_distance_based_unit);
        triggerDistancePreferenceUnit.setText(TRIGGER_DISTANCE_UNIT);

        // Static Exposure Time
        staticExposureTimeSwitcher = view.findViewById(R.id.camera_settings_static_exposure_time_switcher);
        staticExposureTimeSwitcher.setOnCheckedChangeListener(new StaticExposureTimeSwitcherHandler());
        staticExposureTimePreference = view.findViewById(R.id.camera_settings_static_exposure_time_preference);
        // staticExposureTimeSlider = view.findViewById(R.id.camera_settings_static_exposure_time_slider);
        // staticExposureTimeSlider.setOnChangeListener(new StaticExposureTimeSliderHandler());
        staticExposureTimePreference.setOnClickListener(new StaticExposureTimeClickHandler());
        staticExposureTimePreferenceUnit = view.findViewById(R.id.camera_settings_static_exposure_time_unit);
        staticExposureTimePreferenceUnit.setText(EXPOSURE_TIME_UNIT);
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

        // Static Exposure Value
        staticExposureValueTitle = view.findViewById(R.id.camera_settings_static_exposure_value_title);
        staticExposureValuePreference = view.findViewById(R.id.camera_settings_static_exposure_value_preference);
        staticExposureValueSlider = view.findViewById(R.id.camera_settings_static_exposure_value_slider);
        staticExposureValueSlider.addOnChangeListener(new StaticExposureValueSliderHandler());
        staticExposureValuePreferenceDescription = view
                .findViewById(R.id.camera_settings_static_exposure_value_preference_description);
        staticExposureValueSlider.setValueFrom(EXPOSURE_VALUES.firstKey());
        staticExposureValueSlider.setValueTo(EXPOSURE_VALUES.lastKey());

        // Show supported camera features
        this.manualSensorSupported = isFeatureSupported(characteristics,
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR);
        displaySupportLevelsAndUnits(characteristics, manualSensorSupported, minFocusDistance);

        // Sensor frequency
        sensorFrequencyPreference = view.findViewById(R.id.settings_sensor_frequency_preference);
        sensorFrequencySlider = view.findViewById(R.id.settings_sensor_frequency_slider);
        sensorFrequencySlider.addOnChangeListener(new SensorFrequencySliderHandler());

        return view;
    }

    /**
     * Returns the features supported by the camera hardware.
     *
     * @param context the base {@code Context} required to access system services
     * @return The hardware feature support
     */
    private CameraCharacteristics loadCameraCharacteristics(@Nullable final Context context) {

        Validate.isTrue(context != null); // Handle if this actually happens
        final CameraManager cameraManager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        try {
            Validate.notNull(cameraManager);
            final String cameraId = Utils.getMainRearCameraId(cameraManager, null);
            return cameraManager.getCameraCharacteristics(cameraId);
        } catch (final CameraAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateViewToPreference();
    }

    /**
     * Updates the view to the current preferences. This can be used to initialize the view.
     */
    private void updateViewToPreference() {

        // Update camera enabled and mode status view if incorrect
        final boolean cameraModeEnabledPreferred = preferences.getBoolean(PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY,
                false);
        final boolean videoModePreferred = preferences.getBoolean(PREFERENCES_CAMERA_VIDEO_MODE_ENABLED_KEY,
                false);
        final boolean rawModePreferred = preferences.getBoolean(PREFERENCES_CAMERA_RAW_MODE_ENABLED_KEY,
                false);
        final String cameraModeText = videoModePreferred ? "Video" : (rawModePreferred ? "DNG" : "JPEG");
        final String cameraStatusText = !cameraModeEnabledPreferred ? "disabled"
                : ("enabled, " + cameraModeText + " mode");
        if (cameraEnabledAndModePreference.getText() != cameraStatusText) {
            Log.d(TAG, "updateView -> camera mode view " + cameraStatusText);
            cameraEnabledAndModePreference.setText(cameraStatusText);
        }

        updateDistanceBasedTriggeringViewToPreference();

        // Update sensor frequency slider view if incorrect
        final int preferredSensorFrequency = preferences.getInt(PREFERENCES_SENSOR_FREQUENCY_KEY,
                DEFAULT_SENSOR_FREQUENCY);
        if (sensorFrequencySlider.getValue() != preferredSensorFrequency) {
            Log.d(TAG, "updateView -> sensor frequency slider " + preferredSensorFrequency);
            sensorFrequencySlider.setValue(preferredSensorFrequency);
        }
        if (sensorFrequencyPreference.getText().length() == 0
                || Integer.parseInt(sensorFrequencyPreference.getText().toString()) != preferredSensorFrequency) {
            Log.d(TAG, "updateView -> sensor frequency text " + preferredSensorFrequency);
            sensorFrequencyPreference.setText(String.valueOf(preferredSensorFrequency));
        }

        // Only check manual sensor settings if it's supported or else the app crashes
        if (manualSensorSupported) {
            updateManualSensorViewToPreferences();
        } else {
            staticFocusDistanceSlider.setVisibility(View.INVISIBLE);
            // staticExposureTimeSlider.setVisibility(View.INVISIBLE);
            staticExposureValueSlider.setVisibility(View.INVISIBLE);
        }
    }

    private void updateDistanceBasedTriggeringViewToPreference() {
        // Update slider view if incorrect
        final float preferredTriggerDistance = preferences.getFloat(PREFERENCES_CAMERA_TRIGGERING_DISTANCE_KEY,
                DEFAULT_TRIGGERING_DISTANCE);
        final float roundedDistance = Math.round(preferredTriggerDistance * 100) / 100f;
        if (triggerDistanceSlider.getValue() != roundedDistance) {
            Log.d(TAG, "updateView -> triggering distance slider " + roundedDistance);
            triggerDistanceSlider.setValue(roundedDistance);
        }
        if (triggerDistancePreference.getText().length() == 0
                || Float.parseFloat(triggerDistancePreference.getText().toString()) != roundedDistance) {
            Log.d(TAG, "updateView -> triggering distance text " + roundedDistance);
            triggerDistancePreference.setText(String.valueOf(roundedDistance));
        }

        // Update switcher view if incorrect
        final boolean distanceBasedTriggeringPreferred = preferences.getBoolean(
                PREFERENCES_CAMERA_DISTANCE_BASED_TRIGGERING_ENABLED_KEY,
                true);
        if (distanceBasedSwitcher.isChecked(    ) != distanceBasedTriggeringPreferred) {
            Log.d(TAG, "updateView distance based triggering switcher -> " + distanceBasedTriggeringPreferred);
            distanceBasedSwitcher.setChecked(distanceBasedTriggeringPreferred);

        }

        // Update visibility slider + distance field
        final int expectedTriggerDistanceVisibility = distanceBasedTriggeringPreferred ? View.VISIBLE : View.INVISIBLE;
        Log.d(TAG, "view status -> distance triggering fields are " + triggerDistanceSlider.getVisibility()
                + "expected is: "
                + (expectedTriggerDistanceVisibility == View.VISIBLE ? "visible" : "invisible"));
        if (triggerDistanceSlider.getVisibility() != expectedTriggerDistanceVisibility
                || triggerDistancePreference.getVisibility() != expectedTriggerDistanceVisibility
                || triggerDistancePreferenceUnit.getVisibility() != expectedTriggerDistanceVisibility) {
            Log.d(TAG, "updateView -> distance triggering fields to "
                    + (expectedTriggerDistanceVisibility == View.VISIBLE ? "visible" : "invisible"));
            triggerDistanceSlider.setVisibility(expectedTriggerDistanceVisibility);
            triggerDistancePreference.setVisibility(expectedTriggerDistanceVisibility);
            triggerDistancePreferenceUnit.setVisibility(expectedTriggerDistanceVisibility);
        }
    }

    private void updateManualSensorViewToPreferences() {
        // Update focus distance slider view if incorrect
        final float preferredFocusDistance = preferences.getFloat(PREFERENCES_CAMERA_STATIC_FOCUS_DISTANCE_KEY,
                DEFAULT_STATIC_FOCUS_DISTANCE);
        final float roundedDistance = Math.round(preferredFocusDistance * 100) / 100f;
        if (staticFocusDistanceSlider.getValue() != roundedDistance) {
            Log.d(TAG, "updateView -> focus distance slider " + roundedDistance);
            staticFocusDistanceSlider.setValue(roundedDistance);
        }
        if (staticFocusDistancePreference.getText().length() == 0
                || Float.parseFloat(staticFocusDistancePreference.getText().toString()) != roundedDistance) {
            Log.d(TAG, "updateView -> focus distance text " + roundedDistance);
            staticFocusDistancePreference.setText(String.valueOf(roundedDistance));
        }

        // Update exposure time slider view if incorrect
        final long preferredExposureTimeNanos = preferences.getLong(PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_KEY,
                DEFAULT_STATIC_EXPOSURE_TIME);
        /*
         * if (staticExposureTimeSlider.getValue() != preferredExposureTimeNanos) {
         * Log.d(TAG, "updateView -> exposure time slider " + preferredExposureTimeNanos);
         * staticExposureTimeSlider.setValue(preferredExposureTimeNanos);
         * }
         */
        if (staticExposureTimePreference.getText().length() == 0
                || !staticExposureTimePreference.getText().toString()
                        .equals(getExposureTimeFraction(preferredExposureTimeNanos))) {
            Log.d(TAG, "updateView -> exposure time text " + preferredExposureTimeNanos);
            staticExposureTimePreference.setText(getExposureTimeFraction(preferredExposureTimeNanos));
        }

        // Update exposure value slider view if incorrect
        final int preferredExposureValue = preferences.getInt(
                PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_EXPOSURE_VALUE_KEY,
                DEFAULT_STATIC_EXPOSURE_VALUE_ISO_100);
        if (staticExposureValueSlider.getValue() != preferredExposureValue) {
            Log.d(TAG, "updateView -> exposure value slider " + preferredExposureValue);
            staticExposureValueSlider.setValue(preferredExposureValue);
        }
        if (staticExposureValuePreference.getText().length() == 0
                || Float.parseFloat(staticExposureValuePreference.getText().toString()) != preferredExposureValue) {
            Log.d(TAG, "updateView -> exposure value text " + preferredExposureValue);
            staticExposureValuePreference.setText(String.valueOf(preferredExposureValue));
        }
        // Update exposure value description view if incorrect
        final String expectedExposureValueDescription = EXPOSURE_VALUES.get(preferredExposureValue);
        if (!staticExposureValuePreferenceDescription.getText().equals(expectedExposureValueDescription)) {
            Log.d(TAG, "updateView -> exposure value description " + expectedExposureValueDescription);
            staticExposureValuePreferenceDescription.setText(expectedExposureValueDescription);
        }

        // Update focus distance switcher view if incorrect
        final boolean focusDistancePreferred = preferences.getBoolean(PREFERENCES_CAMERA_STATIC_FOCUS_ENABLED_KEY,
                false);
        if (staticFocusSwitcher.isChecked() != focusDistancePreferred) {
            Log.d(TAG, "updateView focus distance switcher -> " + focusDistancePreferred);
            staticFocusSwitcher.setChecked(focusDistancePreferred);
        }

        // Update exposure time switcher view if incorrect
        final boolean exposureTimePreferred = preferences
                .getBoolean(PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_ENABLED_KEY, false);
        if (staticExposureTimeSwitcher.isChecked() != exposureTimePreferred) {
            Log.d(TAG, "updateView exposure time switcher -> " + exposureTimePreferred);
            staticExposureTimeSwitcher.setChecked(exposureTimePreferred);
        }

        // Update visibility of focus distance slider + distance field
        final int expectedFocusDistanceVisibility = focusDistancePreferred ? View.VISIBLE : View.INVISIBLE;
        if (staticFocusDistanceSlider.getVisibility() != expectedFocusDistanceVisibility
                || staticFocusDistancePreference.getVisibility() != expectedFocusDistanceVisibility
                || staticFocusDistancePreferenceUnit.getVisibility() != expectedFocusDistanceVisibility) {
            Log.d(TAG, "updateView -> focus distance fields to "
                    + (expectedFocusDistanceVisibility == View.VISIBLE ? "visible" : "invisible"));
            staticFocusDistanceSlider.setVisibility(expectedFocusDistanceVisibility);
            staticFocusDistancePreference.setVisibility(expectedFocusDistanceVisibility);
            staticFocusDistancePreferenceUnit.setVisibility(expectedFocusDistanceVisibility);
        }

        // Update visibility of exposure time and value slider + time and value fields
        final int expectedExposureTimeVisibility = exposureTimePreferred ? View.VISIBLE : View.INVISIBLE;
        if (/*
             * staticExposureTimeSlider.getVisibility() != expectedExposureTimeVisibility
             * ||
             */ staticExposureTimePreference.getVisibility() != expectedExposureTimeVisibility
                || staticExposureTimePreferenceUnit.getVisibility() != expectedExposureTimeVisibility
                || staticExposureValueTitle.getVisibility() != expectedExposureTimeVisibility
                || staticExposureValueSlider.getVisibility() != expectedExposureTimeVisibility
                || staticExposureValuePreference.getVisibility() != expectedExposureTimeVisibility
                || staticExposureValuePreferenceDescription.getVisibility() != expectedExposureTimeVisibility) {
            Log.d(TAG, "updateView -> exposure time fields to "
                    + (expectedExposureTimeVisibility == View.VISIBLE ? "visible" : "invisible"));
            // staticExposureTimeSlider.setVisibility(expectedExposureTimeVisibility);
            staticExposureTimePreference.setVisibility(expectedExposureTimeVisibility);
            staticExposureTimePreferenceUnit.setVisibility(expectedExposureTimeVisibility);
            staticExposureValueTitle.setVisibility(expectedExposureTimeVisibility);
            staticExposureValueSlider.setVisibility(expectedExposureTimeVisibility);
            staticExposureValuePreference.setVisibility(expectedExposureTimeVisibility);
            staticExposureValuePreferenceDescription.setVisibility(expectedExposureTimeVisibility);
        }
    }

    /**
     * Displays the supported camera2 features in the view.
     *
     * @param characteristics the features to check
     * @param isManualSensorSupported {@code True} if the 'manual sensor' feature is supported
     * @param minFocusDistance the minimum focus distance supported
     */
    private void displaySupportLevelsAndUnits(final CameraCharacteristics characteristics,
            final boolean isManualSensorSupported, final Float minFocusDistance) {

        // Display camera2 API support level
        final String camera2Level = getCamera2SupportLevel(characteristics);
        final TextView camera2View = view.findViewById(R.id.camera_settings_hardware_support);
        camera2View.setText(camera2Level);

        // Display 'manual sensor' support
        final TextView manualSensorView = view.findViewById(R.id.camera_settings_manual_sensor_support);
        manualSensorView.setText(isManualSensorSupported ? "supported" : "no supported");

        // Display whether the focus distance setting is calibrated (i.e. has a unit)
        final TextView focusCalibrationView = view.findViewById(R.id.camera_settings_focus_calibration);
        final String calibrationLevel = getFocusDistanceCalibration(characteristics);
        final String unitDetails = calibrationLevel.equals(FOCUS_DISTANCE_NOT_CALIBRATED) ? "uncalibrated" : "dioptre";
        final String unit = calibrationLevel.equals(FOCUS_DISTANCE_NOT_CALIBRATED) ? "" : " [dioptre]";
        focusCalibrationView.setText(calibrationLevel);
        staticFocusDistancePreferenceUnit.setText(unitDetails);

        // Display focus distance ranges
        final TextView hyperFocalDistanceView = view.findViewById(R.id.camera_settings_hyper_focal_distance);
        final TextView shortestFocusDistanceView = view.findViewById(R.id.camera_settings_minimum_focus_distance);
        final Float hyperFocalDistance = characteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE);
        final String hyperFocalDistanceText = hyperFocalDistance + unit;
        final String shortestFocalDistanceText = minFocusDistance + unit;
        hyperFocalDistanceView.setText(hyperFocalDistanceText);
        shortestFocusDistanceView.setText(shortestFocalDistanceText);
    }

    /**
     * Returns the hardware calibration of the focus distance feature as simple {@code String}.
     * <p>
     * If "uncalibrated" the focus distance does not have a unit, else it's in dioptre [1/m], more details:
     * https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#LENS_INFO_FOCUS_DISTANCE_CALIBRATION
     *
     * @param characteristics the hardware feature set to check
     * @return the calibration level as {@code String}
     */
    private String getFocusDistanceCalibration(final CameraCharacteristics characteristics) {

        final Integer focusDistanceCalibration = characteristics
                .get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION);
        if (focusDistanceCalibration == null) return "N/A";

        switch (focusDistanceCalibration) {
            case CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED:
                return "calibrated";
            case CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE:
                return "approximate";
            case CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED:
                return FOCUS_DISTANCE_NOT_CALIBRATED;
            default:
                return "unknown: " + focusDistanceCalibration;
        }
    }

    /**
     * Returns the support level for the 'camera2' API, see http://stackoverflow.com/a/31240881/5815054.
     *
     * @param characteristics the camera hardware features to check
     * @return the support level as simple {@code String}
     */
    private String getCamera2SupportLevel(final CameraCharacteristics characteristics) {

        final Integer supportedHardwareLevel = characteristics
                .get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        String supportLevel = "unknown";
        if (supportedHardwareLevel != null) {
            switch (supportedHardwareLevel) {
                case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                    supportLevel = "legacy";
                    break;
                case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                    supportLevel = "limited";
                    break;
                case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                    supportLevel = "full";
                    break;
            }
        }
        return supportLevel;
    }

    /**
     * Handles UI changes of the 'slider' used to adjust the 'focus distance' setting.
     */
    private class StaticFocusDistanceSliderHandler implements Slider.OnChangeListener {

        @Override
        public void onValueChange(@NonNull Slider slider, float newValue, boolean fromUser) {

            final float roundedDistance = Math.round(newValue * 100) / 100f;
            Log.d(TAG, "Update preference to focus distance -> " + roundedDistance);

            final float preference = preferences.getFloat(PREFERENCES_CAMERA_STATIC_FOCUS_DISTANCE_KEY,
                    DEFAULT_STATIC_FOCUS_DISTANCE);
            if (preference == roundedDistance) {
                Log.d(TAG, "Preference already " + preference + ", doing nothing");
                return;
            }

            final SharedPreferences.Editor editor = preferences.edit();
            editor.putFloat(PREFERENCES_CAMERA_STATIC_FOCUS_DISTANCE_KEY, roundedDistance).apply();
            StringBuilder text = new StringBuilder(String.valueOf(roundedDistance));
            while (text.length() < 4) {
                text.append("0");
            }
            staticFocusDistancePreference.setText(text);
        }
    }

    /**
     * Handles UI changes of the 'slider' used to adjust the 'triggering distance' setting.
     */
    private class TriggerDistanceSliderHandler implements Slider.OnChangeListener {

        @Override
        public void onValueChange(@NonNull Slider slider, float newValue, boolean fromUser) {

            final float roundedDistance = Math.round(newValue * 100) / 100f;
            Log.d(TAG, "Update preference to triggering distance -> " + roundedDistance);

            final float preference = preferences.getFloat(PREFERENCES_CAMERA_TRIGGERING_DISTANCE_KEY,
                    DEFAULT_TRIGGERING_DISTANCE);
            if (preference == roundedDistance) {
                Log.d(TAG, "Preference already " + preference + ", doing nothing");
                return;
            }

            final SharedPreferences.Editor editor = preferences.edit();
            editor.putFloat(PREFERENCES_CAMERA_TRIGGERING_DISTANCE_KEY, roundedDistance).apply();
            StringBuilder text = new StringBuilder(String.valueOf(roundedDistance));
            while (text.length() < 4) {
                text.append("0");
            }
            triggerDistancePreference.setText(text);
        }
    }

    /**
     * Handles UI changes of the 'switcher' used to en-/disable 'static focus' feature.
     */
    private class StaticFocusSwitcherHandler implements CompoundButton.OnCheckedChangeListener {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (!manualSensorSupported && isChecked) {
                Toast.makeText(getContext(), "This device does not support manual focus control", Toast.LENGTH_LONG)
                        .show();
                staticFocusSwitcher.setChecked(false);
                return;
            }

            Log.d(TAG, "Update preference to focus -> " + (isChecked ? "manual" : "auto"));

            final boolean preference = preferences.getBoolean(PREFERENCES_CAMERA_STATIC_FOCUS_ENABLED_KEY,
                    false);
            if (preference == isChecked) {
                Log.d(TAG, "Preference already " + preference + ", doing nothing");
                return;
            }

            final SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(PREFERENCES_CAMERA_STATIC_FOCUS_ENABLED_KEY, isChecked).apply();

            if (isChecked) {
                Toast.makeText(getContext(), R.string.experimental_feature_warning, Toast.LENGTH_LONG).show();
            }
            // Update visibility of slider
            final int expectedVisibility = isChecked ? View.VISIBLE : View.INVISIBLE;
            final boolean sliderVisibilityOutOfSync = staticFocusDistanceSlider.getVisibility() != expectedVisibility;
            final boolean preferenceVisibilityOutOfSync = staticFocusDistancePreference
                    .getVisibility() != expectedVisibility;
            final boolean unitVisibilityOutOfSync = staticFocusDistancePreferenceUnit
                    .getVisibility() != expectedVisibility;
            if (sliderVisibilityOutOfSync || preferenceVisibilityOutOfSync || unitVisibilityOutOfSync) {
                Log.d(TAG, "updateView -> " + (expectedVisibility == View.VISIBLE ? "visible" : "invisible"));
                staticFocusDistanceSlider.setVisibility(expectedVisibility);
                staticFocusDistancePreference.setVisibility(expectedVisibility);
                staticFocusDistancePreferenceUnit.setVisibility(expectedVisibility);
            }
        }
    }

    /**
     * Handles UI changes of the 'switcher' used to en-/disable 'distance based triggering' feature.
     */
    private class DistanceBasedSwitcherHandler implements CompoundButton.OnCheckedChangeListener {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            Log.d(TAG, "Update preference to distance-based-trigger -> " + isChecked);

            final boolean preference = preferences.getBoolean(PREFERENCES_CAMERA_DISTANCE_BASED_TRIGGERING_ENABLED_KEY,
                    true);
            if (preference == isChecked) {
                Log.d(TAG, "Preference already " + preference + ", doing nothing");
                return;
            }

            final SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(PREFERENCES_CAMERA_DISTANCE_BASED_TRIGGERING_ENABLED_KEY, isChecked).apply();

            if (isChecked) {
                Toast.makeText(getContext(), R.string.experimental_feature_warning, Toast.LENGTH_LONG).show();
            }
            // Update visibility of slider
            final int expectedVisibility = isChecked ? View.VISIBLE : View.INVISIBLE;
            final boolean sliderVisibilityOutOfSync = triggerDistanceSlider.getVisibility() != expectedVisibility;
            final boolean preferenceVisibilityOutOfSync = triggerDistancePreference
                    .getVisibility() != expectedVisibility;
            final boolean unitVisibilityOutOfSync = triggerDistancePreferenceUnit
                    .getVisibility() != expectedVisibility;
            if (sliderVisibilityOutOfSync || preferenceVisibilityOutOfSync || unitVisibilityOutOfSync) {
                Log.d(TAG, "updateView -> " + (expectedVisibility == View.VISIBLE ? "visible" : "invisible"));
                triggerDistanceSlider.setVisibility(expectedVisibility);
                triggerDistancePreference.setVisibility(expectedVisibility);
                triggerDistancePreferenceUnit.setVisibility(expectedVisibility);
            }
        }
    }

    /**
     * Handles UI clicks on the exposure time used to adjust the 'exposure time' setting.
     */
    private class StaticExposureTimeClickHandler implements TextView.OnClickListener {

        @Override
        public void onClick(View v) {

            Log.d(TAG, "StaticExposureTimeClickHandler triggered, showing ExposureTimeDialog");

            final FragmentManager fragmentManager = getFragmentManager();
            Validate.notNull(fragmentManager);
            final ExposureTimeDialog dialog = new ExposureTimeDialog();
            dialog.setTargetFragment(SettingsFragment.this, DIALOG_EXPOSURE_TIME_SELECTION_REQUEST_CODE);
            dialog.setCancelable(true);
            dialog.show(fragmentManager, "EXPOSURE_TIME_DIALOG");
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

    /**
     * Handles UI changes of the 'switcher' used to en-/disable 'static exposure time' feature.
     */
    private class StaticExposureTimeSwitcherHandler implements CompoundButton.OnCheckedChangeListener {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (!manualSensorSupported && isChecked) {
                Toast.makeText(getContext(), "This device does not support manual exposure control", Toast.LENGTH_LONG)
                        .show();
                staticExposureTimeSwitcher.setChecked(false);
                return;
            }

            Log.d(TAG, "Update preference to exposure -> " + (isChecked ? "Tv-/S-Mode" : "auto"));

            final boolean preference = preferences.getBoolean(PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_ENABLED_KEY,
                    false);
            if (preference == isChecked) {
                Log.d(TAG, "Preference already " + preference + ", doing nothing");
                return;
            }

            final SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_ENABLED_KEY, isChecked).apply();

            if (isChecked) {
                Toast.makeText(getContext(), R.string.experimental_feature_warning, Toast.LENGTH_LONG).show();
            }
            // Update visibility of slider
            final int expectedVisibility = isChecked ? View.VISIBLE : View.INVISIBLE;
            // final boolean sliderVisibilityOutOfSync = staticExposureTimeSlider.getVisibility() != expectedVisibility;
            final boolean preferenceVisibilityOutOfSync = staticExposureTimePreference
                    .getVisibility() != expectedVisibility;
            final boolean unitVisibilityOutOfSync = staticExposureTimePreferenceUnit
                    .getVisibility() != expectedVisibility;
            final boolean evTitleVisibilityOutOfSync = staticExposureValueTitle.getVisibility() != expectedVisibility;
            final boolean evSliderVisibilityOutOfSync = staticExposureValueSlider.getVisibility() != expectedVisibility;
            final boolean evPreferenceVisibilityOutOfSync = staticExposureValuePreference
                    .getVisibility() != expectedVisibility;
            final boolean evDescriptionVisibilityOutOfSync = staticExposureValuePreferenceDescription
                    .getVisibility() != expectedVisibility;
            if (/* sliderVisibilityOutOfSync || */preferenceVisibilityOutOfSync || unitVisibilityOutOfSync
                    || evTitleVisibilityOutOfSync || evSliderVisibilityOutOfSync || evPreferenceVisibilityOutOfSync
                    || evDescriptionVisibilityOutOfSync) {
                Log.d(TAG, "updateView -> " + (expectedVisibility == View.VISIBLE ? "visible" : "invisible"));
                // staticExposureTimeSlider.setVisibility(expectedVisibility);
                staticExposureTimePreference.setVisibility(expectedVisibility);
                staticExposureTimePreferenceUnit.setVisibility(expectedVisibility);
                staticExposureValueTitle.setVisibility(expectedVisibility);
                staticExposureValueSlider.setVisibility(expectedVisibility);
                staticExposureValuePreference.setVisibility(expectedVisibility);
                staticExposureValuePreferenceDescription.setVisibility(expectedVisibility);
            }
        }
    }

    /**
     * Handles UI changes of the 'slider' used to adjust the 'exposure value' setting.
     */
    private class StaticExposureValueSliderHandler implements Slider.OnChangeListener {

        @Override
        public void onValueChange(@NonNull Slider slider, float newValue, boolean fromUser) {
            Log.d(TAG, "Update preference to exposure value -> " + newValue);

            final int preference = preferences.getInt(PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_EXPOSURE_VALUE_KEY,
                    DEFAULT_STATIC_EXPOSURE_VALUE_ISO_100);
            if (preference == newValue) {
                Log.d(TAG, "Preference already " + preference + ", doing nothing");
                return;
            }

            final int value = (int)newValue;
            final String description = EXPOSURE_VALUES.get(value);
            final SharedPreferences.Editor editor = preferences.edit();
            editor.putInt(PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_EXPOSURE_VALUE_KEY, value).apply();
            staticExposureValuePreference.setText(String.valueOf(value));
            staticExposureValuePreferenceDescription.setText(description);
        }
    }

    /**
     * Handles UI changes of the 'slider' used to adjust the 'sensor frequency' setting.
     */
    private class SensorFrequencySliderHandler implements Slider.OnChangeListener {

        @Override
        public void onValueChange(@NonNull Slider slider, float newValue, boolean fromUser) {

            Log.d(TAG, "Update preference to sensor frequency -> " + newValue);

            final int newSensorFrequency = (int)newValue;
            final int preference = preferences.getInt(PREFERENCES_SENSOR_FREQUENCY_KEY, DEFAULT_SENSOR_FREQUENCY);
            if (preference == newSensorFrequency) {
                Log.d(TAG, "Preference already " + preference + ", doing nothing");
                return;
            }

            final SharedPreferences.Editor editor = preferences.edit();
            editor.putInt(PREFERENCES_SENSOR_FREQUENCY_KEY, newSensorFrequency).apply();
            StringBuilder text = new StringBuilder(String.valueOf(newSensorFrequency));
            sensorFrequencyPreference.setText(text);
        }
    }

    /**
     * Called when an exposure time was selected in the {@link ExposureTimeDialog}.
     *
     * @param data an intent which may contain result data
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == DIALOG_EXPOSURE_TIME_SELECTION_REQUEST_CODE) {

            final long exposureTimeNanos = data.getLongExtra(PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_KEY, -1L);
            Validate.isTrue(exposureTimeNanos != -1L);

            final String fraction = getExposureTimeFraction(exposureTimeNanos);
            Log.d(TAG, "Update view to exposure time -> " + exposureTimeNanos + " ns - fraction: " + fraction + " s");
            staticExposureTimePreference.setText(fraction);
        }
    }
}
