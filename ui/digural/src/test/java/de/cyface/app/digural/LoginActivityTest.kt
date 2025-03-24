/*
 * Copyright 2017-2021 Cyface GmbH
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
package de.cyface.app.digural

import com.google.android.material.textfield.TextInputEditText
import de.cyface.app.digural.auth.LoginActivity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.util.regex.Pattern

/**
 * Unit tests for the [LoginActivity]. All dependencies to the Android framework are mocked.
 *
 * @author Armin Schnabel
 * @version 1.0.11
 * @since 1.0.0
 */
@RunWith(MockitoJUnitRunner::class)
class LoginActivityTest {
    @Mock
    private val inputEditText: TextInputEditText? = null

    /**
     * An Object Of the Class Under Test.
     */
    private var oocut: LoginActivity? = null

    @Before
    fun setUp() {
        oocut = LoginActivity()
        oocut!!.loginInput = inputEditText
        oocut!!.passwordInput = inputEditText
    }

    /**
     * Tests the validation of the login input with valid and incorrect formatted data.
     */
    @Test
    fun testInputValidation() {
        oocut!!.eMailPattern = EMAIL_ADDRESS

        // Correct credential format
        val validInputResult = oocut!!.credentialsAreValid("test@cyface.de", "test12", true)
        // Missing password
        val invalidInputResult1 = oocut!!.credentialsAreValid("test@cyface.de", "", true)
        // Missing email
        val invalidInputResult2 = oocut!!.credentialsAreValid("", "test", true)
        // Password overlong
        val invalidInputResult3 = oocut!!.credentialsAreValid(
            "test@cyface.de",
            "1234567890123456789012345678901234567890",
            true
        )
        // null password
        val invalidInputResult4 = oocut!!.credentialsAreValid("test@cyface.de", null, true)
        // null email
        val invalidInputResult5 = oocut!!.credentialsAreValid(null, "test", true)

        require(validInputResult)
        require(!invalidInputResult1)
        require(!invalidInputResult2)
        require(!invalidInputResult3)
        require(!invalidInputResult4)
        require(!invalidInputResult5)
    }

    companion object {
        /**
         * We cannot access the Android framework's Pattern and we can't mock it away directly via
         * mocking away the [LoginActivity] as we want to test this activity as the same time.
         * For this reason, we inject a replacement pattern into the LoginActivity.
         */
        private const val EMAIL_PATTERN = ("^[_A-Za-z0-9-+]+(\\.[_A-Za-z0-9-]+)*@"
                + "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$")
        private val EMAIL_ADDRESS: Pattern = Pattern.compile(EMAIL_PATTERN)
    }
}
