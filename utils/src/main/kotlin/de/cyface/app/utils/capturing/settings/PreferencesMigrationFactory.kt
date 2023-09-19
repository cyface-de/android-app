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
import android.util.Log
import androidx.datastore.migrations.SharedPreferencesMigration
import androidx.datastore.migrations.SharedPreferencesView
import de.cyface.app.utils.Settings
import de.cyface.app.utils.SharedConstants.TAG

/**
 * Factory for the migration which imports preferences from the previously used SharedPreferences.
 *
 * @author Armin Schnabel
 * @since 4.3.0
 */
object PreferencesMigrationFactory {

    /**
     * The filename, keys and defaults of the preferences, historically.
     *
     * *Don't change this, this is migration code!*
     */
    private const val PREFERENCES_NAME = "AppPreferences"
    private const val INCENTIVES_URL_KEY = "de.cyface.incentives.endpoint"

    /**
     * @param context The context to search and access the old SharedPreferences from.
     * @param incentivesUrl The URL of the Incentives API, e.g. "https://example.com/api/v1".
     * @return The migration code which imports preferences from the SharedPreferences if found.
     */
    fun create(context: Context, incentivesUrl: String): SharedPreferencesMigration<Settings> {
        return SharedPreferencesMigration(
            context,
            PREFERENCES_NAME,
            migrate = { preferences, settings ->
                migratePreferences(preferences, settings, incentivesUrl)
            }
        )
    }

    private fun migratePreferences(
        preferences: SharedPreferencesView,
        settings: Settings,
        incentivesUrl: String
    ): Settings {
        Log.i(TAG, "Migrating from shared preferences to version 1")
        return settings.toBuilder()
            // Setting version to 1 as it would else default to Protobuf default of 0 which would
            // trigger the StoreMigration from 0 -> 1 which ignores previous settings.
            // This way the last supported version of SharedPreferences is hard-coded here and
            // then the migration steps in StoreMigration starting at version 1 continues from here.
            .setVersion(1)
            .setIncentivesUrl(preferences.getString(INCENTIVES_URL_KEY, incentivesUrl))
            .build()
    }
}