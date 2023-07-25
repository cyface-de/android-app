package de.cyface.app.digural.capturing.settings

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.slider.Slider
import de.cyface.app.digural.R
import de.cyface.app.digural.capturing.settings.SettingsFragment.Companion.TAG

/**
 * Handles UI changes of the 'switcher' used to en-/disable 'distance based triggering' feature.
 */
class DistanceBasedSwitchHandler(
    private val context: Context,
    private val viewModel: SettingsViewModel,
    private val distanceBasedSlider: Slider,
    private val distanceBased: TextView,
    private val distanceBasedUnit: TextView
) : CompoundButton.OnCheckedChangeListener {
    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (viewModel.distanceBasedTriggering.value != isChecked) {
            Log.d(TAG, "Update preference to distance-based-trigger -> $isChecked")
            viewModel.setDistanceBasedTriggering(isChecked)
            if (isChecked) {
                Toast.makeText(context, R.string.experimental_feature_warning, Toast.LENGTH_LONG)
                    .show()
            }

            // Update visibility of slider - FIXME: do via observe
            val expectedVisibility = if (isChecked) View.VISIBLE else View.INVISIBLE
            val sliderVisibilityOutOfSync = distanceBasedSlider.visibility != expectedVisibility
            val preferenceVisibilityOutOfSync = distanceBased.visibility != expectedVisibility
            val unitVisibilityOutOfSync = distanceBasedUnit.visibility != expectedVisibility
            if (sliderVisibilityOutOfSync || preferenceVisibilityOutOfSync || unitVisibilityOutOfSync) {
                Log.d(
                    TAG,
                    "updateView -> " + if (expectedVisibility == View.VISIBLE) "visible" else "invisible"
                )
                distanceBasedSlider.visibility = expectedVisibility
                distanceBased.visibility = expectedVisibility
                distanceBasedUnit.visibility = expectedVisibility
            }
        }
    }
}