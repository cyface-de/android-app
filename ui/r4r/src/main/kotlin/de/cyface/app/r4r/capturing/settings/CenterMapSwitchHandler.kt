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
package de.cyface.app.r4r.capturing.settings

import android.content.Context
import android.widget.CompoundButton
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import de.cyface.app.utils.R
import kotlinx.coroutines.launch

/**
 * Handles when the user toggles the center map switch.
 *
 * @author Armin Schnabel
 * @version 1.0.2
 * @since 3.2.0
 */
class CenterMapSwitchHandler(
    private val viewModel: SettingsViewModel,
    private val context: Context?
) : CompoundButton.OnCheckedChangeListener {

    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        if (viewModel.centerMap.value == isChecked) {
            return
        }

        viewModel.viewModelScope.launch {
            viewModel.setCenterMap(isChecked)
        }

        if (isChecked) {
            Toast.makeText(
                context,
                R.string.zoom_to_location_enabled_toast,
                Toast.LENGTH_LONG
            ).show()
        }
    }
}