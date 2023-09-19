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
package de.cyface.app.digural.capturing

/**
 * The location data which is expected by the [de.cyface.app.digural.capturing.DiguralApiService].
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 3.7.3
 * @property deviceId The unique identifier of the device which executes the trigger.
 * @property measurementId The unique identifier of the measurement which executes the trigger.
 * @property latitude The latitude of the last known location at the time of the trigger.
 * @property longitude The longitude of the last known location at the time of the trigger.
 * @property time The time of the last known location at the time of the trigger.
 */
data class Location(
    val deviceId: String,
    val measurementId: Long,
    val latitude: Double,
    val longitude: Double,
    val time: Long
) {
    fun toJson(): String {
        return "{\"DeviceId\":\"$deviceId\",\"MeasurementId\":$measurementId,\"Latitude\":$latitude,\"Longitude\":$longitude,\"Time\":$time}"
    }
}