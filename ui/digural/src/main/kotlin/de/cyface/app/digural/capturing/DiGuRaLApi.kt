package de.cyface.app.digural.capturing

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

private const val BASE_URL = "http://localhost:5000/PanAiCam/"
private val retrofit = Retrofit.Builder()
    .addConverterFactory(GsonConverterFactory.create())
    .baseUrl(BASE_URL)
    .build()

interface DiguralApiService {
    @POST("Trigger")
    suspend fun trigger(@Body location: Location)
}

object DiguralApi {
    val diguralService: DiguralApiService by lazy {
        retrofit.create(DiguralApiService::class.java)
    }
}

data class Location(
    val deviceId: String,
    val measurementId: Long,
    val latitude: Double,
    val longitude: Double,
    val time: Long
    )