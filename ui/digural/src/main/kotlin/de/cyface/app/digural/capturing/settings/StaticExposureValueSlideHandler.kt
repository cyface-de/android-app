package de.cyface.app.digural.capturing.settings

import com.google.android.material.slider.Slider

/**
 * Handles UI changes of the 'slider' used to adjust the 'exposure value' setting.
 */
class StaticExposureValueSlideHandler(
    private val viewModel: SettingsViewModel
) : Slider.OnChangeListener {
    override fun onValueChange(slider: Slider, newValue: Float, fromUser: Boolean) {
        if (viewModel.staticExposureValue.value!!.toFloat() != newValue) {
            val value = newValue.toInt()
            viewModel.setStaticExposureValue(value)
        }
    }
}