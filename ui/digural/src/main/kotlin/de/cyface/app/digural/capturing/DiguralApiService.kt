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

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Interface which describes the Digural API endpoints.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 */
interface DiguralApiService {
    /**
     * The endpoint which accepts trigger events with location data.
     */
    @POST("PanAiCam/Trigger")
    suspend fun trigger(@Body location: Location): Response<Void>

    /**
     * The endpoint which accepts start events with an empty String body -d ''.
     */
    @Suppress("SpellCheckingInspection")
    @POST("start-aufnahme")
    suspend fun start(@Body body: RequestBody = "".toRequestBody("application/json".toMediaType())): Response<Void>

    /**
     * The endpoint which accepts stop events with an empty String body -d ''.
     */
    @Suppress("SpellCheckingInspection")
    @POST("stop-aufnahme")
    suspend fun stop(@Body body: RequestBody = "".toRequestBody("application/json".toMediaType())): Response<Void>
}