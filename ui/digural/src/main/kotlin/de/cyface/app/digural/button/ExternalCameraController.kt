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
import de.cyface.app.digural.capturing.settings.CustomPreferences
import de.cyface.camera_service.background.ParcelableCapturingProcessListener
import de.cyface.utils.Validate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import kotlin.concurrent.thread

/**
 * Calls the API that triggers external cameras to trigger in sync with this smartphones camera.
 *
 * @author Klemens Muthmann
 * @since 4.2.0
 * @constructor Create a new controller from the world wide unique device identifier of this device.
 */
@Parcelize
class ExternalCameraController(private val deviceId: String) : ParcelableCapturingProcessListener {
    init {
        Validate.notEmpty(deviceId)
    }

    override fun contextBasedInitialization(context: Context) {
        // FIXME: Known issue: After starting the capturing once, further preference changes
        // are not affecting the preferences received here, even when this is always executed after
        // starting a new BackgroundService. The SharedPreferences don't support multi-process env.
        // We cannot inject the address preference in `CameraService` as this is a ui-specific
        // preferences which the camera_service has no knowledge of.
        // A possible solution would be to store this in Room/Sql, as this supports multi-process.
        val address = CustomPreferences(context).getDiguralUrl()
        DiguralApi.baseUrl = address
        Log.d(TAG, "###########Setting digural address to: $address")
    }

    override fun onCameraAccessLost() {}
    override fun onPictureCaptured() {}
    override fun onRecordingStarted() {}
    override fun onRecordingStopped() {}
    override fun onCameraError(reason: String) {}
    override fun onAboutToCapture(measurementId: Long, location: Location?) {
        Log.d(TAG, "On About to Capture $location")
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

        /* Begin Retrofit Variant */
        /*runBlocking {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "###########Sending Payload $payload to ${DiguralApi.baseUrl}")
                DiguralApi.diguralService.trigger(payload)
            }
        }*/
        /* End Retrofit Variant */

        /* Begin Classic Variant */
        thread {
            Log.d(TAG, "Sending Payload ${payload.toJson()}")
            with(URL("http://192.168.113.154:5000/PanAiCam/Trigger").openConnection() as HttpURLConnection) {
                try {
                    requestMethod = "POST"
                    setRequestProperty("Accept", "*/*")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true

                    outputStream.use { os ->
                        val input: ByteArray =
                            payload.toJson().toByteArray(Charset.defaultCharset())
                        os.write(input, 0, input.size)
                    }
                    outputStream.flush()
                    outputStream.close()

                    Log.d(TAG, "$responseCode")
                } finally {
                    disconnect()
                }
            }
        }
        /* End Classic Variant */
    }

    override fun shallStop() {
        TODO("Not yet implemented")
    }
}