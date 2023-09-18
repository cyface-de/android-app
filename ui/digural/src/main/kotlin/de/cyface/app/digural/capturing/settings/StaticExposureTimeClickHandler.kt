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

import android.view.View
import androidx.fragment.app.FragmentManager
import de.cyface.app.digural.capturing.settings.SettingsFragment.Companion.DIALOG_EXPOSURE_TIME_SELECTION_REQUEST_CODE
import de.cyface.app.digural.dialog.ExposureTimeDialog
import de.cyface.utils.Validate

/**
 * Handles UI clicks on the exposure time used to adjust the 'exposure time' setting.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 */
class StaticExposureTimeClickHandler(
    private val fragmentManager: FragmentManager?,
    private val settingsFragment: SettingsFragment
) : View.OnClickListener {
    override fun onClick(v: View) {
        Validate.notNull(fragmentManager)
        val dialog = ExposureTimeDialog(settingsFragment.viewModel.cameraSettings)
        dialog.setTargetFragment(
            settingsFragment,
            DIALOG_EXPOSURE_TIME_SELECTION_REQUEST_CODE
        )
        dialog.isCancelable = true
        dialog.show(fragmentManager!!, "EXPOSURE_TIME_DIALOG")
    }
}