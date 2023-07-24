package de.cyface.app.digural.capturing.settings

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.slider.Slider
import de.cyface.app.digural.R
import de.cyface.app.digural.capturing.settings.SettingsFragment.Companion.TAG

/**
 * Handles UI changes of the 'switcher' used to en-/disable 'static focus' feature.
 */
class StaticFocusSwitchHandler(
    private val context: Context,
    private val viewModel: SettingsViewModel,
    private val staticFocusSwitcher: SwitchCompat,
    private val staticFocusDistanceSlider: Slider,
    private val staticFocus: TextView,
    private val staticFocusUnit: TextView
) : CompoundButton.OnCheckedChangeListener {
    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (!viewModel.manualSensorSupported && isChecked) {
            Toast.makeText(
                context,
                "This device does not support manual focus control",
                Toast.LENGTH_LONG
            ).show()
            staticFocusSwitcher.isChecked = false
            return
        }

        if (viewModel.staticFocus.value != isChecked) {
            Log.d(TAG, "Update preference to focus -> " + if (isChecked) "manual" else "auto")
            viewModel.setStaticFocus(isChecked)
            if (isChecked) {
                Toast.makeText(context, R.string.experimental_feature_warning, Toast.LENGTH_LONG)
                    .show()
            }

            // Update visibility of slider - FIXME: do via observe
            val expectedVisibility = if (isChecked) View.VISIBLE else View.INVISIBLE
            val sliderVisibilityOutOfSync = staticFocusDistanceSlider.visibility != expectedVisibility
            val preferenceVisibilityOutOfSync = staticFocus.visibility != expectedVisibility
            val unitVisibilityOutOfSync = staticFocusUnit.visibility != expectedVisibility
            if (sliderVisibilityOutOfSync || preferenceVisibilityOutOfSync || unitVisibilityOutOfSync) {
                Log.d(
                    TAG,
                    "updateView -> " + if (expectedVisibility == View.VISIBLE) "visible" else "invisible"
                )
                staticFocusDistanceSlider.visibility = expectedVisibility
                staticFocus.visibility = expectedVisibility
                staticFocusUnit.visibility = expectedVisibility
            }
        }
    }
}