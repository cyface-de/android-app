/*
 * Copyright 2017-2023 Cyface GmbH
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
package de.cyface.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import de.cyface.utils.Validate
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests whether the capturing notification is shown correctly. UI Automator is used to carry out these tests.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.2.1
 * @since 3.0.0
 */
@RunWith(AndroidJUnit4::class)
class CapturingNotificationTest {
    /**
     * UI Automator handle for the device under test.
     */
    private var device: UiDevice? = null

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.POST_NOTIFICATIONS
    )

    /**
     * Starts the Cyface App before running tests.
     */
    @Before
    fun startMainActivityFromHomeScreen() {
        // Initialize UiDevice instance
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // Start from the home screen
        device!!.pressHome()

        // Wait for launcher
        val launcherPackage = device!!.launcherPackageName
        MatcherAssert.assertThat(launcherPackage, CoreMatchers.notNullValue())
        device!!.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), DEFAULT_TIMEOUT.toLong())

        // Launch the app
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = context.packageManager.getLaunchIntentForPackage(CYFACE_APP_PACKAGE)
        // Clear out any previous instances
        Validate.notNull(intent)
        intent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)

        // Wait for the app to appear
        device!!.wait(
            Until.hasObject(By.pkg(CYFACE_APP_PACKAGE).depth(0)),
            DEFAULT_TIMEOUT.toLong()
        )
    }

    /**
     * Tests that the notification is shown after a click on play.
     *
     * This test is flaky on the Bitrise CI.
     */
    @Test
    fun test() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Arrange: Reach MainFragment with capturing button
        acceptTermsIfNeeded(device)
        device!!.waitForIdle()

        loginIfNeeded(device)
        device!!.waitForIdle()
        allowLocationPermissionsIfNeeded(device)
        device!!.waitForIdle()
        selectModalityIfNeeded(context, device)
        device!!.waitForIdle()
        // Notification permissions introduced in Android 13 are granted via `GrantPermissionRule`

        // Wait for the capturing button (flaky on CI)
        // If flakiness comes back, migrate the UI-test dependencies and check again
        val mainButtonSelector = By.res("de.cyface.app:id/capture_data_main_button")
        device!!.wait(Until.hasObject(mainButtonSelector), DEFAULT_TIMEOUT.toLong())
        MatcherAssert.assertThat(device!!.hasObject(mainButtonSelector), Matchers.`is`(true))

        // Act - Click capturing button
        val mainButton = device!!.findObject(mainButtonSelector)
        MatcherAssert.assertThat(mainButton.isEnabled, Matchers.`is`(true))
        mainButton.click()
        device!!.waitForIdle()

        // Act: Open Notification Area
        device!!.openNotification()
        @Suppress("SpellCheckingInspection")
        device!!.wait(Until.hasObject(By.pkg("com.android.systemui")), DEFAULT_TIMEOUT.toLong())
        @Suppress("SpellCheckingInspection")
        val navigationScroller = By.res("com.android.systemui:id/notification_stack_scroller")
        val notificationArea = device!!.findObject(navigationScroller)

        // Assert: Notification is shown
        val notificationText = context.getText(R.string.capturing_active).toString()
        val notificationTextSelector = By.text(notificationText)
        val waitLonger = DEFAULT_TIMEOUT.toLong() * 2 // Android 13 emulator takes longer
        device!!.wait(Until.hasObject(notificationTextSelector), waitLonger)
        MatcherAssert.assertThat(
            notificationArea.hasObject(notificationTextSelector),
            Matchers.`is`(true)
        )

        // Cleanup
        // Close Notification Area
        device!!.pressBack()
        // Click Stop Button
        mainButton.wait(Until.hasObject(mainButtonSelector), DEFAULT_TIMEOUT.toLong())
        MatcherAssert.assertThat(device!!.hasObject(mainButtonSelector), Matchers.`is`(true))
        MatcherAssert.assertThat(mainButton.isEnabled, Matchers.`is`(true))
        mainButton.click()
        // Close App
        device!!.pressHome()
    }

    private fun selectModalityIfNeeded(context: Context, device: UiDevice?) {
        val modalitySelector = By.text(
            context
                .resources.getStringArray(R.array.dialog_modality)[1]
        )
        device!!.wait(Until.hasObject(modalitySelector), DEFAULT_TIMEOUT.toLong())
        if (device.hasObject(modalitySelector)) {
            Log.i(TAG, "Selecting Modality")
            device.findObject(modalitySelector).click()
        }
    }

    private fun acceptTermsIfNeeded(device: UiDevice?) {
        val acceptTermsCheckboxSelector = By.res(CYFACE_APP_PACKAGE, "accept_terms_checkbox")
        device!!.wait(
            Until.hasObject(acceptTermsCheckboxSelector),
            DEFAULT_TIMEOUT.toLong()
        ) // to fix CI
        if (device.hasObject(acceptTermsCheckboxSelector)
            && device.hasObject(By.res(CYFACE_APP_PACKAGE, "accept_terms_button"))
        ) {
            val acceptTermsCheckbox = device.findObject(acceptTermsCheckboxSelector)
            MatcherAssert.assertThat(acceptTermsCheckbox.isCheckable, Matchers.`is`(true))
            acceptTermsCheckbox.click()
            val acceptTermsButtonSelector = By.res(CYFACE_APP_PACKAGE, "accept_terms_button")
            val acceptTermsButton = device.findObject(acceptTermsButtonSelector)
            MatcherAssert.assertThat(acceptTermsButton.isClickable, Matchers.`is`(true))
            MatcherAssert.assertThat(acceptTermsButton.isEnabled, Matchers.`is`(true))
            Log.i(TAG, "Clicking accept terms button and waiting up to $DEFAULT_TIMEOUT ms")
            acceptTermsButton.clickAndWait(Until.newWindow(), DEFAULT_TIMEOUT.toLong())
        }
    }

    private fun loginIfNeeded(device: UiDevice?) {
        val loginTextBoxSelector = By.res(CYFACE_APP_PACKAGE, "input_login")
        val passwordTextBoxSelector = By.res(CYFACE_APP_PACKAGE, "input_password")
        device!!.wait(
            Until.hasObject(loginTextBoxSelector),
            DEFAULT_TIMEOUT.toLong()
        ) // else, flaky in CI and locally
        if (device.hasObject(loginTextBoxSelector) && device.hasObject(passwordTextBoxSelector)) {
            val loginTextBox = device.findObject(loginTextBoxSelector)
            val passwordTextBox = device.findObject(passwordTextBoxSelector)
            val loginButton = device.findObject(By.res(CYFACE_APP_PACKAGE, "login_button"))
            loginTextBox.text = BuildConfig.testLogin
            passwordTextBox.text = BuildConfig.testPassword
            Log.i(TAG, "Clicking login button and waiting up to  $DEFAULT_TIMEOUT ms")
            loginButton.clickAndWait(Until.newWindow(), DEFAULT_TIMEOUT.toLong())
        }
    }

    private fun allowLocationPermissionsIfNeeded(device: UiDevice?) {

        // On Android 10+ the location permission screen changed.
        // We now request "Allow app to access location only while in foreground"
        val allowButtonSelector =
            @Suppress("SpellCheckingInspection")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) By.res("com.android.permissioncontroller:id/permission_allow_foreground_only_button") else By.res(
                "com.android.packageinstaller:id/permission_allow_button"
            )

        // Location permission added in Android M
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device!!.wait(Until.hasObject(allowButtonSelector), DEFAULT_TIMEOUT.toLong())
            if (device.hasObject(allowButtonSelector)) {
                Log.i(TAG, "Click allow permissions and waiting up to  $DEFAULT_TIMEOUT ms")
                device.findObject(allowButtonSelector)
                    .clickAndWait(Until.newWindow(), DEFAULT_TIMEOUT.toLong())
            }
        }
    }

    companion object {
        /**
         * A tag to identify log output this test
         */
        private const val TAG = "de.cyface.app.test.cnt"

        /**
         * Time to wait for the app to start before tests are run.
         */
        private const val DEFAULT_TIMEOUT = 5000

        /**
         * The Cyface app package name. This is used to access the app on the device from UI Automator
         */
        private const val CYFACE_APP_PACKAGE = "de.cyface.app"
    }
}