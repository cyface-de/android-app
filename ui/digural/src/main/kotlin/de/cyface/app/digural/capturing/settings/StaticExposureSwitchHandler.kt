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
 * Handles UI changes of the 'switcher' used to en-/disable 'static exposure time' feature.
 */
class StaticExposureSwitchHandler(
    private val viewModel: SettingsViewModel,
    private val context: Context,
    private val staticExposureTimeSwitcher: SwitchCompat,
    private val staticExposureTime: TextView,
    private val staticExposureTimeUnit: TextView,
    private val staticExposureValueTitle: TextView,
    private val staticExposureValueSlider: Slider,
    private val staticExposureValue: TextView,
    private val staticExposureValueDescription: TextView
) : CompoundButton.OnCheckedChangeListener {
    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (!viewModel.manualSensorSupported && isChecked) {
            Toast.makeText(
                context,
                "This device does not support manual exposure control",
                Toast.LENGTH_LONG
            ).show()
            staticExposureTimeSwitcher.isChecked = false
            return
        }

        if (viewModel.staticExposure.value != isChecked) {
            Log.d(
                TAG,
                "Update preference to exposure -> " + if (isChecked) "Tv-/S-Mode" else "auto"
            )
            viewModel.setStaticExposure(isChecked)
            if (isChecked) {
                Toast.makeText(context, R.string.experimental_feature_warning, Toast.LENGTH_LONG)
                    .show()
            }

            // Update visibility of slider - FIXME: do via observe
            val expectedVisibility = if (isChecked) View.VISIBLE else View.INVISIBLE
            // final boolean sliderVisibilityOutOfSync = staticExposureTimeSlider.getVisibility() != expectedVisibility;
            val preferenceVisibilityOutOfSync = staticExposureTime.visibility != expectedVisibility
            val unitVisibilityOutOfSync = staticExposureTimeUnit.visibility != expectedVisibility
            val evTitleVisibilityOutOfSync =
                staticExposureValueTitle.visibility != expectedVisibility
            val evSliderVisibilityOutOfSync =
                staticExposureValueSlider.visibility != expectedVisibility
            val evPreferenceVisibilityOutOfSync =
                staticExposureValue.visibility != expectedVisibility
            val evDescriptionVisibilityOutOfSync =
                staticExposureValueDescription.visibility != expectedVisibility
            if ( /* sliderVisibilityOutOfSync || */preferenceVisibilityOutOfSync || unitVisibilityOutOfSync
                || evTitleVisibilityOutOfSync || evSliderVisibilityOutOfSync || evPreferenceVisibilityOutOfSync
                || evDescriptionVisibilityOutOfSync
            ) {
                Log.d(
                    TAG,
                    "updateView -> " + if (expectedVisibility == View.VISIBLE) "visible" else "invisible"
                )
                // staticExposureTimeSlider.setVisibility(expectedVisibility);
                staticExposureTime.visibility = expectedVisibility
                staticExposureTimeUnit.visibility = expectedVisibility
                staticExposureValueTitle.visibility = expectedVisibility
                staticExposureValueSlider.visibility = expectedVisibility
                staticExposureValue.visibility = expectedVisibility
                staticExposureValueDescription.visibility = expectedVisibility
            }
        }
    }
}