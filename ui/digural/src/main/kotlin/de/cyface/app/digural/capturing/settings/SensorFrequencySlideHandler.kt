package de.cyface.app.digural.capturing.settings

import android.util.Log
import android.widget.TextView
import com.google.android.material.slider.Slider
import de.cyface.app.digural.capturing.settings.SettingsFragment.Companion.TAG

/**
 * Handles UI changes of the 'slider' used to adjust the 'sensor frequency' setting.
 */
class SensorFrequencySlideHandler(
    private val viewModel: SettingsViewModel,
    private val sensorFrequency: TextView
) : Slider.OnChangeListener {
    override fun onValueChange(slider: Slider, newValue: Float, fromUser: Boolean) {
        val newSensorFrequency = newValue.toInt()
        if (viewModel.sensorFrequency.value != newSensorFrequency) {
            Log.d(TAG, "Update preference to sensor frequency -> $newValue")
            viewModel.setSensorFrequency(newSensorFrequency)

            // FIXME: do via observe
            sensorFrequency.text = StringBuilder(newSensorFrequency)
        }
    }
}