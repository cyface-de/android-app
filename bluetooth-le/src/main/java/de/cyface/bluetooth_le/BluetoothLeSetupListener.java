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

import android.bluetooth.BluetoothDevice;


/**
 * A listener for events occurring during the Bluetooth LE setup process. A class implementing this
 * interface can be registered on a {@link BluetoothLeSetup} and be notified about relevant phases
 * in the Bluetooth LE setup process.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public interface BluetoothLeSetupListener {
    /**
     * Called if the user selects a device to be used by the Bluetooth LE connection.
     *
     * @param device             The selected device.
     * @param wheelCircumference The wheel circumference of the vehicle bearing the device.
     */
    void onDeviceSelected(final BluetoothDevice device, final double wheelCircumference);

    /**
     * Called if the setup process failes and gives a reason for the failure.
     *
     * @param reason The failure reason.
     */
    void onSetupProcessFailed(final Reason reason);
}
