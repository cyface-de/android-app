/*
 * Copyright 2023-2025 Cyface GmbH
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
import android.util.Log
import android.widget.CompoundButton
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import de.cyface.app.digural.capturing.settings.SettingsFragment.Companion.TAG
import kotlinx.coroutines.launch

/**
 * Handles UI changes of the 'switcher' used to en-/disable 'distance based triggering' feature.
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 3.2.0
 */
class DistanceBasedSwitchHandler(
    private val context: Context,
    private val viewModel: SettingsViewModel
) : CompoundButton.OnCheckedChangeListener {
    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (viewModel.distanceBasedTriggering.value == isChecked) {
            return
        }

        viewModel.viewModelScope.launch {
            viewModel.setDistanceBasedTriggering(isChecked)
        }

        if (isChecked) {
            Toast.makeText(
                context,
                de.cyface.camera_service.R.string.experimental_feature_warning,
                Toast.LENGTH_LONG
            ).show()
        }
    }
}