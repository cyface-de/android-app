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
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import de.cyface.app.digural.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.net.URL

/**
 * This class is responsible for storing and retrieving settings specific to the DiGuRaL project.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 3.4.0
 */
class CustomSettings(context: Context) {

    /**
     * This avoids leaking the context when this object outlives the Activity of Fragment.
     */
    private val appContext = context.applicationContext

    /**
     * The data store with multi-process support.
     *
     * The reason for multi-process support is that the digural url is accessed by BackgroundService
     * and is not updated until the app restarts when changed otherwise (tested with the old
     * SharedPreferences).
     *
     * Attention:
     * - Never mix SingleProcessDataStore with MultiProcessDataStore for the same file.
     * - We use MultiProcessDataStore, i.e. the preferences can be accessed from multiple processes.
     * - Only create one instance of `DataStore` per file in the same process.
     * - We use ProtoBuf to ensure type safety. Rebuild after changing the .proto file.
     */
    private val dataStore: DataStore<Settings> = MultiProcessDataStoreFactory.create(
        serializer = SettingsSerializer,
        produceFile = {
            File("${appContext.cacheDir.path}/ui.pb")
        },
        // TODO [RFR-788]: Add a test to ensure version is not set to 1 if no SharedPreferences file exist
        // TODO [RFR-788]: Add a test which ensures preferences migration works and not default values are used
        // TODO [RFR-788]: Add a test where the version is already 1 and SharedPreferences file is found
        // TODO [RFR-788]: Add a test where the version is 1 and ensure no migration is executed / defaults are set
        migrations = listOf(
            PreferencesMigrationFactory.create(appContext),
            StoreMigration()
        )
    )

    /**
     * Saves the URL of the server to inform about events, e.g. to trigger an external camera.
     *
     * @param value The url to save.
     */
    suspend fun setDiguralUrl(value: URL) {
        dataStore.updateData { currentSettings ->
            currentSettings.toBuilder()
                .setDiguralUrl(value.toExternalForm())
                .build()
        }
    }

    /**
     * @return The URL of the server to inform about events, e.g. to trigger an external camera.
     */
    val diguralUrlFlow: Flow<URL> = dataStore.data
        .map { settings ->
            URL(settings.diguralUrl)
        }
}