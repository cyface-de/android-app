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
import de.cyface.synchronization.Auth
import de.cyface.uploader.DefaultAuthenticator
import net.openid.appauth.AuthorizationException
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL

/**
 * The API to get the voucher data from.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 3.3.0
 * @property context The authenticator to get the auth token from
 * @property apiEndpoint An API endpoint running a Cyface Incentives API, like `https://some.url/api/v1`
 */
class Incentives(
    private val context: Context,
    private val apiEndpoint: URL,
    private val auth: Auth
) {
    private val client: OkHttpClient = OkHttpClient()

    /**
     * Requests the number of available vouchers.
     *
     * @param handler the handler which receives the response in case of success
     * @param authErrorHandler the handler which receives the auth errors
     */
    fun availableVouchers(
        handler: Callback,
        authErrorHandler: AuthExceptionListener
    ) {
        auth.performActionWithFreshTokens { accessToken, _, ex ->
            if (ex != null) {
                authErrorHandler.onException(ex as AuthorizationException)
                return@performActionWithFreshTokens
            }

            // Try to send the request
            val url = voucherCountEndpoint().toString()
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            client.newCall(request).enqueue(handler)
        }
    }

    /**
     * Requests a voucher for the currently logged in user.
     *
     * @param handler the handler which receives the response in case of success
     * @param authErrorHandler the handler which receives the auth errors
     */
    fun voucher(
        handler: Callback,
        authErrorHandler: AuthExceptionListener
    ) {
        auth.performActionWithFreshTokens { accessToken, _, ex ->
            if (ex != null) {
                authErrorHandler.onException(ex as AuthorizationException)
                return@performActionWithFreshTokens
            }

            // Try to send the request
            val url = voucherEndpoint().toString()
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            client.newCall(request).enqueue(handler)
        }
    }

    @Suppress("MemberVisibilityCanBePrivate") // Part of the API
    private fun voucherCountEndpoint(): URL {
        return URL(DefaultAuthenticator.returnUrlWithTrailingSlash(apiEndpoint.toExternalForm()) + "voucher_count")
    }

    @Suppress("MemberVisibilityCanBePrivate") // Part of the API
    private fun voucherEndpoint(): URL {
        return URL(DefaultAuthenticator.returnUrlWithTrailingSlash(apiEndpoint.toExternalForm()) + "voucher")
    }
}