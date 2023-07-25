package de.cyface.app.digural.capturing.settings

import android.content.Context
import androidx.core.content.edit
import java.net.MalformedURLException
import java.net.URL

private const val DIGURAL_SERVER_ADDRESS = "de.cyface.digural.server"
/**
 * This class is responsible for storing and retrieving preferences specific to the DiGuRaL project.
 */
class DiGuRaLPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("DiGuRaL", Context.MODE_PRIVATE)

    fun saveDiGuRaLApiAddress(address: URL) {
        preferences.edit {
            putString(DIGURAL_SERVER_ADDRESS, address.toExternalForm())
            apply()
        }
    }

    fun getDiGuRaLApiAddress(): URL {
        try {
            val addressString = preferences.getString(
                DIGURAL_SERVER_ADDRESS,
                "http://localhost:33553/PanAiCam/"
            )
            return URL(addressString)
            // TODO: Add proper Error handling here before merging.
        } catch (e: MalformedURLException) {
            return URL("http://localhost:33553/PanAiCam/")
        }
    }
}