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

import static android.bluetooth.BluetoothAdapter.EXTRA_STATE;
import static android.bluetooth.BluetoothAdapter.STATE_OFF;
import static android.bluetooth.BluetoothAdapter.STATE_ON;
import static de.cyface.bluetooth_le.Constants.TAG;
import static de.cyface.bluetooth_le.DeviceSelectionActivity.EXTRA_RESULT;
import static de.cyface.bluetooth_le.DeviceSelectionActivity.EXTRA_SELECTED_DEVICE;
import static de.cyface.bluetooth_le.DeviceSelectionActivity.EXTRA_WHEEL_CIRCUMFERENCE;
import static de.cyface.bluetooth_le.DeviceSelectionActivity.RESULT_CANCEL;
import static de.cyface.bluetooth_le.DeviceSelectionActivity.RESULT_OK;
import static de.cyface.bluetooth_le.DeviceSelectionActivity.SELECTION_FINISHED;

import java.lang.ref.WeakReference;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * A class responsible for getting all necessary permissions and information from the user to
 * provide him with a cycling-speed-and-cadence-sensor.
 *
 * @author Klemens Muthmann
 * @version 1.0.2
 * @since 1.0.0
 */
public final class BluetoothLeSetup {

    /**
     * A listener that is notified of changes in the setup process. You will need this especially
     * to get a notification on whether the process was successful or not.
     */
    private final BluetoothLeSetupListener listener;
    /**
     * A key for the {@link BluetoothDevice} object extra attached to an intent calling this service.
     */
    public static final String BLUETOOTH_LE_DEVICE = "de.cyface.bluetooth.device";
    /**
     * A key for the wheel circumference as a double specifying the vehicles wheel circumference in centimeters.
     */
    public static final String WHEEL_CIRCUMFERENCE = "de.cyface.bluetooth.wheel_circumference";
    public static final String BLUETOOTHLE_DEVICE_MAC_KEY = "de.cyface.bluetoothle.device.mac";
    public static final String BLUETOOTHLE_WHEEL_CIRCUMFERENCE = "de.cyface.bluetoothle.wheelcircumference";

    /**
     * The setup process calls a broadcast receiver of this type each time a device selection
     * has occurred (or has been canceled). The receiver notifies all relevant listeners about the
     * event.
     */
    final class DeviceChoiceMessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (!intent.hasExtra(EXTRA_RESULT)) {
                listener.onSetupProcessFailed(Reason.SETUP_ERROR);
            }

            int result = intent.getIntExtra(EXTRA_RESULT, -1);
            // The first case should not happen if the selection dialog is properly coded.
            if (result == RESULT_OK
                    && (!intent.hasExtra(EXTRA_SELECTED_DEVICE) || !intent.hasExtra(EXTRA_WHEEL_CIRCUMFERENCE))) {
                listener.onSetupProcessFailed(Reason.SETUP_ERROR);
            } else if (result == RESULT_CANCEL) {
                listener.onSetupProcessFailed(Reason.CANCELED);
            } else {
                BluetoothDevice device = intent.getParcelableExtra(EXTRA_SELECTED_DEVICE);
                double wheelCircumference = intent.getDoubleExtra(EXTRA_WHEEL_CIRCUMFERENCE, 0.0);

                Log.d(TAG, "Connection to bluetooth device " + device.getName() + " established.");
                listener.onDeviceSelected(device, wheelCircumference);
            }
            context.unregisterReceiver(this);
        }
    }

    /**
     * The setup process notifies a broadcast receiver of this type when Bluetooth has been
     * turned on or off. As soon as it has been turned on it starts the process of choosing an
     * appropriate device and enter the vehicles wheel circumference. For this purpose a UI dialog is displayed.
     */
    final class EnableBluetoothEventReceiver extends BroadcastReceiver {

        /**
         * A {@code WeakReference} to the context for this receiver. This is required to present
         * the user interface for device selection. If this reference is not active anymore no
         * device selection dialog will be shown effectively ending the Bluetooth setup process
         * without success.
         */
        private final WeakReference<Context> context;

        /**
         * Creates a new completely initialized event receiver for Bluetooth state events.
         *
         * @param context A reference to the context for this receiver. This is required to present
         *            the user interface for device selection. If this reference is not active
         *            anymore no device selection dialog will be shown effectively ending the
         *            Bluetooth setup process without success.
         */
        public EnableBluetoothEventReceiver(final Context context) {
            this.context = new WeakReference<>(context);
        }

        @Override
        public void onReceive(final Context context, final Intent intent) {
            Log.i(TAG, "Received bluetooth status change.");
            int state = intent.getExtras().getInt(EXTRA_STATE);
            Context activity = this.context.get();
            if (state == STATE_ON && activity != null) {
                startDeviceScan(activity);
            } else if (state == STATE_OFF) {
                listener.onSetupProcessFailed(Reason.SWITCHED_OFF);
            }
            context.unregisterReceiver(this);
        }
    }

    /**
     * Creates a new completely initialized object for handling the BLE setup process.
     *
     * @param listener A listener that is notified of changes in the setup process. You will need
     *            this especially to get a notification on whether the process was successful
     *            or not.
     */
    public BluetoothLeSetup(final BluetoothLeSetupListener listener) {
        this.listener = listener;
    }

    /**
     * Setup the BLE connection and start the device choice. Calling the method asks for
     * necessary permissions if not yet granted and in the end starts a UI for selecting a proper
     * BLE device and enter the vehicles wheel circumference.
     *
     * @param activity The parent activity calling this code. This is required to check for proper
     *            permissions and start the device choice selection dialog.
     */
    public void setup(final Context activity) {
        // check whether device has bluetooth le support.
        if (!activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            listener.onSetupProcessFailed(Reason.NOT_SUPPORTED);
            return;
        }

        final BluetoothManager bluetoothManager = (BluetoothManager)activity
                .getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter adapter = bluetoothManager.getAdapter();

        // Get permission to use bluetooth le.
        if (adapter == null || !adapter.isEnabled()) {

            EnableBluetoothEventReceiver enableBluetoothEventReceiver = new EnableBluetoothEventReceiver(activity);
            IntentFilter enableBluetoothEventFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            activity.registerReceiver(enableBluetoothEventReceiver, enableBluetoothEventFilter);

            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivity(enableBtIntent);
        }
        startDeviceScan(activity);
    }

    /**
     * Starts the process of choosing a device. Presents a UI dialog to the user for selecting
     * the correct BLE device and wheel circumference. {@link DeviceSelectionActivity} implements
     * the ui shown to the user.
     *
     * @param activity The parent {@code Activity} this code is called from.
     */
    private void startDeviceScan(final Context activity) {
        Intent deviceSelectionIntent = new Intent(activity, DeviceSelectionActivity.class);
        IntentFilter filter = new IntentFilter(SELECTION_FINISHED);
        DeviceChoiceMessageReceiver deviceChoiceMessageReceiver = new DeviceChoiceMessageReceiver();
        activity.registerReceiver(deviceChoiceMessageReceiver, filter);
        activity.startActivity(deviceSelectionIntent);
    }
}
