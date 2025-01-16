/*
 * Copyright 2017-2025 Cyface GmbH
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
package de.cyface.app.digural

import android.app.Application
import android.content.IntentFilter
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import de.cyface.app.digural.auth.LoginActivity
import de.cyface.app.digural.auth.WebdavAuth
import de.cyface.app.digural.auth.WebdavAuthenticator
import de.cyface.energy_settings.TrackingSettings
import de.cyface.synchronization.settings.DefaultSynchronizationSettings
import de.cyface.synchronization.ErrorHandler
import de.cyface.synchronization.settings.SyncConfig
import de.cyface.utils.settings.AppSettings
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The implementation of Android's `Application` class for this project.
 *
 * It shows the errors (not handled by [LoginActivity]) as simple [Toast] to the user.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 5.0.0
 * @since 1.0.0
 */
class Application : Application() {

    /**
     * The settings used by both, UIs and libraries.
     *
     * Lazy initialization to ensure it's available when needed, e.g. in errorListener init below.
     */
    private val lazyAppSettings by lazy { // android-utils
        AppSettings.getInstance(this)
    }

    /**
     * Reports error events to the user via UI and to Sentry, if opted-in.
     */
    private val errorListener =
        ErrorHandler.ErrorListener { errorCode, errorMessage, fromBackground ->
            val appName = applicationContext.getString(R.string.app_name)
            if (!fromBackground) { // RFR-772
                Toast.makeText(
                    this@Application,
                    String.format(Locale.getDefault(), "%s - %s", errorMessage, appName),
                    Toast.LENGTH_LONG
                ).show()
            }

            // There are two cases we can have network errors
            // 1. during authentication (AuthTokenRequest), either login or before upload
            // 2. during upload (SyncPerformer/SyncAdapter)
            // In the first case we get the full stacktrace by a Sentry capture in AuthTokenRequest
            // but in the second case we cannot get the stacktrace as it's only available in the SDK.
            // For that reason we also capture a message here.
            // However, it seems like e.g. a interrupted upload shows a toast but does not trigger sentry.
            CoroutineScope(Dispatchers.Default).launch {
                lazyAppSettings.reportErrorsFlow.firstOrNull()?.let { reportErrors ->
                    if (reportErrors) {
                        Sentry.captureMessage(errorCode.name + ": " + errorMessage)
                    }
                }
            }
        }

    override fun onCreate() {
        super.onCreate()

        // Initialize DataStore once for all settings
        appSettings = lazyAppSettings
        TrackingSettings.initialize(this) // energy_settings
        WebdavAuthenticator.settings = DefaultSynchronizationSettings.getInstance( // synchronization
            this,
            SyncConfig(
                BuildConfig.collectorApi,
                WebdavAuth.dummyAuthConfig()
            ),
        )

        // Register the activity to be called by the authenticator to request credentials from the user.
        WebdavAuthenticator.LOGIN_ACTIVITY = LoginActivity::class.java

        // Register error listener
        errorHandler = ErrorHandler()
        // Other than ShutdownFinishedHandler, this seem to work with local broadcast
        LocalBroadcastManager.getInstance(this).registerReceiver(
            errorHandler!!,
            IntentFilter(ErrorHandler.ERROR_INTENT)
        )

        errorHandler!!.addListener(errorListener)

        // Use strict mode in dev environment to crash when a resource failed to call close.
        // Cannot be enabled due to an open issue in the sardine library and probably DataStore.
        /*if (BuildConfig.DEBUG) {
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .penaltyDeath()
                    .build()
            )
        }*/
    }

    override fun onTerminate() {
        errorHandler!!.removeListener(errorListener)
        // Other than ShutdownFinishedHandler, this seem to work with local broadcast
        LocalBroadcastManager.getInstance(this).unregisterReceiver(
            errorHandler!!
        )
        super.onTerminate()
    }

    companion object {
        /**
         * Allows to subscribe to error events.
         */
        @JvmStatic
        var errorHandler: ErrorHandler? = null
            private set
        /**
         * The settings used by both, UIs and libraries.
         */
        lateinit var appSettings: AppSettings
            private set
    }
}