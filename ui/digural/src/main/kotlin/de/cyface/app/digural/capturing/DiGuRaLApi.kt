package de.cyface.app.digural.capturing

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.net.URL

//private const val BASE_URL = "http://192.168.178.112:33553/PanAiCam/"

val retrofit = Retrofit.Builder().baseUrl("http://192.168.113.154:5000/").build()

interface DiguralApiService {
    //@POST("Trigger")
    @GET("swagger/v1/swagger.json")
    suspend fun trigger(): Call<String>//(@Body location: Location)
}

object DiguralApi {
    val tag = "de.cyface.app.digural"
    //val logging = HttpLoggingInterceptor()
    //val httpClient = OkHttpClient.Builder()

    lateinit var baseUrl: URL

    /*private val retrofitBuilder: Retrofit.Builder
        get() {
            //logging.setLevel(HttpLoggingInterceptor.Level.HEADERS)

            //httpClient.addInterceptor(logging)

            return Retrofit.Builder()
                //.addConverterFactory(GsonConverterFactory.create())
                //.client(httpClient.build())
        }*/

    val diguralService: DiguralApiService = retrofit.create(DiguralApiService::class.java)
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
