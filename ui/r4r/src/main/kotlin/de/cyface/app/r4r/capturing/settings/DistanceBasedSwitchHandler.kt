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
package de.cyface.app.r4r.capturing.settings

import android.content.Context
import android.widget.CompoundButton
import android.widget.Toast

/**
 * Handles UI changes of the 'switcher' used to en-/disable 'distance based triggering' feature.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 */
class DistanceBasedSwitchHandler(
    private val context: Context,
    private val viewModel: SettingsViewModel
) : CompoundButton.OnCheckedChangeListener {
    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (viewModel.distanceBasedTriggering.value == isChecked) {
            return
        }

        viewModel.setDistanceBasedTriggering(isChecked)

        if (isChecked) {
            Toast.makeText(
                context,
                de.cyface.camera_service.R.string.experimental_feature_warning,
                Toast.LENGTH_LONG
            ).show()
        }
    }
}