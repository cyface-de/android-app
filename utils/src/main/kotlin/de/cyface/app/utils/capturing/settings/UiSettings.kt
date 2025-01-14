/*
 * Copyright 2023-2025 Cyface GmbH
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
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import de.cyface.app.utils.Settings
import de.cyface.persistence.SetupException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.net.URL

data class UiConfig(val incentivesUrl: String)

/**
 * Settings used by multiple uis.
 *
 * We currently don't use a repository to abstract the interface of the data types from the data
 * source. The reason for this is the class is very simple and we don't plan multiple data sources.
 * If this changes, consider using the standard Android Architecture, see `MeasurementRepository`.
 *
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 3.4.0
 */
class UiSettings private constructor(context: Context, private val config: UiConfig) {

    /**
     * Use Singleton to ensure only one instance per process is created. [LEIP-294]
     *
     * It should be okay to use a Singleton as this is also suggested in the documentation:
     * https://developer.android.com/topic/libraries/architecture/datastore#multiprocess
     */
    companion object {
        @Volatile
        private var instance: UiSettings? = null

        fun getInstance(context: Context, config: UiConfig):
                UiSettings {
            return instance ?: synchronized(this) {
                instance ?: UiSettings(
                    context.applicationContext,
                    config
                ).also {
                    instance = it
                }
            }.also {
                if (it.config != config) {
                    throw IllegalStateException("Already initialized with different configuration.")
                }
            }
        }
    }

    /**
     * This avoids leaking the context when this object outlives the Activity of Fragment.
     */
    private val appContext = context.applicationContext

    /**
     * The data store with multi-process support.
     *
     * The reason for multi-process support is that the incentives url may be accessed by a
     * background service which may run in another process.
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
            // With cacheDir the settings are lost on app restart [RFR-799]
            File("${appContext.filesDir.path}/app_utils.pb")
        },
        migrations = listOf(
            PreferencesMigrationFactory.create(appContext, config.incentivesUrl),
            StoreMigration(config.incentivesUrl)
        )
    )

    init {
        if (!config.incentivesUrl.startsWith("https://") && !config.incentivesUrl.startsWith("http://")) {
            throw SetupException("Invalid URL protocol")
        }
    }

    /**
     * Saves the URL of the server to get incentives from.
     *
     * @param value the URL to save.
     */
    suspend fun setIncentivesUrl(value: URL) {
        dataStore.updateData { currentSettings ->
            currentSettings.toBuilder()
                .setIncentivesUrl(value.toExternalForm())
                .build()
        }
    }

    /**
     * @return The URL of the server to get incentives from.
     */
    val incentivesUrlFlow: Flow<URL> = dataStore.data
        .map { settings ->
            URL(settings.incentivesUrl)
        }
}