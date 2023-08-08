package de.cyface.app.digural.capturing

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

private const val BASE_URL = "http://192.168.178.112:33553/PanAiCam/"

val logging: HttpLoggingInterceptor
    get() {
        val logging = HttpLoggingInterceptor()
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC)
        return HttpLoggingInterceptor()
    }

val httpClient: OkHttpClient.Builder
    get() {
        val ret = OkHttpClient.Builder()
        httpClient.addInterceptor(logging)
        return ret
    }

private val retrofit = Retrofit.Builder()
    .addConverterFactory(GsonConverterFactory.create())
    .baseUrl(BASE_URL)
    .client(httpClient.build())
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