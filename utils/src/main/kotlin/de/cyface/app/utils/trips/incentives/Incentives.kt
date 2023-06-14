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

import android.content.Context
import android.os.Handler
import com.android.volley.Response.ErrorListener
import com.android.volley.Response.Listener
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import de.cyface.synchronization.AuthStateManager
import de.cyface.synchronization.Configuration
import de.cyface.uploader.DefaultAuthenticator
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import org.json.JSONObject
import java.lang.Exception
import java.net.URL
import java.util.function.Consumer

/**
 * The API to get the voucher data from.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.3.0
 * @property context The authenticator to get the auth token from
 * @property apiEndpoint An API endpoint running a Cyface Incentives API, like `https://some.url/api/v1`
 */
class Incentives(private val context: Context, private val apiEndpoint: String) {

    /**
     * The service used for authorization.
     */
    private var mAuthService: AuthorizationService? = null

    /**
     * The authorization state.
     */
    private var mStateManager: AuthStateManager

    /**
     * The configuration of the OAuth 2 endpoint to authorize against.
     */
    private var mConfiguration: Configuration

    init {
        // Authorization
        mStateManager = AuthStateManager.getInstance(context)
        //mExecutor = Executors.newSingleThreadExecutor()
        mConfiguration = Configuration.getInstance(context)
        val config = Configuration.getInstance(context)
        if (config.hasConfigurationChanged()) {
            throw IllegalStateException("Authentifizierung ist abgelaufen")
            //Handler().postDelayed({ signOut(false) }, 2000)
            //return
        }
        mAuthService = AuthorizationService(
            context,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(config.connectionBuilder)
                .build()
        )
    }

    /**
     * Requests the number of available vouchers.
     *
     * @param context the context required to load the account manager and to create cache dirs
     * @param handler the handler which receives the response in case of success
     * @param failureHandler the handler which receives the errors
     */
    fun availableVouchers(
        context: Context,
        handler: Listener<JSONObject>,
        failureHandler: ErrorListener,
        authErrorHandler: Listener<AuthorizationException>
    ) {
        mStateManager.current.performActionWithFreshTokens(
            mAuthService!!
        ) { accessToken: String?, idToken: String?, ex: AuthorizationException? ->
            if (ex != null) {
                authErrorHandler.onResponse(ex)
                return@performActionWithFreshTokens
            }

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
                    return headers(accessToken!!)
                }
            }
            queue.add(request)
        }
    }

    /**
     * Requests a voucher for the currently logged in user.
     *
     * @param context the context required to load the account manager and to create cache dirs
     * @param handler the handler which receives the response in case of success
     * @param failureHandler the handler which receives the errors
     */
    fun voucher(
        context: Context,
        handler: Listener<JSONObject>,
        failureHandler: ErrorListener,
        authErrorHandler: Listener<AuthorizationException>
    ) {
        mStateManager.current.performActionWithFreshTokens(
            mAuthService!!
        ) { accessToken: String?, idToken: String?, ex: AuthorizationException? ->
            if (ex != null) {
                authErrorHandler.onResponse(ex)
                return@performActionWithFreshTokens
            }

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
                    return headers(accessToken!!)
                }
            }
            queue.add(request)
        }
    }

    private fun headers(jwtAuthToken: String): MutableMap<String, String> {
        val headers: MutableMap<String, String> = HashMap()
        headers["Authorization"] = "Bearer $jwtAuthToken"
        return headers
    }

    @Suppress("MemberVisibilityCanBePrivate") // Part of the API
    private fun voucherCountEndpoint(): URL {
        return URL(DefaultAuthenticator.returnUrlWithTrailingSlash(apiEndpoint) + "voucher_count")
    }

    @Suppress("MemberVisibilityCanBePrivate") // Part of the API
    private fun voucherEndpoint(): URL {
        return URL(DefaultAuthenticator.returnUrlWithTrailingSlash(apiEndpoint) + "voucher")
    }

    companion object {
        /**
         * The settings key used to identify the settings storing the URL of the server to get incentives from.
         */
        const val INCENTIVES_ENDPOINT_URL_SETTINGS_KEY = "de.cyface.incentives.endpoint"
    }
}