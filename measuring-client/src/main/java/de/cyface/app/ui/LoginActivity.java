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
package de.cyface.app.ui;

import static de.cyface.app.utils.Constants.ACCOUNT_TYPE;
import static de.cyface.app.utils.Constants.AUTHORITY;
import static de.cyface.app.utils.Constants.PREFERENCES_SERVER_KEY;
import static de.cyface.app.utils.Constants.SUPPORT_EMAIL;
import static de.cyface.app.utils.Constants.TAG;
import static de.cyface.energy_settings.TrackingSettings.generateFeedbackEmailIntent;
import static de.cyface.synchronization.Constants.AUTH_TOKEN_TYPE;

import java.util.regex.Pattern;

import com.google.android.material.textfield.TextInputEditText;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.apache.commons.lang3.Validate;

import de.cyface.app.BuildConfig;
import de.cyface.app.MeasuringClient;
import de.cyface.app.R;
import de.cyface.app.utils.AuthTokenRequest;
import de.cyface.app.utils.Constants;
import de.cyface.synchronization.CyfaceAuthenticator;
import de.cyface.synchronization.ErrorHandler;
import de.cyface.synchronization.WiFiSurveyor;

/**
 * A login screen that offers login via email/password.
 *
 * @author Armin Schnabel
 * @version 3.2.5
 * @since 1.0.0
 */
public class LoginActivity extends AccountAuthenticatorActivity {

    private SharedPreferences preferences;
    // True if the login must match the email pattern TODO [CY-4099]: Needs to be configurable from outside
    private final static boolean loginMustBeAnEmailAddress = false;
    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private AuthTokenRequest loginTask = null;
    // UI references
    private ProgressBar progressBar;
    /**
     * A {@code Button} which is used to confirm the entered credentials.
     */
    private Button loginButton;
    /**
     * A {@code Button} which is used to use guest credentials to log in.
     */
    private Button guestLoginButton;
    /**
     * Needs to be resettable for testing. That's the only way to mock a single method of Android's Activity's
     */
    TextInputEditText loginInput;
    TextInputEditText passwordInput;
    /**
     * Needs to be resettable for testing. That's the only way to mock a single method of Android's Activity's
     */
    Pattern eMailPattern = Patterns.EMAIL_ADDRESS;

