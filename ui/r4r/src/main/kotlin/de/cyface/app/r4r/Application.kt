/*
 * Copyright 2017-2023 Cyface GmbH
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
package de.cyface.app.r4r

import android.app.Application
import android.content.IntentFilter
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import de.cyface.app.r4r.auth.LoginActivity
import de.cyface.synchronization.CyfaceAuthenticator
import de.cyface.synchronization.ErrorHandler
import de.cyface.synchronization.ErrorHandler.ErrorCode
import de.cyface.utils.AppPreferences
import io.sentry.Sentry

/**
 * The implementation of Android's `Application` class for this project.
 *
 * It shows the errors (not handled by [LoginActivity]) as simple [Toast] to the user.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.5.2
 * @since 1.0.0
 */
class Application : Application() {
    /**
     * The `SharedPreferences` used to store the app preferences.
     */
    private lateinit var preferences: AppPreferences

    /**
     * Reports error events to the user via UI and to Sentry, if opted-in.
     */
    private val errorListener =
        ErrorHandler.ErrorListener { errorCode: ErrorCode, errorMessage: String ->
            val appName = applicationContext.getString(R.string.app_name)
            Toast.makeText(
                this@Application,
                String.format("%s - %s", errorMessage, appName),
                Toast.LENGTH_LONG
            ).show()

            // There are two cases we can have network errors
            // 1. during authentication (AuthTokenRequest), either login or before upload
            // 2. during upload (SyncPerformer/SyncAdapter)
            // In the first case we get the full stacktrace by a Sentry capture in AuthTokenRequest
            // but in the second case we cannot get the stacktrace as it's only available in the SDK.
            // For that reason we also capture a message here.
            // However, it seems like e.g. a interrupted upload shows a toast but does not trigger sentry.
            if (preferences.getReportingAccepted()) {
                Sentry.captureMessage(errorCode.name + ": " + errorMessage)
            }
        }

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)

        // Register the activity to be called by the authenticator to request credentials from the user.
        CyfaceAuthenticator.LOGIN_ACTIVITY = LoginActivity::class.java

        // Register error listener
        errorHandler = ErrorHandler()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            errorHandler!!,
            IntentFilter(ErrorHandler.ERROR_INTENT)
        )
        errorHandler!!.addListener(errorListener)
    }

    override fun onTerminate() {
        errorHandler!!.removeListener(errorListener)
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
    }
}