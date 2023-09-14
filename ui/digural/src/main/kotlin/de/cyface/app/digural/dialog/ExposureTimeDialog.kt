/*
 * Copyright 2017-2023 Cyface GmbH
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
package de.cyface.app.digural.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.DialogFragment
import de.cyface.app.digural.capturing.settings.SettingsFragment
import de.cyface.camera_service.Constants
import de.cyface.camera_service.settings.CameraSettings
import de.cyface.utils.Validate
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.round

/**
 * To allow the user to select the exposure time from a set of common values (1/125s, 1/250s, ...)
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 2.9.0
 */
class ExposureTimeDialog(private val cameraSettings: CameraSettings) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        builder.setTitle(de.cyface.camera_service.R.string.static_camera_exposure_time).setItems(
            itemsDescriptions
        ) { _: DialogInterface?, which: Int ->
            val fragmentActivity = activity
            Validate.notNull(fragmentActivity)
            // Pixel 3a reference device: EV100 10, f/1.8, 1/125s (2ms) => iso 40 (55 used, minimum)
            val exposureTimeNanos: Long = when (which) {
                0 -> round(1000000000.0 / 125).toLong()
                1 -> round(1000000000.0 / 250).toLong()
                2 -> round(1000000000.0 / 500).toLong()
                3 -> round(1000000000.0 / 1000).toLong()
                4 -> round(1000000000.0 / 2000).toLong()
                5 -> round(1000000000.0 / 4000).toLong()
                6 -> round(1000000000.0 / 8000).toLong()
                7 -> 54444 // shortest exposure time on Pixel 3a
                else -> throw IllegalArgumentException("Unknown exposure time selected: $which")
            }
            Log.d(Constants.TAG, "Update preference to exposure time -> $exposureTimeNanos ns")
            GlobalScope.launch { cameraSettings.setStaticExposureTime(exposureTimeNanos) }
            val requestCode = targetRequestCode
            val resultCode: Int
            val intent = Intent()
            if (requestCode == SettingsFragment.DIALOG_EXPOSURE_TIME_SELECTION_REQUEST_CODE) {

                resultCode = DIALOG_EXPOSURE_TIME_SELECTION_RESULT_CODE
                intent.putExtra(
                    CAMERA_STATIC_EXPOSURE_TIME_KEY,
                    exposureTimeNanos
                )
            } else {
                throw IllegalArgumentException("Unknown request code: $requestCode")
            }
            val targetFragment = targetFragment
            Validate.notNull(targetFragment)
            targetFragment!!.onActivityResult(requestCode, resultCode, intent)
        }
        return builder.create()
    }

    companion object {
        private const val DIALOG_EXPOSURE_TIME_SELECTION_RESULT_CODE = 202002171
        const val CAMERA_STATIC_EXPOSURE_TIME_KEY = "de.cyface.camera_service.static_exposure_time"
        private val itemsDescriptions = arrayOf<CharSequence>(
            "1/125 s", "1/250 s", "1/500 s",
            "1/1.000 s", "1/2.000 s", "1/4.000 s", "1/8.000 s", "1/18.367 s"
        )
    }
}