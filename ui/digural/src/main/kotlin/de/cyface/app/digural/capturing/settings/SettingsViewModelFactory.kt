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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.cyface.camera_service.CameraPreferences
import de.cyface.utils.AppPreferences

/**
 * Factory which creates the [ViewModel] with the required dependencies.
 *
 * Survives configuration changes and returns the right instance after Activity recreation.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.4.0
 * @param appPreferences Persistence storage of the app preferences.
 * @param cameraPreferences Persistence storage of the camera preferences.
 */
class SettingsViewModelFactory(
    private val appPreferences: AppPreferences,
    private val cameraPreferences: CameraPreferences,
    private val diGuRaLPreferences: DiGuRaLPreferences
) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(appPreferences, cameraPreferences, diGuRaLPreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}