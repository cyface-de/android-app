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
import de.cyface.utils.Validate
import kotlinx.parcelize.Parcelize

/**
 * Calls the API that triggers external cameras to trigger in sync with this smartphones camera.
 *
 * @author Klemens Muthmann
 * @since 4.2.0
 * @constructor Create a new triggerer from the world wide unique device identifier of this device
 * and an endpoint to send requests to.
 */
@Parcelize
class DiGuRaLCameraSystemTriggerer(val deviceId: String, val endpoint: String) : CapturingProcessListener {
    init {
        Validate.notEmpty(deviceId)
        Validate.notEmpty(endpoint)
    }

    override fun onCameraAccessLost() {}
    override fun onPictureCaptured() {}
    override fun onRecordingStarted() {}
    override fun onRecordingStopped() {}
    override fun onCameraError(reason: String) {}
    override fun onAboutToCapture(measurementId: Long, location: Location?) {
        val payload = de.cyface.app.digural.capturing.Location(
            deviceId!!,
            measurementId,
            location!!.latitude,
            location.longitude,
            location.time
        )
        diguralService.trigger(payload)
    }
}