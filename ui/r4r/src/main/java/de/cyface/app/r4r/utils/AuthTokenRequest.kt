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
package de.cyface.app.r4r.utils

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.NetworkErrorException
import android.content.Context
import android.content.SharedPreferences
import android.os.AsyncTask
import android.preference.PreferenceManager
import android.util.Log
import de.cyface.app.utils.SharedConstants.ACCEPTED_REPORTING_KEY
import de.cyface.app.r4r.utils.Constants.ACCOUNT_TYPE
import de.cyface.app.r4r.utils.Constants.TAG
import de.cyface.synchronization.Constants
import de.cyface.synchronization.CyfaceAuthenticator
import de.cyface.utils.Validate
import io.sentry.Sentry
import java.lang.ref.WeakReference

/**
 * Asynchronous Request to get a new auth token.
 *
 * If no credentials are stored the [de.cyface.app.r4r.ui.LoginActivity] is opened first.
 *
 * **ATTENTION:** In order to notice if the task was successful you need to overwrite the method
 * `AsyncTask.onPostExecute` and check `isSuccessful`.
 *
 * TODO [CY-3737]: This class should be removed and the methods moved to the WiFiSurveyor with different names
 *
 * @author Armin Schnabel
 * @version 4.0.3
 * @since 1.0.0
 */
// FIXME Replace AsyncTask
abstract class AuthTokenRequest protected constructor(contextReference: Context) :
    AsyncTask<Void?, Void?, AuthTokenRequest.AuthTokenRequestParams?>() {
    /**
     * The [Context] required to access the [AccountManager]
     */
    val context: WeakReference<Context>

    init {
        context = WeakReference(contextReference)
    }

    @Deprecated("Use the standard java.util.concurrent or Kotlin concurrency utilities instead.")
    override fun doInBackground(vararg voids: Void?): AuthTokenRequestParams? {
        val context = context.get()
        if (context == null) {
            Log.w(TAG, "Context reference is null, ignoring task.")
            return null
        }
        val account: Account = getAccount(context)

        // Explicitly calling CyfaceAuthenticator.getAuthToken(), see its documentation
        val cyfaceAuthenticator = CyfaceAuthenticator(context)
        val authToken: String = try {
            // AsyncTask because this is blocking but only for a short time
            cyfaceAuthenticator.getAuthToken(null, account, Constants.AUTH_TOKEN_TYPE, null)
                .getString(AccountManager.KEY_AUTHTOKEN)!!
        } catch (e: NetworkErrorException) {
            // We cannot capture the exceptions in CyfaceAuthenticator as it's part of the SDK.
            // We also don't want to capture the errors in the error handler as we don't have the stacktrace there
            val preferences: SharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context)
            val isReportingEnabled: Boolean = preferences.getBoolean(ACCEPTED_REPORTING_KEY, false)
            if (isReportingEnabled) {
                Sentry.captureException(e)
            }
            // "the authenticator could not honor the request due to a network error"
            return AuthTokenRequestParams(account, false)
        }
        Validate.notNull(authToken)
        Log.d(TAG, "Setting auth token to: **" + authToken.substring(authToken.length - 7))
        val accountManager: AccountManager = AccountManager.get(context)
        accountManager.setAuthToken(account, Constants.AUTH_TOKEN_TYPE, authToken)
        return AuthTokenRequestParams(account, true)
    }

    /**
     * Parameters returned from [.doInBackground] containing information weather the task was successful.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 2.3.0
     */
    class AuthTokenRequestParams internal constructor(account: Account, success: Boolean) {
        private val account: Account
        val isSuccessful: Boolean

        init {
            this.account = account
            isSuccessful = success
        }

        fun getAccount(): Account {
            return account
        }
    }

    companion object {
        /**
         * Returns the Cyface account. Throws a Runtime Exception if no or more than one account exists.
         *
         * @param context the Context to load the AccountManager
         * @return the only existing account
         */
        private fun getAccount(context: Context): Account {
            val accountManager: AccountManager = AccountManager.get(context)
            val existingAccounts: Array<Account> = accountManager.getAccountsByType(ACCOUNT_TYPE)
            Validate.isTrue(existingAccounts.size < 2, "More than one account exists.")
            Validate.isTrue(existingAccounts.size > 0, "No account exists.")
            return existingAccounts[0]
        }
    }
}