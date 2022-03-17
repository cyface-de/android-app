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
package de.cyface.app;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import de.cyface.utils.Validate;

/**
 * Tests whether the capturing notification is shown correctly. UI Automator is used to carry out these tests.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.2.1
 * @since 3.0.0
 */
@RunWith(AndroidJUnit4.class)
public class CapturingNotificationTest {

    /**
     * A tag to identify log output this test
     */
    private final static String TAG = "de.cyface.app.test.cnt";
    /**
     * Time to wait for the app to start before tests are run.
     */
    private static final int DEFAULT_TIMEOUT = 5_000;
    /**
     * The Cyface app package name. This is used to access the app on the device from UI Automator
     */
    private static final String CYFACE_APP_PACKAGE = "de.cyface.app";
    /**
     * UI Automator handle for the device under test.
     */
    private UiDevice device;

    /**
     * Starts the Cyface App before running tests.
     */
    @Before
    public void startMainActivityFromHomeScreen() {
        // Initialize UiDevice instance
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Start from the home screen
        device.pressHome();

        // Wait for launcher
        final String launcherPackage = device.getLauncherPackageName();
        assertThat(launcherPackage, notNullValue());
        device.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), DEFAULT_TIMEOUT);

        // Launch the app
        Context context = ApplicationProvider.getApplicationContext();
        final Intent intent = context.getPackageManager().getLaunchIntentForPackage(CYFACE_APP_PACKAGE);
        // Clear out any previous instances
        Validate.notNull(intent);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);

        // Wait for the app to appear
        device.wait(Until.hasObject(By.pkg(CYFACE_APP_PACKAGE).depth(0)), DEFAULT_TIMEOUT);
    }

    /**
     * Tests that the notification is shown after a click on play.
     * <p>
     * This test is flaky on the Bitrise CI.
     */
    @Test
    public void test() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // Arrange: Reach MainFragment with capturing button
        acceptTermsIfNeeded(device);
        device.waitForIdle();

        allowPermissionsIfNeeded(device);
        device.waitForIdle();

        loginIfNeeded(device);
        device.waitForIdle();

        allowPermissionsIfNeeded(device);
        device.waitForIdle();

        selectModalityIfNeeded(context, device);
        device.waitForIdle();

        // Wait for the capturing button (flaky on CI)
        // If flakiness comes back, migrate the UI-test dependencies and check again
        final BySelector mainButtonSelector = By.res("de.cyface.app:id/capture_data_main_button");
        device.wait(Until.hasObject(mainButtonSelector), DEFAULT_TIMEOUT);
        assertThat(device.hasObject(mainButtonSelector), is(true));

        // Act - Click capturing button
        final UiObject2 mainButton = device.findObject(mainButtonSelector);
        assertThat(mainButton.isEnabled(), is(true));
        mainButton.click();

        // Act: Open Notification Area
        device.openNotification();
        //noinspection SpellCheckingInspection
        device.wait(Until.hasObject(By.pkg("com.android.systemui")), DEFAULT_TIMEOUT);
        //noinspection SpellCheckingInspection
        final BySelector navigationScroller = By.res("com.android.systemui:id/notification_stack_scroller");
        final UiObject2 notificationArea = device.findObject(navigationScroller);

        // Assert: Notification is shown
        final String notificationText = context.getText(R.string.capturing_active).toString();
        final BySelector notificationTextSelector = By.text(notificationText);
        device.wait(Until.hasObject(notificationTextSelector), DEFAULT_TIMEOUT);
        assertThat(notificationArea.hasObject(notificationTextSelector), is(true));

        // Cleanup
        // Close Notification Area
        device.pressBack();
        // Click Stop Button
        mainButton.wait(Until.hasObject(mainButtonSelector), DEFAULT_TIMEOUT);
        assertThat(device.hasObject(mainButtonSelector), is(true));
        assertThat(mainButton.isEnabled(), is(true));
        mainButton.click();
        // Close App
        device.pressHome();
    }

    private void selectModalityIfNeeded(final Context context, final UiDevice device) {
        final BySelector modalitySelector = By.text(context
                .getResources().getStringArray(R.array.dialog_modality)[1]);
        device.wait(Until.hasObject(modalitySelector), DEFAULT_TIMEOUT);
        if (device.hasObject(modalitySelector)) {
            Log.i(TAG, "Selecting Modality");
            device.findObject(modalitySelector).click();
        }
    }

    private void acceptTermsIfNeeded(final UiDevice device) {
        final BySelector acceptTermsCheckboxSelector = By.res(CYFACE_APP_PACKAGE, "accept_terms_checkbox");
        device.wait(Until.hasObject(acceptTermsCheckboxSelector), DEFAULT_TIMEOUT); // to fix CI
        if (device.hasObject(acceptTermsCheckboxSelector)
                && device.hasObject(By.res(CYFACE_APP_PACKAGE, "accept_terms_button"))) {
            UiObject2 acceptTermsCheckbox = device.findObject(acceptTermsCheckboxSelector);
            assertThat(acceptTermsCheckbox.isCheckable(), is(true));
            acceptTermsCheckbox.click();

            BySelector acceptTermsButtonSelector = By.res(CYFACE_APP_PACKAGE, "accept_terms_button");
            UiObject2 acceptTermsButton = device.findObject(acceptTermsButtonSelector);
            assertThat(acceptTermsButton.isClickable(), is(true));
            assertThat(acceptTermsButton.isEnabled(), is(true));
            Log.i(TAG, "Clicking accept terms button and waiting up to " + DEFAULT_TIMEOUT + " ms");
            acceptTermsButton.clickAndWait(Until.newWindow(), DEFAULT_TIMEOUT);
        }
    }

    private void loginIfNeeded(final UiDevice device) {
        BySelector loginTextBoxSelector = By.res(CYFACE_APP_PACKAGE, "input_login");
        BySelector passwordTextBoxSelector = By.res(CYFACE_APP_PACKAGE, "input_password");
        device.wait(Until.hasObject(loginTextBoxSelector), DEFAULT_TIMEOUT); // else, flaky in CI and locally
        if (device.hasObject(loginTextBoxSelector) && device.hasObject(passwordTextBoxSelector)) {
            UiObject2 loginTextBox = device.findObject(loginTextBoxSelector);
            UiObject2 passwordTextBox = device.findObject(passwordTextBoxSelector);
            UiObject2 loginButton = device.findObject(By.res(CYFACE_APP_PACKAGE, "login_button"));

            loginTextBox.setText(BuildConfig.testLogin);
            passwordTextBox.setText(BuildConfig.testPassword);

            Log.i(TAG, "Clicking login button and waiting up to  " + DEFAULT_TIMEOUT + " ms");
            loginButton.clickAndWait(Until.newWindow(), DEFAULT_TIMEOUT);
        }
    }

    private void allowPermissionsIfNeeded(final UiDevice device) {

        // On Android 10+ the location permission screen changed.
        // We now request "Allow app to access location only while in foreground"
        //noinspection SpellCheckingInspection
        final BySelector allowButtonSelector = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? By.res("com.android.permissioncontroller:id/permission_allow_foreground_only_button")
                : By.res("com.android.packageinstaller:id/permission_allow_button");

        // Location permission added in Android M
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.wait(Until.hasObject(allowButtonSelector), DEFAULT_TIMEOUT);
            if (device.hasObject(allowButtonSelector)) {
                Log.i(TAG, "Clicking allow permissions button and waiting up to  " + DEFAULT_TIMEOUT + " ms");
                device.findObject(allowButtonSelector).clickAndWait(Until.newWindow(), DEFAULT_TIMEOUT);
            }
        }
    }
}
