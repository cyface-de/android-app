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
package de.cyface.app.utils.capturing.settings

import android.content.Context
import androidx.core.content.edit
import de.cyface.app.utils.trips.incentives.Incentives
import de.cyface.utils.AppPreferences

/**
 * Preferences persisted with SharedPreferences.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.8.1
 */
class CustomPreferences(context: Context) : AppPreferences(context) {

    fun saveIncentivesUrl(incentivesUrl: String) {
        preferences.edit {
            putString(Incentives.INCENTIVES_ENDPOINT_URL_SETTINGS_KEY, incentivesUrl)
            apply()
        }
    }

    fun getIncentivesUrl(): String? {
        return preferences.getString(Incentives.INCENTIVES_ENDPOINT_URL_SETTINGS_KEY, null)
    }
}