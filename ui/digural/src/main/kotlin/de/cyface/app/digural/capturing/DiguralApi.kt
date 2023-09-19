package de.cyface.app.digural.capturing

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.net.URL
import java.util.concurrent.TimeUnit

//private const val BASE_URL = "http://192.168.178.112:33553/PanAiCam/"

interface DiguralApiService {
    @POST("PanAiCam/Trigger")
    //@GET("swagger/v1/swagger.json")
    suspend fun trigger(@Body location: Location): Response<Void> //Call<String>//(@Body location: Location)
}

object DiguralApi {

    lateinit var baseUrl: URL

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
            .baseUrl(baseUrl.toString()/*.toURI().resolve("PanAiCam").toURL()*/)
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient)
            .build()
    }

    val diguralService: DiguralApiService by lazy {
        //retrofit.baseUrl(baseUrl.toURI().resolve("./PanAiCam").toURL())
        retrofit.create(DiguralApiService::class.java)
    }
}

data class Location(
        val deviceId: String,
        val measurementId: Long,
        val latitude: Double,
        val longitude: Double,
        val time: Long
    ) {
    fun toJson(): String {
        return "{\"DeviceId\":\"$deviceId\",\"MeasurementId\":$measurementId,\"Latitude\":$latitude,\"Longitude\":$longitude,\"Time\":$time}"
    }
}
