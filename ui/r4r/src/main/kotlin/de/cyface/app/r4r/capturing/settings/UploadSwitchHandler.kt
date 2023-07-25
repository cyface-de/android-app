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
import de.cyface.app.utils.R
import de.cyface.datacapturing.CyfaceDataCapturingService

/**
 * Handles when the user toggles the upload switch.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.2.0
 */
class UploadSwitchHandler(
    private val viewModel: SettingsViewModel,
    private val context: Context?,
    private val capturingService: CyfaceDataCapturingService
) : CompoundButton.OnCheckedChangeListener {
    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        if (viewModel.upload.value != isChecked) {
            // Also update WifiSurveyor's synchronizationEnabled
            capturingService.wiFiSurveyor.isSyncEnabled = isChecked
            viewModel.setUpload(isChecked)

            // Show warning to user (storage gets filled)
            if (!isChecked) {
                Toast.makeText(
                    context,
                    R.string.sync_disabled_toast,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}