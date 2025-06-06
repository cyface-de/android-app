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
package de.cyface.app.digural.capturing

import android.content.Context
import android.location.Location
import android.util.Log
import de.cyface.app.digural.MainActivity.Companion.TAG
import de.cyface.app.digural.capturing.settings.CustomSettings
import de.cyface.camera_service.background.ParcelableCapturingProcessListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * Calls the API that triggers external cameras to trigger in sync with this smartphones camera.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 4.2.0
 * @constructor Create a new controller from the world wide unique device identifier of this device.
 * @property deviceId The unique identifier of the device which calls the trigger.
 */
@Parcelize
class ExternalCameraController(
    private val deviceId: String,
) : ParcelableCapturingProcessListener {

    @IgnoredOnParcel
    private lateinit var scope: CoroutineScope

    init {
        require(deviceId.isNotEmpty())
    }

    override suspend fun contextBasedInitialization(context: Context, scope: CoroutineScope) {
        this.scope = scope
        // Instance required to get current digural URL. MainActivity also needs access to settings
        // before capturing. Can't inject it (not parcelable right now). We use a singleton as
        // suggested by the docs, as only one instance is allowed per process. [LEIP-294]
        val customSettings = CustomSettings.getInstance(context)
        val address = customSettings.diguralUrlFlow.first()
        DiguralApi.baseUrl = address
        DiguralApi.setToUseWifi(context)
        Log.d(TAG, "Setting digural address to: $address")
    }

    override fun onCameraAccessLost() = Unit
    override fun onPictureCaptured() = Unit
    override fun onRecordingStarted() = Unit
    override fun onRecordingStopped() = Unit
    override fun onCameraError(reason: String) = Unit
    override fun onAboutToCapture(measurementId: Long, location: Location?) {
        Log.d(TAG, "On About to Capture $location")
        requireNotNull(this.scope)
        if (location == null) {
            return
        }

        val payload = Location(
            deviceId,
            measurementId,
            location.latitude,
            location.longitude,
            location.time
        )

        scope.launch {
            try {
                Log.d(TAG, "Sending Payload $payload to ${DiguralApi.baseUrl}")
                val response = DiguralApi.diguralService.trigger(payload)
                if (!response.isSuccessful) {
                    Log.e(TAG, "API call failed with response code: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send DiGuRaL trigger request", e)
            }
        }
    }

    override fun onTriggerNext() = Unit

    override fun onStart() {
        scope.launch {
            try {
                Log.d(TAG, "Sending Start to ${DiguralApi.baseUrl}")
                val response = DiguralApi.diguralService.start()
                if (!response.isSuccessful) {
                    Log.e(TAG, "API call failed with response code: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send DiGuRaL start request", e)
            }
        }
    }

    override fun onClose(context: Context) {
        scope.launch {
            try {
                Log.d(TAG, "Sending Stop to ${DiguralApi.baseUrl}")
                val response = DiguralApi.diguralService.stop()
                if (!response.isSuccessful) {
                    Log.e(TAG, "API call failed with response code: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send DiGuRaL stop request", e)
            }
        }
        DiguralApi.shutdown(context)
    }

    override fun shallStop(context: Context) {
        // Nothing to do
    }
}
