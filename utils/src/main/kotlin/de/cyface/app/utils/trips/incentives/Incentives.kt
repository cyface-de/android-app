/*
 * Copyright 2023 Cyface GmbH
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
package de.cyface.app.utils.trips.incentives

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AuthenticatorException
import android.accounts.NetworkErrorException
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.android.volley.Response.ErrorListener
import com.android.volley.Response.Listener
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import de.cyface.synchronization.Constants
import de.cyface.synchronization.CyfaceAuthenticator
import de.cyface.synchronization.SyncAdapter
import de.cyface.uploader.DefaultAuthenticator
import de.cyface.uploader.exception.SynchronizationInterruptedException
import de.cyface.utils.Validate
import org.json.JSONObject
import java.net.URL


/**
 * The API to get the voucher data from.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.3.0
 * @property authenticator The authenticator to get the auth token from
 * @property apiEndpoint An API endpoint running a Cyface Incentives API, like `https://some.url/api/v1`
 */
class Incentives(private val authenticator: CyfaceAuthenticator, private val apiEndpoint: String) {

    fun availableVouchers(
        context: Context,
        handler: Listener<JSONObject>,
        failureHandler: ErrorListener
    ) {
        // Acquire new auth token before each request (old one could be expired)
        val jwtAuthToken = getAuthToken(authenticator, getAccount(context))

        // Try to send the request and handle expected errors
        val queue = Volley.newRequestQueue(context)
        val url = voucherCountEndpoint().toString()
        val request = object : JsonObjectRequest(
            Method.GET,
            url,
            null,
            handler,
            failureHandler
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return headers(jwtAuthToken)
            }
        }
        queue.add(request)
    }

    fun voucher(
        context: Context,
        handler: Listener<JSONObject>,
        failureHandler: ErrorListener
    ) {
        // Acquire new auth token before each request (old one could be expired)
        val jwtAuthToken = getAuthToken(authenticator, getAccount(context))

        // Try to send the request and handle expected errors
        val queue = Volley.newRequestQueue(context)
        val url = voucherEndpoint().toString()
        val request = object : JsonObjectRequest(
            Method.GET,
            url,
            null,
            handler,
            failureHandler
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return headers(jwtAuthToken)
            }
        }
        queue.add(request)
    }

    private fun headers(jwtAuthToken: String): MutableMap<String, String> {
        val headers: MutableMap<String, String> = HashMap()
        headers["Authorization"] = "Bearer $jwtAuthToken"
        return headers
    }

    /**
     * Returns the Cyface account. Throws a Runtime Exception if no or more than one account exists.
     *
     * @param context the Context to load the AccountManager
     * @return the only existing account
     */
    private fun getAccount(context: Context): Account {
        val accountType = "de.cyface.app.r4r"
        val accountManager = AccountManager.get(context)
        val existingAccounts = accountManager.getAccountsByType(accountType)
        Validate.isTrue(existingAccounts.size < 2, "More than one account exists.")
        Validate.isTrue(existingAccounts.isNotEmpty(), "No account exists.")
        return existingAccounts[0]
    }

    /**
     * Gets the authentication token from the [CyfaceAuthenticator].
     *
     * @param authenticator The `CyfaceAuthenticator` to be used
     * @param account The `Account` to get the token for
     * @return The token as string
     * @throws AuthenticatorException If no token was supplied which must be supported for implementing apps (SR)
     * @throws NetworkErrorException If the network authentication request failed for any reasons
     * @throws SynchronizationInterruptedException If the synchronization was [Thread.interrupted].
     */
    @Throws(
        AuthenticatorException::class,
        NetworkErrorException::class,
        SynchronizationInterruptedException::class
    )
    private fun getAuthToken(authenticator: CyfaceAuthenticator, account: Account): String {
        val jwtAuthToken: String?
        // Explicitly calling CyfaceAuthenticator.getAuthToken(), see its documentation
        val bundle: Bundle? = try {
            authenticator.getAuthToken(null, account, Constants.AUTH_TOKEN_TYPE, null)
        } catch (e: NetworkErrorException) {
            // This happened e.g. when Wifi was manually disabled just after synchronization started (Pixel 2 XL).
            Log.w(SyncAdapter.TAG, "getAuthToken failed, was the connection closed? Aborting sync.")
            throw e
        }
        if (bundle == null) {
            // Because of Movebis we don't throw an IllegalStateException if there is no auth token
            throw AuthenticatorException("No valid auth token supplied. Aborting data synchronization!")
        }
        jwtAuthToken = bundle.getString(AccountManager.KEY_AUTHTOKEN)
        // When WifiSurveyor.deleteAccount() was called in the meantime the jwt token is empty, thus:
        if (jwtAuthToken == null) {
            Validate.isTrue(Thread.interrupted())
            throw SynchronizationInterruptedException("Sync interrupted, aborting sync.")
        }
        Log.d(
            SyncAdapter.TAG,
            "Login authToken: **" + jwtAuthToken.substring(jwtAuthToken.length - 7)
        )
        return jwtAuthToken
    }

    @Suppress("MemberVisibilityCanBePrivate") // Part of the API
    private fun voucherCountEndpoint(): URL {
        return URL(DefaultAuthenticator.returnUrlWithTrailingSlash(apiEndpoint) + "voucher_count")
    }

    @Suppress("MemberVisibilityCanBePrivate") // Part of the API
    private fun voucherEndpoint(): URL {
        return URL(DefaultAuthenticator.returnUrlWithTrailingSlash(apiEndpoint) + "voucher")
    }
}