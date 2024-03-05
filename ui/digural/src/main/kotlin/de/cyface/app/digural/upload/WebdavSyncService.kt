/*
 * Copyright 2024 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.app.digural.upload

import android.app.Service
import android.content.Intent
import android.os.IBinder
import de.cyface.app.digural.BuildConfig
import de.cyface.app.digural.auth.WebdavAuth
import de.cyface.app.digural.auth.WebdavAuthenticator
import de.cyface.persistence.DefaultPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.synchronization.SyncAdapter
import de.cyface.utils.Validate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * The synchronisation `Service` used to bind the synchronisation adapter to the Android framework.
 *
 * Further details are described in the [Android
 * documentation](https://developer.android.com/training/sync-adapters/creating-sync-adapter.html#CreateSyncAdapterService).
 *
 * This is a custom implementation of the [de.cyface.synchronization.SyncService].
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.8.0
 */
class WebdavSyncService : Service() {
    override fun onCreate() {
        synchronized(LOCK) {
            if (syncAdapter == null) {
                val persistence = DefaultPersistenceLayer(this, DefaultPersistenceBehaviour())
                val deviceId = persistence.restoreOrCreateDeviceId()
                val collectorApi = collectorApi()
                syncAdapter = SyncAdapter(
                    applicationContext,
                    true,
                    WebdavAuth(applicationContext, WebdavAuthenticator.settings),
                    WebdavUploader(collectorApi, deviceId, BuildConfig.testLogin), //FIXME: username
                )
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return syncAdapter!!.syncAdapterBinder
    }

    /**
     * Reads the Collector API URL from the preferences.
     *
     * @return The URL as string
     */
    private fun collectorApi(): String {
        // The `collectorApi` stands for the webdav API which collects the data from us.
        val apiEndpoint =
            runBlocking { WebdavAuthenticator.settings.collectorUrlFlow.first() }
        Validate.notNull(
            apiEndpoint,
            "Sync canceled: Server url not available. Please set the applications server url preference."
        )
        return apiEndpoint
    }

    companion object {
        /**
         * The synchronisation adapter this service is supposed to call.
         *
         * Singleton isn't what they call a beauty. Nevertheless this is how it is specified in the documentation. Maybe try
         * to change this after it runs.
         */
        private var syncAdapter: SyncAdapter? = null

        /**
         * Lock object used to synchronize synchronisation adapter creation as described in the Android documentation.
         */
        private val LOCK = Any()
    }
}