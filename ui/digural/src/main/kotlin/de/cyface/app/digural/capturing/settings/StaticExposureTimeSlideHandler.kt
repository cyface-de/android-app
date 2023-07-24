package de.cyface.app.digural.capturing.settings

import android.util.Log
import android.widget.TextView
import com.google.android.material.slider.Slider
import de.cyface.app.digural.capturing.settings.SettingsFragment.Companion.TAG
import de.cyface.camera_service.Utils

/*
 * Handles UI changes of the 'slider' used to adjust the 'exposure time' setting.
 */
class StaticExposureTimeSlideHandler(
    private val viewModel: SettingsViewModel,
    private val staticExposureValue: TextView
) :
    Slider.OnChangeListener {
    override fun onValueChange(slider: Slider, newValue: Float, fromUser: Boolean) {
        if (viewModel.staticExposureTime.value!!.toFloat() != newValue) {
            Log.d(TAG, "Update preference to exposure time -> $newValue ns")
            val value = newValue.toLong()
            viewModel.setStaticExposureTime(value)

            // FIXME: do via observe
            staticExposureValue.text = Utils.getExposureTimeFraction(value)
        }
    }
}