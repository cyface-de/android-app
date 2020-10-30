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
/**
 * This package contains all files necessary to measure speed using a Bluetooth LE Cycling Speed
 * and Cadence (CSC) sensor. The central classes are the {@link de.cyface.bluetooth_le.BluetoothLeSetup}
 * for establishing a connection and the {@link de.cyface.bluetooth_le.CyclingCadenceSpeedMeasurementDevice}
 * for getting the speed from the measured device.
 * <p>
 * The setup process displays a dialog for selecting an appropriate device and entering the devices
 * wheel circumference, after asking for all necessary permissions.
 * <p>
 * The {@link de.cyface.bluetooth_le.CyclingCadenceSpeedMeasurementDevice} is a wrapper that
 * listens to system Bluetooth LE events calculates a speed value from those events and provides the
 * calculated speed from the calling application.
 * <p>
 * Note: The {@link de.cyface.bluetooth_le.BluetoothLeSetup} process does not directly provide a
 * {@link de.cyface.bluetooth_le.CyclingCadenceSpeedMeasurementDevice} but gives you a {@link
 * android.bluetooth.BluetoothDevice} from which you may construct your own {@link
 * de.cyface.bluetooth_le.CyclingCadenceSpeedMeasurementDevice}. The reason for this design decision
 * was the required interprocess communication between the Cyface app and its measuring service. It
 * is much easier to transfer a {@link android.bluetooth.BluetoothDevice} via IPC rather than a
 * {@link de.cyface.bluetooth_le.CyclingCadenceSpeedMeasurementDevice}.
 */
package de.cyface.bluetooth_le;
