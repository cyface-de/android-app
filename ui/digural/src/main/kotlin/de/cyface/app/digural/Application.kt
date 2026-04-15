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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import de.cyface.app.digural.auth.LoginActivity
import de.cyface.app.digural.auth.WebdavAuth
import de.cyface.app.digural.auth.WebdavAuthenticator
import de.cyface.camera_service.MessageCodes
import de.cyface.energy_settings.TrackingSettings
import de.cyface.synchronization.BundlesExtrasCodes
import de.cyface.synchronization.settings.DefaultSynchronizationSettings
import de.cyface.synchronization.ErrorHandler
import de.cyface.synchronization.settings.SyncConfig
import de.cyface.utils.settings.AppSettings
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
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
     * Receives [MessageCodes.GLOBAL_BROADCAST_MEMORY_PRESSURE] emitted by camera_service's
     * `BackgroundService` when Android fires a high-severity memory callback in the
     * `:camera_process`. Forwards the payload to Sentry (if opt-in) along with the log-file
     * sizes in the measurement's folder, so field LOW_MEMORY episodes are observable without
     * adb access.
     */
    private val memoryPressureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != MessageCodes.GLOBAL_BROADCAST_MEMORY_PRESSURE) return

            // Snapshot extras before the intent is recycled by the system
            val level = intent.getIntExtra("level", 0)
            val source = intent.getStringExtra("source") ?: "?"
            val measurementId = if (intent.hasExtra(BundlesExtrasCodes.MEASUREMENT_ID)) {
                intent.getLongExtra(BundlesExtrasCodes.MEASUREMENT_ID, -1L)
            } else null
            val nativeHeapKb = intent.getLongExtra("nativeHeapKb", -1L)
            val javaHeapUsedKb = intent.getLongExtra("javaHeapUsedKb", -1L)
            val javaHeapTotalKb = intent.getLongExtra("javaHeapTotalKb", -1L)
            val javaHeapMaxKb = intent.getLongExtra("javaHeapMaxKb", -1L)
            val logFolder = intent.getStringExtra("logFolder")

            Log.w(
                TAG,
                "Memory pressure from camera_service (source=$source level=$level) " +
                        "mid=$measurementId nativeHeap=${nativeHeapKb}KB " +
                        "javaHeap=$javaHeapUsedKb/$javaHeapTotalKb(max=$javaHeapMaxKb)KB"
            )

            CoroutineScope(Dispatchers.IO).launch {
                val reportErrors = lazyAppSettings.reportErrorsFlow.firstOrNull() == true
                if (!reportErrors) return@launch

                val fileSizes = logFolder?.let { path ->
                    val dir = File(path)
                    if (dir.isDirectory) {
                        dir.listFiles()?.associate { it.name to it.length() } ?: emptyMap()
                    } else emptyMap()
                } ?: emptyMap()

                Sentry.withScope { scope ->
                    scope.level = SentryLevel.WARNING
                    scope.setTag("source", source)
                    measurementId?.let { scope.setTag("measurementId", it.toString()) }
                    scope.setExtra("level", level.toString())
                    scope.setExtra("nativeHeapKb", nativeHeapKb.toString())
                    scope.setExtra("javaHeapUsedKb", javaHeapUsedKb.toString())
                    scope.setExtra("javaHeapTotalKb", javaHeapTotalKb.toString())
                    scope.setExtra("javaHeapMaxKb", javaHeapMaxKb.toString())
                    fileSizes.forEach { (name, size) ->
                        scope.setExtra("logFile_$name", size.toString())
                    }
                    Sentry.captureMessage(
                        "camera_service memory pressure ($source level=$level)"
                    )
                }
            }
        }
    }

    /**
     * Reports error events to the user via UI and to Sentry, if opted-in.
     */
    private val errorListener = object : ErrorHandler.ErrorListener {
        override fun onErrorReceive(
            errorCode: ErrorHandler.ErrorCode,
            errorMessage: String?,
            fromBackground: Boolean
        ) {
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

        // Listen for memory-pressure broadcasts from camera_service's :camera_process.
        // RECEIVER_NOT_EXPORTED: only same-UID (our own processes) can deliver this broadcast.
        ContextCompat.registerReceiver(
            this,
            memoryPressureReceiver,
            IntentFilter(MessageCodes.GLOBAL_BROADCAST_MEMORY_PRESSURE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

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
        unregisterReceiver(memoryPressureReceiver)
        super.onTerminate()
    }

    companion object {
        private const val TAG = "de.cyface.app.digural"
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
