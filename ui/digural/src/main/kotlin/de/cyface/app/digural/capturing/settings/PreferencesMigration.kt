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
import androidx.datastore.migrations.SharedPreferencesMigration
import androidx.datastore.migrations.SharedPreferencesView
import de.cyface.app.digural.Settings

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
    private const val DIGURAL_URL_KEY = "de.cyface.digural.server"
    private const val DEFAULT_URL = "http://localhost:33552/"

    /**
     * @param context The context to search and access the old SharedPreferences from.
     * @return The migration code which imports preferences from the SharedPreferences if found.
     */
    fun create(context: Context): SharedPreferencesMigration<Settings> {
        return SharedPreferencesMigration(
            context,
            PREFERENCES_NAME,
            migrate = ::migratePreferences
        )
    }

    private fun migratePreferences(
        preferences: SharedPreferencesView,
        settings: Settings
    ): Settings {
        return settings.toBuilder()
            .setVersion(1) // Ensure the migrated values below are used instead of default values.
            .setDiguralUrl(preferences.getString(DIGURAL_URL_KEY, DEFAULT_URL))
            .build()
    }
}