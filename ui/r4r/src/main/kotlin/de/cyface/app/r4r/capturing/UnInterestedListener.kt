/*
 * Copyright 2023-2024 Cyface GmbH
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
package de.cyface.app.r4r.capturing

import android.content.Context
import android.location.Location
import de.cyface.camera_service.background.ParcelableCapturingProcessListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.parcelize.Parcelize

@Parcelize
class UnInterestedListener : ParcelableCapturingProcessListener {

    override suspend fun contextBasedInitialization(context: Context, scope: CoroutineScope) {
        // Nothing to do
    }
    override fun onCameraAccessLost() {
        // Nothing to do
    }
    override fun onPictureCaptured() {
        // Nothing to do
    }
    override fun onRecordingStarted() {
        // Nothing to do
    }
    override fun onRecordingStopped() {
        // Nothing to do
    }
    override fun onCameraError(reason: String) {
        // Nothing to do
    }
    override fun onAboutToCapture(measurementId: Long, location: Location?) {
        // Nothing to do
    }
    override fun onTriggerNext() {
        // Nothing to do
    }
    override fun onStart() {
        // Nothing to do
    }
    override fun onClose(context: Context) {
        // Nothing to do
    }
    override fun shallStop(context: Context) {
        // Nothing to do
    }
}
