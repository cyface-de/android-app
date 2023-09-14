package de.cyface.app.digural.capturing.settings

import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

/**
 * Handles UI changes of the 'slider' used to adjust the 'triggering distance' setting.
 */
class TriggerDistanceSlideHandler(
    private val viewModel: SettingsViewModel
) : Slider.OnChangeListener {
    override fun onValueChange(slider: Slider, newValue: Float, fromUser: Boolean) {
        val roundedDistance = (newValue * 100).roundToInt() / 100f
        if (viewModel.triggeringDistance.value != roundedDistance) {
            viewModel.setTriggeringDistance(roundedDistance)
        }
    }
}