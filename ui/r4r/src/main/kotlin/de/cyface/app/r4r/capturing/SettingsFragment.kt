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
package de.cyface.app.r4r.capturing

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
import de.cyface.app.utils.ServiceProvider
import de.cyface.app.r4r.databinding.FragmentSettingsBinding
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

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        binding.centerMapSwitch.isChecked =
            preferences.getBoolean(PREFERENCES_CENTER_MAP_KEY, true)
        binding.uploadSwitch.isChecked =
            preferences.getBoolean(PREFERENCES_SYNCHRONIZATION_KEY, true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Handles when the user toggles the upload switch.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.2.0
 */
private class UploadSwitchHandler(
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
class CenterMapSwitchHandler(
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
