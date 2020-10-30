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

import static de.cyface.bluetooth_le.Constants.TAG;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

/**
 * Abstract base class for measuring events from a Cycling Speed and Cadence sensor (CSC) using
 * Bluetooth Low Energy (BLE).
 * <p>
 * The actual measurement done by the device is the Last Wheel Event (LWE) time and the Cumulative
 * Wheel Revolutions (CWR). From the difference of the last two LWE and CWR values and the wheel
 * circumference of the vehicle using this sensor, one can calculate its current speed.
 *
 * @author Klemens Muthmann
 * @version 1.0.1
 * @see <a href=
 *      "https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.characteristic.csc_measurement.xml">https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.characteristic.csc_measurement.xml</a>
 * @since 1.0.0
 */
public abstract class CyclingCadenceSpeedMeasurementDevice {

    /**
     * The maximum value for an unsigned short. This is required to simulate the unsigned short
     * with an int, since java has no real support for unsigned values but Bluetooth LE requires them.
     */
    private static final int UNSIGNED_SHORT_MAX_VALUE = 65535;
    /**
     * For speed calculation the wheel circumference of the vehicle using this device is required.
     * This is the wheel circumference in centimeters.
     */
    private final double wheelCircumference;
    /**
     * Current measurement of cumulative wheel revolutions.
     */
    private long curCwr;
    /**
     * Current last wheel event time in 1/1024 seconds. This value rolls over every 64 seconds.
     */
    private int curLwet;
    /**
     * The Android Bluetooth device wrapped by this object.
     */
    private final BluetoothDevice device;
    /**
     * The Android context object used to open and close the connection to the wrapped device.
     */
    private final Context context;
    /**
     * The Android service object for managing Bluetooth LE connections.
     */
    private BluetoothGatt gatt;
    /**
     * Callback for receiving events happening to the Android devices Bluetooth.
     */
    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                Log.i(TAG, "Started Service discovery." + gatt.discoverServices());
                onConnected();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                stopReceivingUpdates();
                disconnect();
                onDisconnected();
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            BluetoothGattService speedAndCadenceService = gatt
                    .getService(UUID.fromString("00001816-0000-1000-8000-00805f9b34fb"));
            List<BluetoothGattCharacteristic> speedAndCadenceServiceCharacteristics = speedAndCadenceService
                    .getCharacteristics();
            for (BluetoothGattCharacteristic characteristic : speedAndCadenceServiceCharacteristics) {
                if (characteristic.getUuid().equals(UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb"))) {
                    gatt.setCharacteristicNotification(characteristic, true);
                    BluetoothGattDescriptor clientDescriptor = characteristic
                            .getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                    clientDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(clientDescriptor);
                }
            }
            onConnected();
        }

        /**
         * Called every time a monitored characteristic of the GATT changes. This call
         * is capable of parsing the CSC Measurement characteristic as described in
         * <a href=
         * "https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.characteristic.csc_measurement.xml">https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.characteristic.csc_measurement.xml</a>.
         *
         * @param gatt The GATT the characteristic changed on.
         * @param characteristic The changed characteristic.
         */
        @Override
        public void onCharacteristicChanged(final BluetoothGatt gatt,
                final BluetoothGattCharacteristic characteristic) {
            byte[] cscValue = characteristic.getValue();
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                StringBuilder cscValueAsString = new StringBuilder();
                for (byte value : cscValue) {
                    cscValueAsString
                            .append(String.format("%8s", Integer.toBinaryString(value & 0xFF)).replace(' ', '0'));
                    cscValueAsString.append(" ");
                }
                Log.v(TAG, "Received " + cscValueAsString.toString() + " from CSC Sensor.");
            }

