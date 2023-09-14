package de.cyface.app.digural.capturing.settings

import com.google.android.material.slider.Slider

/**
 * Handles UI changes of the 'slider' used to adjust the 'exposure time' setting.
 */
class StaticExposureTimeSlideHandler(
    private val viewModel: SettingsViewModel
) :
    Slider.OnChangeListener {
    override fun onValueChange(slider: Slider, newValue: Float, fromUser: Boolean) {
        if (viewModel.staticExposureTime.value!!.toFloat() != newValue) {
            val value = newValue.toLong()
            viewModel.setStaticExposureTime(value)
        }
    }
}