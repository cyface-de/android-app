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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.content.Context;

// TODO [CY-3577]: This test currently does not much. Since we have no access to a Bluetooth device during testing and
// there is no simulation it is not possible to call the functions listening to Bluetooth events via the GATT and thus
// it is not possible to really test the process.

/**
 * Tests whether the process of measuring speed with a CSC-sensor works correctly.
 *
 * @author Klemens Muthmann
 * @version 1.0.2
 * @since 1.0.0
 */
@RunWith(MockitoJUnitRunner.class)
public final class CyclingCadenceSpeedMeasurementDeviceTest {

    /**
     * The mocked system context used by the test.
     */
    @Mock
    Context mockContext;

    /**
     * The mocked Bluetooth LE device used by the tests.
     */
    @Mock
    BluetoothDevice mockDevice;

    /**
     * The mocked object to access the fictitious system's Bluetooth LE stack.
     */
    @Mock
    BluetoothGatt mockGatt;

    /**
     * An Object Of the Class Under Test.
     */
    private CyclingCadenceSpeedMeasurementDevice oocut;

    @Before
    public void setUp() {
        oocut = new CyclingCadenceSpeedMeasurementDevice(mockContext, 10.2, mockDevice) {
            @Override
            public void onSpeedChanged(double newSpeed) {
            }

            @Override
            public void onDisconnected() {
            }

            @Override
            public void onConnected() {
            }
        };

        when(mockDevice.connectGatt(eq(mockContext), anyBoolean(), any(BluetoothGattCallback.class)))
                .thenReturn(mockGatt);

    }

    /**
     * Tests starting and stopping the device. Expected outcome is an established connection to the
     * mocked GATT followed by a close operation.
     */
    @Test
    public void testStartStop() {
        oocut.startReceivingUpdates();
        oocut.stopReceivingUpdates();

        InOrder inOrder = inOrder(mockDevice, mockGatt);

        inOrder.verify(mockDevice).connectGatt(eq(mockContext), anyBoolean(), any(BluetoothGattCallback.class));
        inOrder.verify(mockGatt).close();
    }

    // TODO [CY-3577]: Do we really expect two calls? Shouldn't this be idempotent? Is connectGatt idempotent?
    /**
     * Tests what happens if start is called twice in a row. Expected outcome are two calls to GATT
     * connections.
     */
    @Test
    public void testStartStart() {
        oocut.startReceivingUpdates();
        oocut.startReceivingUpdates();

        InOrder inOrder = inOrder(mockDevice);

        inOrder.verify(mockDevice, times(2)).connectGatt(eq(mockContext), anyBoolean(),
                any(BluetoothGattCallback.class));
    }

    /**
     * Tests what happens if start and stop are called in reverse order. Expected outcome is that
     * close is never called and connectGatt once.
     */
    @Test
    public void testStopStart() {
        oocut.stopReceivingUpdates();
        oocut.startReceivingUpdates();

        InOrder inOrder = inOrder(mockGatt, mockDevice);

        inOrder.verify(mockGatt, never()).close();
        inOrder.verify(mockDevice).connectGatt(eq(mockContext), anyBoolean(), any(BluetoothGattCallback.class));
    }
}
