package de.cyface.app.digural.capturing.settings

import android.content.Context
import android.util.Log
import android.widget.CompoundButton
import android.widget.Toast
import de.cyface.app.digural.capturing.settings.SettingsFragment.Companion.TAG

/**
 * Handles UI changes of the 'switcher' used to en-/disable 'static exposure time' feature.
 */
class StaticExposureSwitchHandler(
    private val viewModel: SettingsViewModel,
    private val context: Context
) : CompoundButton.OnCheckedChangeListener {
    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (viewModel.staticExposure.value == isChecked) {
            return
        }

        // Manual sensor not supported
        if (!viewModel.manualSensorSupported && isChecked) {
            buttonView.isChecked = false
            Toast.makeText(
                context,
                "This device does not support manual exposure control",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        viewModel.setStaticExposure(isChecked)

        if (isChecked) {
            Toast.makeText(
                context,
                de.cyface.camera_service.R.string.experimental_feature_warning,
                Toast.LENGTH_LONG
            ).show()
        }
    }
}