/*
 * Copyright 2024-2025 Cyface GmbH
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
package de.cyface.app.digural.upload

import android.app.Service
import android.content.Intent
import android.os.IBinder
import de.cyface.app.digural.auth.WebdavAuth
import de.cyface.app.digural.auth.WebdavAuthenticator
import de.cyface.persistence.DefaultPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.synchronization.CyfaceAuthenticator
import de.cyface.synchronization.SyncAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The synchronisation `Service` used to bind the synchronisation adapter to the Android framework.
 *
 * Further details are described in the [Android documentation]
 * (https://developer.android.com/training/sync-adapters/creating-sync-adapter.html#CreateSyncAdapterService).
 *
 * This is a custom implementation of the [de.cyface.synchronization.CyfaceSyncService].
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 3.8.0
 */
class WebdavSyncService : Service() {
    override fun onCreate() {
        synchronized(LOCK) {
            if (syncAdapter == null) {
                val persistence = DefaultPersistenceLayer(this, DefaultPersistenceBehaviour())
                val deviceId = persistence.restoreOrCreateDeviceId()
                val auth = WebdavAuth(applicationContext, WebdavAuthenticator.settings)

                // `onBind()` is called directly after `onCreate()` and requires `syncAdapter` (sync)
                val collectorApi = runBlocking { collectorApi() }

                val account = auth.getAccount()
                syncAdapter = SyncAdapter(
                    applicationContext,
                    true,
                    auth,
                    WebdavUploader(collectorApi, deviceId, account.name, auth.getPassword(account)),
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
    private suspend fun collectorApi(): String {
        return WebdavAuthenticator.settings.collectorUrlFlow.first()
    }

    companion object {
        /**
         * The synchronisation adapter this service is supposed to call.
         *
         * Singleton isn't what they call a beauty. Nevertheless this is how it is specified in
         * the documentation. Maybe try to change this after it runs.
         */
        private var syncAdapter: SyncAdapter? = null

        /**
         * Lock object used to synchronize synchronisation adapter creation as described in the Android documentation.
         */
        private val LOCK = Any()

        const val AUTH_TOKEN_TYPE = "de.cyface.digural.auth_token_type"
    }
}