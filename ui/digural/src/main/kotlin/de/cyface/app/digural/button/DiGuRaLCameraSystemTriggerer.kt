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

import android.location.Location
import de.cyface.app.digural.capturing.DiguralApi.diguralService
import de.cyface.camera_service.background.CapturingProcessListener
import android.util.Log
import de.cyface.camera_service.background.ParcelableCapturingProcessListener
import de.cyface.utils.Validate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import retrofit2.awaitResponse
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import kotlin.concurrent.thread

/**
 * Calls the API that triggers external cameras to trigger in sync with this smartphones camera.
 *
 * @author Klemens Muthmann
 * @since 4.2.0
 * @constructor Create a new triggerer from the world wide unique device identifier of this device.
 */
@Parcelize
class DiGuRaLCameraSystemTriggerer(val deviceId: String, val address: URL) :
    ParcelableCapturingProcessListener {
    private val TAG = "de.cyface.app.digural"

    init {
        Validate.notEmpty(deviceId)
        Log.d(TAG, "Setting digural Server address to: ${address}!")
        baseUrl = address
    }

    override fun onCameraAccessLost() {}
    override fun onPictureCaptured() {}
    override fun onRecordingStarted() {}
    override fun onRecordingStopped() {}
    override fun onCameraError(reason: String) {}
    override fun onAboutToCapture(measurementId: Long, location: Location?) {
        Log.d(TAG, "######## On About to Capture $location")
        val payload = de.cyface.app.digural.capturing.Location(
            deviceId,
            measurementId,
            50.0,
            13.0,
            10_000
        )

        /*runBlocking {
            withContext(Dispatchers.IO) {*/

                thread {
                    //Log.d(TAG, "###########Sending Payload ${payload.toJson()}")
                    with(URL("http://192.168.113.154:5000/PanAiCam/Trigger").openConnection() as HttpURLConnection) {
                        try {
                            requestMethod = "POST"
                            //requestMethod = "GET"
                            setRequestProperty("Accept", "*/*")
                            setRequestProperty("Content-Type", "application/json")
                            doOutput = true
                            //doInput = true

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
                    //}
                //}

                    /*Log.d(TAG,"#############Triggering")
                    val response = diguralService.trigger().awaitResponse()//(payload)

                    Log.d(TAG, "##### Response Code: ${response.code()}")
                    Log.d(TAG, "####  Body: ${response.body()}")
                    Log.d(TAG, "########Triggered")*/
                }
            }

            /*if(location == null) {
                return
            }*/

            /*val payload = de.cyface.app.digural.capturing.Location(
                deviceId,
                measurementId,
                location.latitude,
                location.longitude,
                location.time
            )*/


        }

        override fun shallStop() {
            TODO("Not yet implemented")
        }
    }
