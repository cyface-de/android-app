/*
 * Copyright 2017 Cyface GmbH
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
package de.cyface.bluetooth_le;

/**
 * Enumeration for all the reasons the Bluetooth LE setup process might fail.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public enum Reason {
    /**
     * If Bluetooth is not supported by the system.
     */
    NOT_SUPPORTED
    /**
     * The user denied permission to use Bluetooth.
     */
    , BLUETOOTH_PERMISSION_DENIED
    /**
     *     The user denied permission to access the devices coarse location.
     *     TODO: should not be required targeting Android 12 using neverForLocation
     */
    , ACCESS_COARSE_LOCATION_DENIED
    /**
     *     Bluetooth has been switched off.
     */
    , SWITCHED_OFF
    /**
     *     Generic error which should usually not occur and might be due to some implementation problem.
     */
    , SETUP_ERROR
    /**
     *     The user canceled the setup process.
     */
    , CANCELED
}
