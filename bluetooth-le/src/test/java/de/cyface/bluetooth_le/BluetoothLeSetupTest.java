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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyDouble;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;

// TODO [CY-3577]: There should be instrumented integration tests as well, but this is currently not possible with the
// android instrumentation framework, since it provides no support to simulate external hardware.

/**
 * Unit tests for the Bluetooth LE setup process. All dependencies to the Android framework are mocked.
 *
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 1.0.0
 */
@RunWith(MockitoJUnitRunner.class)
public final class BluetoothLeSetupTest {

    /**
     * The listener for the setup events (usually the calling Activity or an object created by the
     * calling activity) is mocked here.
     */
    @Mock
    BluetoothLeSetupListener mockListener;

    /**
     * The mocked context activity calling the setup process.
     */
    @Mock
    Activity mockActivity;

    /**
     * The mocked package manager for getting the mocked Bluetooth stack.
     */
    @Mock
    PackageManager mockPackageManager;

    /**
     * Provides mocked access to a fictitious system Bluetooth stack.
     */
    @Mock
    BluetoothManager mockBluetoothManager;

    /**
     * The mocked Bluetooth adapter to simulate access to Bluetooth functionality.
     */
    @Mock
    BluetoothAdapter mockBluetoothAdapter;

    /**
     * An Object Of the Class Under Test.
     */
    private BluetoothLeSetup oocut;

    @Before
    public void setUp() {
        oocut = new BluetoothLeSetup(mockListener);

        when(mockActivity.getPackageManager()).thenReturn(mockPackageManager);
        when(mockPackageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)).thenReturn(true);
        when(mockActivity.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(mockBluetoothManager);
        when(mockBluetoothManager.getAdapter()).thenReturn(mockBluetoothAdapter);
        when(mockBluetoothAdapter.isEnabled()).thenReturn(true);
    }

    /**
     * Tests the setup process on a fictitious device with Bluetooth support. The expected outcome is
     * that the process tries to start The {@link DeviceSelectionActivity} via {@link Intent}.
     */
    @Test
    public void testReactionToDeviceWithBluetoothSupport() {
        oocut.setup(mockActivity);

        verify(mockActivity).registerReceiver(any(BluetoothLeSetup.DeviceChoiceMessageReceiver.class),
                any(IntentFilter.class));
        verify(mockActivity).startActivity(any(Intent.class));

    }

    /**
     * Tests the setup process on a fictitious device without Bluetooth support. The expected outcome
     * is that the process stops without error but provides a proper reason.
     */
    @Test
    public void testReactionToDeviceWithoutBluetoothSupport() {
        when(mockPackageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)).thenReturn(false);

        oocut.setup(mockActivity);

        verify(mockListener).onSetupProcessFailed(Reason.NOT_SUPPORTED);
        verify(mockListener, times(0)).onDeviceSelected(any(BluetoothDevice.class), anyDouble());
    }

    /**
     * Tests the setup process on a device with no Bluetooth permission granted yet. The expected
     * outcome is that a system dialog is launched, that asks for Bluetooth permission.
     */
    @Test
    public void testGetBluetoothPermission() {
        when(mockBluetoothAdapter.isEnabled()).thenReturn(false);

        oocut.setup(mockActivity);

        // This should actually test if EnableBluetoothEventReceiver is registered only once. But this case seems to be
        // impossible with Mockito.
        verify(mockActivity, times(2)).registerReceiver(any(BroadcastReceiver.class), any(IntentFilter.class));
        verify(mockActivity, times(2)).startActivity(any(Intent.class));
    }
}
