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

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

/**
 * Adapter for holding devices found through scanning. This class is copied from the Android SDK
 * Bluetooth LE example project.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
class LeDeviceListAdapter extends ArrayAdapter<BluetoothDevice> {
    /**
     * <p>
     * The inflater used to refresh the list upon detection of new devices.
     * </p>
     */
    private final LayoutInflater mInflater;

    /**
     * Creates a new completely initialized {@link LeDeviceListAdapter} with the provided {@link
     * Activity} as context.
     *
     * @param activity The context activity.
     */
    LeDeviceListAdapter(final Activity activity) {
        super(activity, R.layout.listitem_device);
        mInflater = activity.getLayoutInflater();
    }

    @Override
    @NonNull
    public View getView(int i, View view, @NonNull ViewGroup viewGroup) {
        ViewHolder viewHolder;
        // General ListView optimization code.
        if (view == null) {
            view = mInflater.inflate(R.layout.listitem_device, null);
            viewHolder = new ViewHolder();
            viewHolder.deviceAddress = view.findViewById(R.id.device_address);
            viewHolder.deviceName = view.findViewById(R.id.device_name);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder)view.getTag();
        }

        BluetoothDevice device = getItem(i);

        if (device == null) {
            viewHolder.deviceName.setText(getContext().getText(R.string.error_no_valid_device));
            viewHolder.deviceAddress.setText(getContext().getText(R.string.error_no_valid_device));
        } else {
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.unknown_device);
            viewHolder.deviceAddress.setText(device.getAddress());
        }

        return view;
    }

    /**
     * View holder for the relevant UI elements inside one list item entry layout.
     *
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 1.0.0
     */
    private static class ViewHolder {
        TextView deviceAddress;
        TextView deviceName;
    }
}