    private final ErrorHandler.ErrorListener errorListener = new ErrorHandler.ErrorListener() {
        @Override
        public void onErrorReceive(final ErrorHandler.ErrorCode errorCode, final String errorMessage) {
            if (errorCode == ErrorHandler.ErrorCode.UNAUTHORIZED) {
                passwordInput.setError(errorMessage);
                passwordInput.requestFocus();

                // All other errors are shown as toast by the MeasuringClient
            }
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        setServerUrl(); // TODO [CY-3735]: via Android's settings

        // Set up the login form
        loginInput = findViewById(R.id.input_login);
        passwordInput = findViewById(R.id.input_password);
        loginButton = findViewById(R.id.login_button);
        loginButton.setOnClickListener(view -> attemptLogin(false));
        guestLoginButton = findViewById(R.id.guest_login_button);
        guestLoginButton.setOnClickListener(view -> attemptLogin(true));

        progressBar = findViewById(R.id.login_progress_bar);
        registerFeedbackLink();

        MeasuringClient.getErrorHandler().addListener(errorListener);
    }

    @Override
    protected void onDestroy() {
        MeasuringClient.getErrorHandler().removeListener(errorListener);
        super.onDestroy();
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     *
     * @param isGuestLogin {@code True} if the user does not provide own credentials
     */
    private void attemptLogin(final boolean isGuestLogin) {
        if (loginTask != null) {
            Log.d(TAG, "Auth is already in progress, ignoring attemptLogin().");
            return; // Auth is already in progress
        }

        // Update view
        loginInput.setError(null);
        passwordInput.setError(null);
        loginButton.setEnabled(false);
        guestLoginButton.setEnabled(false);

        String login = BuildConfig.guestLogin;
        String password = BuildConfig.guestPassword;
        if (!isGuestLogin) {
            // Check for valid credentials
            Validate.notNull(loginInput.getText());
            Validate.notNull(passwordInput.getText());
            login = loginInput.getText().toString();
            password = passwordInput.getText().toString();
            if (!credentialsAreValid(login, password, loginMustBeAnEmailAddress)) {
                loginButton.setEnabled(true);
                guestLoginButton.setEnabled(true);
                return;
            }
        }

        // Update view
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.VISIBLE);

        // The CyfaceAuthenticator reads the credentials from the account so we store them there
        updateAccount(this, login, password);

        // Send async login attempt
        // TODO [CY-3737]: warning will be resolved when moving the task to WifiSurveyor
        loginTask = new AuthTokenRequest(this) {

            @Override
            protected void onPostExecute(@NonNull final AuthTokenRequestParams params) {

                loginTask = null;
                progressBar.setVisibility(View.GONE);

                if (!params.isSuccessful()) {
                    Log.d(TAG, "Login failed - removing account to allow new login.");
                    // Clean up if the getAuthToken failed, else the LoginActivity is probably not shown
                    deleteAccount(this.getContext().get(), params.getAccount());
                    loginButton.setEnabled(true);
                    guestLoginButton.setEnabled(true);
                    return;
                }

                // Equals tutorial's "finishLogin()"
                final Intent intent = new Intent();
                intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, params.getAccount().name);
                intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, ACCOUNT_TYPE);
                intent.putExtra(AccountManager.KEY_AUTHTOKEN, AUTH_TOKEN_TYPE);

                // Return the information back to the Authenticator
                setAccountAuthenticatorResult(intent.getExtras());
                setResult(RESULT_OK, intent);
                finish();
            }

            @Override
            protected void onCancelled() {
                Log.d(TAG, "LoginTask canceled.");
                loginTask = null;
                progressBar.setVisibility(View.GONE);
            }
        };
        loginTask.execute();
    }

    /**
     * Checks if the format of the credentials provided is valid, i.e. has the allowed length and
     * is not empty and, if requested, checks if the login is an email address.
     *
     * @param login The login string
     * @param password The password string
     * @param loginMustBeAnEmailAddress True if the login should be checked to be a valid email address
     * @return true is the credentials are in a valid format
     */
    boolean credentialsAreValid(final String login, final String password, final boolean loginMustBeAnEmailAddress) {

        boolean valid = true;
        if (login == null || login.isEmpty()) {
            loginInput.setError(getString(R.string.error_message_field_required));
            loginInput.requestFocus();
            valid = false;
        } else if (login.length() < 4) {
            loginInput.setError(getString(R.string.error_message_login_too_short));
            loginInput.requestFocus();
            valid = false;
        } else if (loginMustBeAnEmailAddress && !eMailPattern.matcher(login).matches()) {
            loginInput.setError(getString(R.string.error_message_invalid_email));
            loginInput.requestFocus();
            valid = false;
        }
        if (password == null || password.isEmpty()) {
            passwordInput.setError(getString(R.string.error_message_field_required));
            passwordInput.requestFocus();
            valid = false;
        } else if (password.length() < 4) {
            passwordInput.setError(getString(R.string.error_message_password_too_short));
            passwordInput.requestFocus();
            valid = false;
        } else if (password.length() > 20) {
            passwordInput.setError(getString(R.string.error_message_password_too_short));
            passwordInput.requestFocus();
            valid = false;
        }

        return valid;
    }

    /**
     * As long as the server URL is hardcoded we want to reset it when it's different from the
     * default URL set in the {@link BuildConfig}. If not, hardcoded updates would not have an
     * effect.
     */
    private void setServerUrl() {
        final String storedServer = preferences.getString(Constants.PREFERENCES_SERVER_KEY, null);
        Validate.notNull(BuildConfig.cyfaceServer);
        if (storedServer == null || !storedServer.equals(BuildConfig.cyfaceServer)) {
            Log.d(TAG, "Updating Cyface Server API URL from " + storedServer + "to" + BuildConfig.cyfaceServer);
            final SharedPreferences.Editor editor = preferences.edit();
            editor.putString(PREFERENCES_SERVER_KEY, BuildConfig.cyfaceServer);
            editor.apply();
        }
    }

    private void registerFeedbackLink() {
        final Intent emailIntent = generateFeedbackEmailIntent(this, getString(R.string.your_request), SUPPORT_EMAIL);
        final TextView feedbackLink = findViewById(R.id.login_link_contact_us);
        feedbackLink.setOnClickListener(
                v -> startActivity(Intent.createChooser(emailIntent, getString(R.string.feedback_choose_email_app))));
    }

    /**
     * Updates the credentials
     *
     * @param context The {@link Context} required to add an {@link Account}
     * @param login The username of the account
     * @param password The password of the account
     */
    private static void updateAccount(final Context context, final String login, final String password) {
        Validate.notEmpty(login);
        Validate.notEmpty(password);

        final AccountManager accountManager = AccountManager.get(context);
        final Account account = new Account(login, ACCOUNT_TYPE);

        // Update credentials if the account already exists
        boolean accountUpdated = false;
        final Account[] existingAccounts = accountManager.getAccountsByType(ACCOUNT_TYPE);
        for (final Account existingAccount : existingAccounts) {
            if (existingAccount.equals(account)) {
                accountManager.setPassword(account, password);
                accountUpdated = true;
                Log.d(TAG, "Updated existing account.");
            }
        }

        // Add new account when it does not yet exist
        if (!accountUpdated) {

            // Delete unused Cyface accounts
            for (final Account existingAccount : existingAccounts) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    accountManager.removeAccountExplicitly(existingAccount);
                } else {
                    accountManager.removeAccount(account, null, null);
                }
                Log.d(TAG, "Removed existing account: " + existingAccount);
            }

            createAccount(context, login, password);
        }

        Validate.isTrue(accountManager.getAccountsByType(ACCOUNT_TYPE).length == 1);
    }

    /**
     * Creates a temporary {@code Account} which can only be used to check the credentials.
     * <p>
     * <b>ATTENTION:</b> If the login is successful you need to use
     * {@link WiFiSurveyor#makeAccountSyncable(Account, boolean)}
     * to ensure the {@code WifiSurveyor} works as expected. We cannot inject the {@link WiFiSurveyor} as the
     * {@link LoginActivity} is called by Android.
     *
     * @param context The current Android context (i.e. Activity or Service).
     * @param username The username of the account to be created.
     * @param password The password of the account to be created. May be null if a custom {@link CyfaceAuthenticator} is
     *            used instead of a LoginActivity to return tokens as in {@code MovebisDataCapturingService}.
     */
    private static void createAccount(@NonNull final Context context, @NonNull final String username,
            @NonNull final String password) {

        final AccountManager accountManager = AccountManager.get(context);
        final Account newAccount = new Account(username, Constants.ACCOUNT_TYPE);
        Validate.isTrue(accountManager.addAccountExplicitly(newAccount, password, Bundle.EMPTY));
        Validate.isTrue(accountManager.getAccountsByType(Constants.ACCOUNT_TYPE).length == 1);
        Log.v(de.cyface.synchronization.Constants.TAG, "New account added");

        ContentResolver.setSyncAutomatically(newAccount, AUTHORITY, false);
        // Synchronization can be disabled via {@link CyfaceDataCapturingService#setSyncEnabled}
        ContentResolver.setIsSyncable(newAccount, AUTHORITY, 1);
        // Do not use validateAccountFlags in production code as periodicSync flags are set async

        // PeriodicSync and syncAutomatically is set dynamically by the {@link WifiSurveyor}
    }

    /**
     * This method removes the existing account. This is useful as we add a temporary account to check
     * the credentials but we have to remove it when the credentials are incorrect.
     * <p>
     * This static method must be implemented as in the non-static {@link WiFiSurveyor#deleteAccount(String)}.
     * We cannot inject the {@link WiFiSurveyor} as the {@link LoginActivity} is called by Android.
     *
     * @param context The {@link Context} to get the {@link AccountManager}
     * @param account the {@code Account} to be removed
     */
    private static void deleteAccount(@NonNull final Context context, @NonNull final Account account) {

        ContentResolver.removePeriodicSync(account, AUTHORITY, Bundle.EMPTY);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            AccountManager.get(context).removeAccount(account, null, null);
        } else {
            AccountManager.get(context).removeAccountExplicitly(account);
        }
    }
}
