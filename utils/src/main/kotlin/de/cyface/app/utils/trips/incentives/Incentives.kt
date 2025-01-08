/*
 * Copyright 2023-2025 Cyface GmbH
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

import android.util.Log
import de.cyface.app.utils.SharedConstants.TAG
import de.cyface.synchronization.Auth
import de.cyface.uploader.DefaultUploader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import java.net.URL
import kotlin.coroutines.resumeWithException

/**
 * The API to get the voucher data from.
 *
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 3.3.0
 * @property apiEndpoint An API endpoint running a Cyface Incentives API, like `https://some.url/api/v1`
 * @property auth The authenticator to get the auth token from
 */
class Incentives(
    private val apiEndpoint: URL,
    private val auth: Auth
) {
    private val client: OkHttpClient = OkHttpClient()

    /**
     * Requests the number of available vouchers.
     *
     * @return The [Response] from the voucher count request.
     * @throws AuthorizationException if there is an error refreshing the access token.
     * @throws IOException if there is a network error.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun availableVouchers(): Response {
        return suspendCancellableCoroutine { continuation ->
            auth.performActionWithFreshTokens { accessToken, _, ex ->
                if (ex != null) {
                    continuation.resumeWithException(ex as AuthorizationException)
                    return@performActionWithFreshTokens
                }

                // Try to send the request
                val url = voucherCountEndpoint().toString()
                Log.d(TAG, "Voucher count request to $url")
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response) {
                            response.close() // close to release resources
                        }
                    }
                })
            }
        }
    }

    /**
     * Requests a voucher for the currently logged in user.
     *
     * @return The [Response] from the voucher request.
     * @throws AuthorizationException if there is an error refreshing the access token.
     * @throws IOException if there is a network error.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun voucher(): Response {
        return suspendCancellableCoroutine { continuation ->
            auth.performActionWithFreshTokens { accessToken, _, ex ->
                if (ex != null) {
                    continuation.resumeWithException(ex as AuthorizationException)
                    return@performActionWithFreshTokens
                }

                // Try to send the request
                val url = voucherEndpoint().toString()
                Log.d(TAG, "Voucher request to $url")
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (!response.isSuccessful) {
                            continuation.resumeWithException(
                                IOException("Unexpected HTTP response: ${response.code}")
                            )
                        } else {
                            continuation.resume(response) {
                                response.close() // close to release resources
                            }
                        }
                    }
                })
            }
        }
    }

    @Suppress("MemberVisibilityCanBePrivate") // Part of the API
    private fun voucherCountEndpoint(): URL {
        return URL(DefaultUploader.returnUrlWithTrailingSlash(apiEndpoint.toExternalForm()) + "voucher_count")
    }

    @Suppress("MemberVisibilityCanBePrivate") // Part of the API
    private fun voucherEndpoint(): URL {
        return URL(DefaultUploader.returnUrlWithTrailingSlash(apiEndpoint.toExternalForm()) + "voucher")
    }
}