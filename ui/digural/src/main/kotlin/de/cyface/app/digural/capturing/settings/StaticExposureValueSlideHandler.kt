package de.cyface.app.digural.capturing.settings

import android.util.Log
import android.widget.TextView
import com.google.android.material.slider.Slider
import de.cyface.app.digural.capturing.settings.SettingsFragment.Companion.EXPOSURE_VALUES
import de.cyface.app.digural.capturing.settings.SettingsFragment.Companion.TAG

/**
 * Handles UI changes of the 'slider' used to adjust the 'exposure value' setting.
 */
class StaticExposureValueSlideHandler(
    private val viewModel: SettingsViewModel,
    private val staticExposureValue: TextView,
    private val staticExposureValueDescription: TextView
) : Slider.OnChangeListener {
    override fun onValueChange(slider: Slider, newValue: Float, fromUser: Boolean) {
        if (viewModel.staticExposureValue.value!!.toFloat() != newValue) {
            Log.d(TAG, "Update preference to exposure value -> $newValue")
            val value = newValue.toInt()
            viewModel.setStaticExposureValue(value)

            // FIXME: do via observe
            val description = EXPOSURE_VALUES[value]
            staticExposureValue.text = value.toString()
            staticExposureValueDescription.text = description
        }
    }
}