package de.cyface.app.digural.capturing.settings

import android.content.Context
import android.util.Log
import android.widget.CompoundButton
import android.widget.Toast
import de.cyface.app.digural.capturing.settings.SettingsFragment.Companion.TAG

/**
 * Handles UI changes of the 'switcher' used to en-/disable 'distance based triggering' feature.
 */
class DistanceBasedSwitchHandler(
    private val context: Context,
    private val viewModel: SettingsViewModel
) : CompoundButton.OnCheckedChangeListener {
    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (viewModel.distanceBasedTriggering.value == isChecked) {
            return
        }

        viewModel.setDistanceBasedTriggering(isChecked)

        if (isChecked) {
            Toast.makeText(
                context,
                de.cyface.camera_service.R.string.experimental_feature_warning,
                Toast.LENGTH_LONG
            ).show()
        }
    }
}