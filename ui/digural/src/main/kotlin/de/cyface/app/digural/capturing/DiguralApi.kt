/*
 * Copyright 2023-2024 Cyface GmbH
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
package de.cyface.app.digural.capturing

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import de.cyface.app.digural.MainActivity.Companion.TAG
import de.cyface.app.digural.capturing.DiguralApi.baseUrl
import de.cyface.app.digural.capturing.DiguralApi.diguralService
import de.cyface.app.digural.capturing.DiguralApi.retrofit
import de.cyface.synchronization.NetworkCallback
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * A singleton object responsible for creating and managing the API client for Digural.
 *
 * This object initializes Retrofit with specified settings such as connection timeouts and logging configurations.
 * The baseUrl for the API must be set externally before accessing the Retrofit instance.
 *
 * The object also exposes `diguralService`, a lazy-loaded service interface to be used for making API calls.
 *
 * Example:
 * ```
 * DiguralApi.baseUrl = URL("https://api.digural.com")
 * DiguralApi.setToUseWifi(context)
 * val service = DiguralApi.diguralService
 * // call endpoints, then:
 * DiguralApi.shutdown(context)
 * ```
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @property baseUrl The base URL for the API endpoints.
 * @property retrofit The Retrofit client, initialized with specified settings.
 * @property diguralService A service interface for making API calls.
 */
object DiguralApi {

    lateinit var baseUrl: URL
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    /**
     * Ensures the API calls use the WiFi network (as we're not expecting this API to be available
     * via the mobile data network, which leads to errors when enabled [LEIP-155].
     *
     * *Attention*: Don't forget to call [shutdown] to unregister from network changes.
     *
     * @param context The context to get the [ConnectivityManager] from.
     */
    fun setToUseWifi(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "DiguralApi: set to use newly connected wifi $network")
                ConnectivityManager.setProcessDefaultNetwork(network)
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "DiguralApi: wifi connection lost")
                ConnectivityManager.setProcessDefaultNetwork(null)
            }
        }
        connectivityManager.requestNetwork(networkRequest, networkCallback)
    }

    /**
     * Needs to be called to cleanup resources, e.g. listening to network changes.
     *
     * @param context The context to get the [ConnectivityManager] from.
     */
    fun shutdown(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    private val retrofit: Retrofit by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val httpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(baseUrl.toString())
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient)
            .build()
    }

    val diguralService: DiguralApiService by lazy {
        retrofit.create(DiguralApiService::class.java)
    }
}
