package de.cyface.app.digural.capturing

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.net.URL

//private const val BASE_URL = "http://192.168.178.112:33553/PanAiCam/"

interface DiguralApiService {
    @POST("Trigger")
    suspend fun trigger(@Body location: Location)
}

object DiguralApi {

    lateinit var baseUrl: URL

    private val retrofit: Retrofit.Builder
        get() {
            val logging = HttpLoggingInterceptor()
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC)

            val httpClient = OkHttpClient.Builder()
            httpClient.addInterceptor(logging)

            return Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create())
                .client(httpClient.build())
        }

    val diguralService: DiguralApiService by lazy {
        retrofit.baseUrl(baseUrl.toURI().resolve("./PanAiCam").toURL())
        retrofit.build().create(DiguralApiService::class.java)
    }
}

data class Location(
        val deviceId: String,
        val measurementId: Long,
        val latitude: Double,
        val longitude: Double,
        val time: Long
    )