            long prevCwr = curCwr;
            int prevLwet = curLwet;
            curCwr = ByteBuffer.wrap(new byte[] {0, 0, 0, 0, cscValue[4], cscValue[3], cscValue[2], cscValue[1]})
                    .getLong();
            curLwet = ByteBuffer.wrap(new byte[] {0, 0, cscValue[6], cscValue[5]}).getInt();
            Log.v(TAG, "Previous Cumulative Wheel Revolutions: " + prevCwr);
            Log.v(TAG, "Current Cumulative Wheel Revolutions: " + curCwr);
            Log.v(TAG, "Previous Last Wheel Event Time: " + prevLwet);
            Log.v(TAG, "Current Last Wheel Event Time: " + curLwet);
            if (prevLwet == curLwet) {
                Log.v(TAG, "No speed change detected.");
                onSpeedChanged(0.0);
            } else {
                onSpeedChanged(calcSpeed(prevCwr, curCwr, prevLwet, curLwet, wheelCircumference));
            }

        }
    };

    /**
     * Creates a new completely initialized {@code CyclingCadenceSpeedMeasurementDevice}. You may
     * connect and disconnect with the device and thus reuse it. Be careful though it is not
     * necessarily thread safe.
     *
     * @param context The Android context object used to open and close the connection to
     *            the wrapped device.
     * @param wheelCircumference For speed calculation the wheel circumference of the vehicle using
     *            this device is required.
     *            This is the wheel circumference in centimeters.
     * @param device The Android Bluetooth device wrapped by this object.
     */
    public CyclingCadenceSpeedMeasurementDevice(final Context context, final double wheelCircumference,
            final BluetoothDevice device) {
        if (wheelCircumference <= 0.0) {
            throw new IllegalArgumentException(String
                    .format("Wheel circumference was set to %s. Should be greater than 0.0.", wheelCircumference));
        }
        if (device == null) {
            throw new IllegalArgumentException("No valid Bluetooth device initialized. The value for device was null.");
        }
        if (context == null) {
            throw new IllegalArgumentException(
                    "Context was null. Please provide a valid context for this class, such as an activity, service or the application context.");
        }
        this.wheelCircumference = wheelCircumference;
        this.device = device;
        this.context = context;
    }

    /**
     * Calling this method registers the required listeners with the system's Bluetooth stack, so
     * this object starts receiving updates from the provided Bluetooth LE device.
     */
    public void startReceivingUpdates() {
        if (device != null) {
            gatt = device.connectGatt(context, true, callback);
        }
    }

    /**
     * Calling this method removes all listeners from the system's Bluetooth stack, so this object stops receiving
     * updates from the provided Bluetooth LE device.
     */
    public void stopReceivingUpdates() {
        if (gatt != null) {
            gatt.close();
        }
    }

    /**
     * This method is called each time a speed change is transmitted from the CSC sensor.
     * Subclasses need to implement this callback and handle the new speed value appropriately.
     *
     * @param newSpeed The new speed in cm per second.
     */
    public abstract void onSpeedChanged(final double newSpeed);

    /**
     * This method is called each time this CSC sensor is disconnected from the system. This might happen because of
     * switching it off or moving it out of range.
     */
    public abstract void onDisconnected();

    /**
     * This method is called each time this CSC sensor is connected to the system.
     */
    public abstract void onConnected();

    /**
     * Disconnect from the sensor and close connection.
     */
    public void disconnect() {
        if (gatt != null) {
            gatt.disconnect();
        }
    }

    /**
     * Calculates a vehicles speed from the values received by a standard cycling speed and cadence
     * sensor (CSC).
     *
     * @param prevCwr Previous measurement of cumulative wheel revolutions.
     * @param curCwr Current measurement of cumulative wheel revolutions.
     * @param prevLwet Previous last wheel event time in 1/1024 seconds. This value rolls
     *            over every 64 seconds.
     * @param curLwet Current last wheel event time in 1/1024 seconds. This value rolls
     *            over every 64 seconds.
     * @param wheelCircumference The circumference of the wheel used by the measured vehicle in
     *            centimeters (cm).
     * @return The current speed of the vehicle in centimeters per second (cm/s).
     */
    static double calcSpeed(final long prevCwr, final long curCwr, final int prevLwet, final int curLwet,
            final double wheelCircumference) {
        if (prevCwr < 0L || curCwr < 0L || prevLwet < 0 || curLwet < 0 || wheelCircumference < 0.0
                || curLwet == prevLwet) {
            throw new IllegalArgumentException();
        }
        // handle overflow.
        int time = curLwet - prevLwet < 0 ? (UNSIGNED_SHORT_MAX_VALUE - prevLwet) + curLwet : curLwet - prevLwet;
        double s = ((double)(curCwr - prevCwr)) * wheelCircumference;
        double v = s / time * 1024.0;
        Log.v(TAG, "Calculated speed: " + v);
        return v;
    }
}
