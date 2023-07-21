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
    }

    companion object {
        /**
         * The tag used to identify logging from this class.
         */
        const val TAG = Constants.PACKAGE + ".sf"
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