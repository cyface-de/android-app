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

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.widget.CompoundButton
import android.widget.Toast
import androidx.core.app.ActivityCompat
import de.cyface.app.digural.R

/**
 * Handles when the user toggles the camera switch.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 2.0.0
 */
class CameraSwitchHandler(
    private val viewModel: SettingsViewModel,
    private val fragment: SettingsFragment
) : CompoundButton.OnCheckedChangeListener {
    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        val context = fragment.requireContext()
        if (viewModel.cameraEnabled.value != isChecked) {
            if (!isChecked) {
                viewModel.setCameraEnabled(false)
            } else {

                // No rear camera found to be enabled - we explicitly only support rear camera for now
                @SuppressLint("UnsupportedChromeOsCameraSystemFeature")
                val noCameraFound =
                    !context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
                if (noCameraFound) {
                    buttonView!!.isChecked = false
                    Toast.makeText(context, R.string.no_camera_available_toast, Toast.LENGTH_LONG)
                        .show()
                    return
                }

                // Request permission for camera capturing
                val permissionsGranted = ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (!permissionsGranted) {
                    /*ActivityCompat.requestPermissions(
                        fragment.requireActivity(),
                        arrayOf(Manifest.permission.CAMERA),
                        PERMISSION_REQUEST_CAMERA_AND_STORAGE_PERMISSION
                    )*/
                    fragment.permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                } else {
                    // Ask user to select camera mode
                    fragment.onCameraEnabled(fragment)

                    viewModel.setCameraEnabled(true)
                }
            }
        }
    }
}