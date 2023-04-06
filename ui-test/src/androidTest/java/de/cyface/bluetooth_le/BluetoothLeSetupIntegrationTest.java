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

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import android.app.Activity;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.widget.TextView;

import de.cyface.ItActivity;

/**
 * @Klemens: This test runs through and opens the dialog to connect to the Bluetooth device.
 *           You can select it, but after you selected it the test just runs endlessly without an assert.
 *           If this is what is to be tested here, please add documentation which describes this.
 *
 *           TODO [CY-3577]: When we actually execute ./gradlew :ui-test:connectedDebugAndroidTest this never ends
 *           as this test does no terminate. Thus, this test is only for manual execution or should be adjusted.
 *
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 1.0.0
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public final class BluetoothLeSetupIntegrationTest {

    Kotlin: @get:Rule
    public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

    Kotlin: @get:Rule
    public ActivityTestRule<ItActivity> activityTestRule = new ActivityTestRule<>(ItActivity.class);

    @Mock
    private BluetoothLeSetupListener bluetoothListener;

    @Test
    public void testSuccessfulSetupProcess() throws InterruptedException {
        BluetoothLeSetup ooCuT = new BluetoothLeSetup(bluetoothListener);
        Activity activity = activityTestRule.getActivity();
        ooCuT.setup(activity);

        while (activity.findViewById(R.id.device_name) == null) {
            Thread.sleep(1000L);
        }
        Log.v(TAG, ((TextView)activity.findViewById(R.id.device_name)).getText().toString());
        Log.v(TAG, ((TextView)activity.findViewById(R.id.device_address)).getText().toString());

        // onView(withId(R.id.devices_lv)).check(matches(isDisplayed()));
        // onData()
    }
}
