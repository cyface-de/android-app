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
import android.preference.PreferenceManager
import androidx.core.content.edit
import de.cyface.app.utils.SharedConstants.DEFAULT_CENTER_MAP_VALUE
import de.cyface.app.utils.SharedConstants.DEFAULT_UPLOAD_VALUE
import de.cyface.app.utils.SharedConstants.PREFERENCES_CENTER_MAP_KEY
import de.cyface.app.utils.SharedConstants.PREFERENCES_SYNCHRONIZATION_KEY

/**
 * A helper class for managing and interacting with the SharedPreferences in the application.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.4.0
 * @param context The context to access the SharedPreferences from.
 */
class AppPreferences(context: Context) {

    /**
     * The preferences used to store the user's preferred settings.
     */
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    /**
     * Saves the centerMap boolean value to SharedPreferences.
     *
     * @param centerMap The boolean value indicating whether the map should be centered.
     */
    fun saveCenterMap(centerMap: Boolean) {
        preferences.edit {
            putBoolean(PREFERENCES_CENTER_MAP_KEY, centerMap)
            apply()
        }
    }

    /**
     * Saves the upload boolean value to SharedPreferences.
     *
     * @param upload The boolean value indicating whether data should be uploaded or synchronized.
     */
    fun saveUpload(upload: Boolean) {
        preferences.edit {
            putBoolean(PREFERENCES_SYNCHRONIZATION_KEY, upload)
            apply()
        }
    }

    /**
     * Retrieves the centerMap boolean value from SharedPreferences.
     *
     * @return The boolean value indicating whether the map should be centered.
     */
    fun getCenterMap(): Boolean {
        return preferences.getBoolean(PREFERENCES_CENTER_MAP_KEY, DEFAULT_CENTER_MAP_VALUE)
    }

    /**
     * Retrieves the upload boolean value from SharedPreferences.
     *
     * @return The boolean value indicating whether data should be uploaded or synchronized.
     */
    fun getUpload(): Boolean {
        return preferences.getBoolean(PREFERENCES_SYNCHRONIZATION_KEY, DEFAULT_UPLOAD_VALUE)
    }
}