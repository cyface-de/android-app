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
package de.cyface.app.digural.button

import android.content.Context
import android.location.Location
import android.util.Log
import de.cyface.app.digural.MainActivity.Companion.TAG
import de.cyface.app.digural.capturing.DiguralApi
import de.cyface.app.digural.capturing.settings.CustomSettings
import de.cyface.camera_service.background.ParcelableCapturingProcessListener
import de.cyface.utils.Validate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * Calls the API that triggers external cameras to trigger in sync with this smartphones camera.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 4.2.0
 * @constructor Create a new controller from the world wide unique device identifier of this device.
 * @property deviceId The unique identifier of the device which calls the trigger.
 */
@Parcelize
class ExternalCameraController(
    private val deviceId: String
) : ParcelableCapturingProcessListener {

    @IgnoredOnParcel
    private lateinit var scope: CoroutineScope

    init {
        Validate.notEmpty(deviceId)
    }

    override fun contextBasedInitialization(context: Context, scope: CoroutineScope) {
        this.scope = scope
        val customSettings = CustomSettings(context) // may only be initialized once per process
        val address = runBlocking { customSettings.diguralUrlFlow.first() }
        DiguralApi.baseUrl = address
        DiguralApi.setToUseWifi(context)
        Log.d(TAG, "Setting digural address to: $address")
    }

    override fun onCameraAccessLost() {}
    override fun onPictureCaptured() {}
    override fun onRecordingStarted() {}
    override fun onRecordingStopped() {}
    override fun onCameraError(reason: String) {}
    override fun onAboutToCapture(measurementId: Long, location: Location?) {
        Log.d(TAG, "On About to Capture $location")
        Validate.notNull(this.scope)
        if (location == null) {
            return
        }

        val payload = de.cyface.app.digural.capturing.Location(
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
                Log.e(TAG, "Failed to send DiGuRaL trigger request", e)
            }
        }
    }

    override fun shallStop(context: Context) {
        DiguralApi.shutdown(context)
    }
}
