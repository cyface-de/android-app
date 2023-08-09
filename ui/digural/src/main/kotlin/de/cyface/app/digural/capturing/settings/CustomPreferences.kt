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

import android.content.Context
import androidx.core.content.edit
import de.cyface.utils.AppPreferences
import java.net.URL

/**
 * This class is responsible for storing and retrieving preferences specific to the DiGuRaL project.
 */
class CustomPreferences(context: Context) : AppPreferences(context) {

    fun saveDiguralUrl(address: URL) {
        preferences.edit {
            putString(DIGURAL_SERVER_URL_SETTINGS_KEY, address.toExternalForm())
            apply()
        }
    }

    fun getDiguralUrl(): URL {
        val addressString = preferences.getString(
            DIGURAL_SERVER_URL_SETTINGS_KEY,
            DEFAULT_URL
        )
        return URL(addressString)
    }

    companion object {
        /**
         * The settings key used to identify the settings storing the URL of the server to share
         * camera trigger events with, e.g. to trigger an external camera.
         */
        const val DIGURAL_SERVER_URL_SETTINGS_KEY = "de.cyface.digural.server"

        private const val DEFAULT_URL = "http://localhost:33552/"
    }
}