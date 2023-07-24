package de.cyface.app.digural.capturing.settings

import android.util.Log
import android.widget.TextView
import com.google.android.material.slider.Slider
import de.cyface.app.digural.capturing.settings.SettingsFragment.Companion.TAG
import kotlin.math.roundToInt

/**
 * Handles UI changes of the 'slider' used to adjust the 'triggering distance' setting.
 */
class TriggerDistanceSlideHandler(
    private val viewModel: SettingsViewModel,
    private val distanceBased: TextView
) : Slider.OnChangeListener {
    override fun onValueChange(slider: Slider, newValue: Float, fromUser: Boolean) {
        val roundedDistance = (newValue * 100).roundToInt() / 100f
        if (viewModel.triggeringDistance.value != roundedDistance) {
            Log.d(TAG, "Update preference to triggering distance -> $roundedDistance")
            viewModel.setTriggeringDistance(roundedDistance)

            // FIXME: do via observe
            val text = StringBuilder(roundedDistance.toString())
            while (text.length < 4) {
                text.append("0")
            }
            distanceBased.text = text
        }
    }
}