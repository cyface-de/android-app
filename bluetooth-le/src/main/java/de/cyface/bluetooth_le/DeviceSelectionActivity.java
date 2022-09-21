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

import static android.Manifest.permission.BLUETOOTH_SCAN;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

/**
 * Activity presenting a dialog for choosing a CSC sensor device and entering the wheel
 * circumference of the used vehicle.
 *
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 1.0.0
 */
public class DeviceSelectionActivity extends Activity {

    /**
     * {@link android.widget.ListAdapter} used by the {@link ListView} that shows the available
     * Bluetooth LE CSC devices.
     */
    private LeDeviceListAdapter listAdapter;
    /**
     * {@link Intent} identifier send out as broadcast when a device was selected successfully.
     */
    public static final String SELECTION_FINISHED = "de.cyface.bluetooth.action.device_selected";
    /**
     * The status issued if the dialog finished with a click on the 'OK' button.
     */
    public final static int RESULT_OK = 1;
    /**
     * The status issued if the dialog finished with a click on the 'Cancel' button.
     */
    public final static int RESULT_CANCEL = 2;
    /**
     * Request code issued via an intent so the system asks the user for coarse location
     * permission. TODO: should not be necessary, targeting Android 12+
     */
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 3;
    /**
     * Request code issued via an intent so the system asks the user for bluetooth scan permission.
     */
    private static final int PERMISSION_REQUEST_BLUETOOTH_SCAN = 4;
    /**
     * The identifier used to identify the selected device in the extras of the intent returned
     * upon successful device selection.
     */
    public final static String EXTRA_SELECTED_DEVICE = "de.cyface.bluetooth_le.selected_device";
    /**
     * The identifier used to identify the entered wheel circumference in the extras of the intent
     * returned upon successful device selection.
     */
    public final static String EXTRA_WHEEL_CIRCUMFERENCE = "de.cyface.bluetooth_le.wheel_circumference";
    /**
     * The identifier used to identify the result state in the extras of the intent of this dialog
     * after it has been resolved. This is either {@link #RESULT_OK} or {@link #RESULT_CANCEL}.
     */
    public final static String EXTRA_RESULT = "de.cyface.bluetooth_le.result";
    /**
     * The time in milliseconds scanning for Bluetooth LE devices is carried out at the start of
     * the dialog or after hitting the scan button.
     */
    final static long SCAN_PERIOD = 10000;
    /**
     * A flag which is {@code true} if the dialog currently scans for Bluetooth LE devices; {@code
     * false} otherwise.
     */
    private boolean mScanning;
    /**
     * Callback which is notified when new devices are detected. The callback is responsible for
     * adding those devices to the dialogs device list.
     */
    private final BluetoothAdapter.LeScanCallback scanCallback = new BluetoothAdapter.LeScanCallback() {
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
            DeviceSelectionActivity.this.runOnUiThread(() -> {
                listAdapter.add(device);
                listAdapter.notifyDataSetChanged();
            });
        }
    };
    /**
     * The currently selected Bluetooth LE CSC device or {@code null} if no device has been
     * selected yet.
     */
    private BluetoothDevice selectedDevice;
    /**
     * The {@link BluetoothAdapter}, which is the interface to the systems Bluetooth stack and is
     * used for device discovery.
     */
    private BluetoothAdapter adapter;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_selection_layout);

        BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        assert bluetoothManager != null;
        final BluetoothAdapter adapter = bluetoothManager.getAdapter();
        setBluetoothAdapter(adapter);

        listAdapter = new LeDeviceListAdapter(this);
        ListView listView = findViewById(R.id.devices_lv);
        listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        listView.setOnItemClickListener(
                (adapterView, view, i, l) -> selectedDevice = (BluetoothDevice)adapterView.getItemAtPosition(i));
        listView.setAdapter(listAdapter);

        Button okButton = findViewById(R.id.ok_button);
        okButton.setOnClickListener(v -> {
            EditText wheelCircumferenceEditText = findViewById(R.id.wheel_circumference_et);
            String wheelCircumferenceText = wheelCircumferenceEditText.getText().toString();

            if (wheelCircumferenceText.equals("")) {
                return;
            }

            double wheelCircumference = Double.parseDouble(wheelCircumferenceText);
            if (wheelCircumference > 0.0 && selectedDevice != null) {
                Intent data = new Intent(SELECTION_FINISHED);
                data.putExtra(EXTRA_RESULT, RESULT_OK);
                data.putExtra(EXTRA_SELECTED_DEVICE, selectedDevice);
                data.putExtra(EXTRA_WHEEL_CIRCUMFERENCE, wheelCircumference);
                sendBroadcast(data);
                setResult(RESULT_OK);
                finish();
            }
        });

        Button cancelButton = findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(v -> cancel());

        Button scanButton = findViewById(R.id.scan_button);
        scanButton.setOnClickListener(v -> scanForDevices());

        if (!mScanning) {
            // Request necessary permissions if not available and start scan after receiving those. If permissions are
            // already granted start scan directly.
            // TODO: should not be required anymore targeting Android 12+, thus, disabled
            /*
             * if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(ACCESS_COARSE_LOCATION) != PERMISSION_GRANTED) {
             * requestPermissions(new String[] {ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
             * } else {
             */
            scanForDevices();
            // }
        }
    }

    /**
     * @param adapter The {@link BluetoothAdapter}, which is the interface to the systems Bluetooth
     *            stack and is used for device discovery.
     */
    void setBluetoothAdapter(final BluetoothAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * Starts scanning for Bluetooth LE devices on a background thread (non-blocking), stopping
     * after {@link #SCAN_PERIOD} has expired.
     */
    private void scanForDevices() {
        if (!mScanning) {
            final Handler scanResultHandler = new Handler();
            Thread scanThread = new Thread(() -> {
                scanResultHandler.postDelayed(() -> {
                    mScanning = false;
                    if (ActivityCompat.checkSelfPermission(this, BLUETOOTH_SCAN) != PERMISSION_GRANTED) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            requestPermissions(new String[] {BLUETOOTH_SCAN}, PERMISSION_REQUEST_BLUETOOTH_SCAN);
                        }
                        return;
                    }
                    adapter.stopLeScan(scanCallback);
                }, SCAN_PERIOD);

                mScanning = true;
                adapter.startLeScan(scanCallback);
            });
            scanThread.start();
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions,
            @NonNull final int[] grantResults) {
        if (requestCode == /* PERMISSION_REQUEST_COARSE_LOCATION */PERMISSION_REQUEST_BLUETOOTH_SCAN) {
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(/* ACCESS_COARSE_LOCATION */BLUETOOTH_SCAN)
                        && grantResults[i] == PERMISSION_GRANTED) {
                    scanForDevices();
                }
            }

        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onBackPressed() {
        cancel();
    }

    /**
     * Called when the dialog is canceled (back pressed or 'Cancel' button). Finishes the activity and informs all
     * listeners via broadcast. No device is selected and no wheel circumference saved.
     */
    private void cancel() {
        Intent data = new Intent(SELECTION_FINISHED);
        data.putExtra(EXTRA_RESULT, RESULT_CANCEL);
        sendBroadcast(data);
        setResult(RESULT_CANCEL);
        finish();
    }
}
