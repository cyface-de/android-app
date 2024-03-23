/*
 * Copyright 2024 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.app.digural.auth

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.accounts.NetworkErrorException
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import de.cyface.synchronization.LoginActivityProvider
import de.cyface.synchronization.settings.DefaultSynchronizationSettings

/**
 * The WebdavAuthenticator is called by the [AccountManager] to fulfill all account relevant
 * tasks such as getting stored auth-tokens, opening the login activity and handling user authentication
 * against the Webdav server.
 *
 * **ATTENTION:** Usually the tokens are handled by the system, but the Webdav library we use
 * handles them internally. Thus, we don't implement any token handling here.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.8.0
 */
class WebdavAuthenticator(private val context: Context) :
    AbstractAccountAuthenticator(context), LoginActivityProvider {

    override fun editProperties(
        response: AccountAuthenticatorResponse,
        accountType: String
    ): Bundle? {
        return null
    }

    override fun addAccount(
        response: AccountAuthenticatorResponse, accountType: String,
        authTokenType: String, requiredFeatures: Array<String>?, options: Bundle
    ): Bundle {
        Log.d(TAG, "WebdavAuthenticator.addAccount: start LoginActivity to authenticate")
        val intent = Intent(context, getLoginActivity())
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
        val bundle = Bundle()
        bundle.putParcelable(AccountManager.KEY_INTENT, intent)
        return bundle
    }

    override fun confirmCredentials(
        response: AccountAuthenticatorResponse,
        account: Account,
        options: Bundle
    ): Bundle? {
        return null
    }

    /**
     * The `#getAuthToken(AccountAuthenticatorResponse, Account, String, Bundle)` method is only
     * called by the system if no token is cached.
     *
     * But as we're calling `StateManager.current.performActionWithFreshTokens` the token should
     * always be automatically refreshed, so this method is probably not being called at all.
     *
     * Besides that, the Webdav implementation does not handle token explicitly.
     */
    @Throws(NetworkErrorException::class)
    override fun getAuthToken(
        response: AccountAuthenticatorResponse?, account: Account,
        authTokenType: String, options: Bundle?
    ): Bundle {
        // Return a bundle containing a dummy token
        val result = Bundle()
        result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name)
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type)
        result.putString(AccountManager.KEY_AUTHTOKEN, DUMMY_TOKEN)
        Log.v(TAG, "getAuthToken: Token refresh requested (async)")
        return result
    }

    override fun getAuthTokenLabel(authTokenType: String): String {
        return "JWT Token"
    }

    override fun updateCredentials(
        response: AccountAuthenticatorResponse, account: Account, authTokenType: String,
        options: Bundle
    ): Bundle? {
        return null
    }

    override fun hasFeatures(
        response: AccountAuthenticatorResponse,
        account: Account,
        features: Array<String>
    ): Bundle? {
        return null
    }

    companion object {
        private const val TAG = "de.cyface.auth"
        const val DUMMY_TOKEN = "WEBDAV_TOKEN_HANDLED_BY_LIBRARY"

        /**
         * A reference to the implementation of the `AccountAuthenticatorActivity` which is called by Android and its
         * [AccountManager]. This happens e.g. when a token is requested while none is cached, using
         * [.getAuthToken].
         */
        @JvmField
        var LOGIN_ACTIVITY: Class<out Activity?>? = null

        /**
         * Custom settings used by this library.
         */
        lateinit var settings: DefaultSynchronizationSettings
    }

    override fun getLoginActivity(): Class<out Activity> {
        return LOGIN_ACTIVITY!!
    }
}