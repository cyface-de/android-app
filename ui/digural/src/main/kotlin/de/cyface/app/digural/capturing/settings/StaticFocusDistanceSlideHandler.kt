package de.cyface.app.digural.capturing.settings

import android.util.Log
import com.google.android.material.slider.Slider
import de.cyface.app.digural.capturing.settings.SettingsFragment.Companion.TAG
import kotlin.math.roundToInt

/**
 * Handles UI changes of the 'slider' used to adjust the 'focus distance' setting.
 */
class StaticFocusDistanceSlideHandler(
    private val viewModel: SettingsViewModel
) : Slider.OnChangeListener {
    override fun onValueChange(slider: Slider, newValue: Float, fromUser: Boolean) {
        val roundedDistance = (newValue * 100).roundToInt() / 100f
        if (viewModel.staticFocusDistance.value != roundedDistance) {
            viewModel.setStaticFocusDistance(roundedDistance)
        }
    }
}