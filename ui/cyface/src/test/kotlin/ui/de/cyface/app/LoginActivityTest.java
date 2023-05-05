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
package ui.de.cyface.app;

import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.google.android.material.textfield.TextInputEditText;

import de.cyface.app.LoginActivity;
import de.cyface.utils.Validate;

/**
 * Unit tests for the {@link LoginActivity}. All dependencies to the Android framework are mocked.
 *
 * @author Armin Schnabel
 * @version 1.0.11
 * @since 1.0.0
 */
@RunWith(MockitoJUnitRunner.class)
public final class LoginActivityTest {

    @Mock
    private TextInputEditText inputEditText;
    /**
     * An Object Of the Class Under Test.
     */
    private LoginActivity oocut;

    /**
     * We cannot access the Android framework's Pattern and we can't mock it away directly via
     * mocking away the {@link LoginActivity} as we want to test this activity as the same time.
     * For this reason, we inject a replacement pattern into the LoginActivity.
     */
    private static final String EMAIL_PATTERN = "^[_A-Za-z0-9-+]+(\\.[_A-Za-z0-9-]+)*@"
            + "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
    private static final Pattern EMAIL_ADDRESS = Pattern.compile(EMAIL_PATTERN);

    @Before
    public void setUp() {
        oocut = new LoginActivity();
        oocut.loginInput = inputEditText;
        oocut.passwordInput = inputEditText;
    }

    /**
     * Tests the validation of the login input with valid and incorrect formatted data.
     */
    @Test
    public void testInputValidation() {
        oocut.eMailPattern = EMAIL_ADDRESS;

        // Correct credential format
        boolean validInputResult = oocut.credentialsAreValid("test@cyface.de", "test12", true);
        // Missing password
        boolean invalidInputResult1 = oocut.credentialsAreValid("test@cyface.de", "", true);
        // Missing email
        boolean invalidInputResult2 = oocut.credentialsAreValid("", "test", true);
        // Password overlong
        boolean invalidInputResult3 = oocut.credentialsAreValid("test@cyface.de", "1234567890123456789012345", true);
        // null password
        boolean invalidInputResult4 = oocut.credentialsAreValid("test@cyface.de", null, true);
        // null email
        boolean invalidInputResult5 = oocut.credentialsAreValid(null, "test", true);

        Validate.isTrue(validInputResult);
        Validate.isTrue(!invalidInputResult1);
        Validate.isTrue(!invalidInputResult2);
        Validate.isTrue(!invalidInputResult3);
        Validate.isTrue(!invalidInputResult4);
        Validate.isTrue(!invalidInputResult5);
    }
}
