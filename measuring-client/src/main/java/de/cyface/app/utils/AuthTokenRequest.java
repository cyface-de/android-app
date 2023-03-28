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
package de.cyface.app.utils;

import static de.cyface.app.utils.SharedConstants.ACCEPTED_REPORTING_KEY;
import static de.cyface.app.utils.Constants.ACCOUNT_TYPE;
import static de.cyface.app.utils.Constants.TAG;
import static de.cyface.synchronization.Constants.AUTH_TOKEN_TYPE;

import java.lang.ref.WeakReference;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.app.ui.LoginActivity;
import de.cyface.synchronization.CyfaceAuthenticator;
import de.cyface.utils.Validate;
import io.sentry.Sentry;

/**
 * Asynchronous Request to get a new auth token.
 * <p>
 * If no credentials are stored the {@link LoginActivity} is opened first.
 * <p>
 * <b>ATTENTION:</b> In order to notice if the task was successful you need to overwrite the method
 * {@link AsyncTask#onPostExecute(Object)} and check {@link AuthTokenRequestParams#success}.
 * <p>
 * TODO [CY-3737]: This class should be removed and the methods moved to the WiFiSurveyor with different names
 *
 * @author Armin Schnabel
 * @version 4.0.3
 * @since 1.0.0
 */
public abstract class AuthTokenRequest extends AsyncTask<Void, Void, AuthTokenRequest.AuthTokenRequestParams> {

    /**
     * The {@link Context} required to access the {@link AccountManager}
     */
    private final WeakReference<Context> contextReference;

    protected AuthTokenRequest(@NonNull final Context contextReference) {
        this.contextReference = new WeakReference<>(contextReference);
    }

    @Override
    protected AuthTokenRequestParams doInBackground(Void... voids) {
        final Context context = contextReference.get();
        if (context == null) {
            Log.w(TAG, "Context reference is null, ignoring task.");
            return null;
        }
        final Account account = getAccount(context);

        // Explicitly calling CyfaceAuthenticator.getAuthToken(), see its documentation
        final CyfaceAuthenticator cyfaceAuthenticator = new CyfaceAuthenticator(context);
        final String authToken;
        try {
            // AsyncTask because this is blocking but only for a short time
            authToken = cyfaceAuthenticator.getAuthToken(null, account, AUTH_TOKEN_TYPE, null)
                    .getString(AccountManager.KEY_AUTHTOKEN);
        } catch (final NetworkErrorException e) {
            // We cannot capture the exceptions in CyfaceAuthenticator as it's part of the SDK.
            // We also don't want to capture the errors in the error handler as we don't have the stacktrace there
            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            final boolean isReportingEnabled = preferences.getBoolean(ACCEPTED_REPORTING_KEY, false);
            if (isReportingEnabled) {
                Sentry.captureException(e);
            }
            // "the authenticator could not honor the request due to a network error"
            return new AuthTokenRequestParams(account, false);
        }

        Validate.notNull(authToken);
        Log.d(TAG, "Setting auth token to: **" + authToken.substring(authToken.length() - 7));
        final AccountManager accountManager = AccountManager.get(context);
        accountManager.setAuthToken(account, AUTH_TOKEN_TYPE, authToken);
        return new AuthTokenRequestParams(account, true);
    }

    /**
     * Returns the Cyface account. Throws a Runtime Exception if no or more than one account exists.
     *
     * @param context the Context to load the AccountManager
     * @return the only existing account
     */
    private static Account getAccount(final Context context) {
        final AccountManager accountManager = AccountManager.get(context);
        final Account[] existingAccounts = accountManager.getAccountsByType(ACCOUNT_TYPE);
        Validate.isTrue(existingAccounts.length < 2, "More than one account exists.");
        Validate.isTrue(existingAccounts.length > 0, "No account exists.");
        return existingAccounts[0];
    }

    public WeakReference<Context> getContext() {
        return contextReference;
    }

    /**
     * Parameters returned from {@link #doInBackground(Void...)} containing information weather the task was successful.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 2.3.0
     */
    public static class AuthTokenRequestParams {
        private final Account account;
        private final boolean success;

        AuthTokenRequestParams(@NonNull final Account account, final boolean success) {
            this.account = account;
            this.success = success;
        }

        public Account getAccount() {
            return account;
        }

        public boolean isSuccessful() {
            return success;
        }
    }
}
