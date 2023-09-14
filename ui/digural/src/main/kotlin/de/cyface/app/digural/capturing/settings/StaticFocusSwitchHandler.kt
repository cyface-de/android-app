package de.cyface.app.digural.capturing.settings

import android.content.Context
import android.widget.CompoundButton
import android.widget.Toast

/**
 * Handles UI changes of the 'switcher' used to en-/disable 'static focus' feature.
 */
class StaticFocusSwitchHandler(
    private val context: Context,
    private val viewModel: SettingsViewModel
) : CompoundButton.OnCheckedChangeListener {
    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (viewModel.staticFocus.value == isChecked) {
            return
        }

        // Static focus enabled: Manual Sensor not supported
        if (!viewModel.manualSensorSupported && isChecked) {
            buttonView.isChecked = false
            Toast.makeText(
                context,
                "This device does not support manual focus control",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        viewModel.setStaticFocus(isChecked)

        if (isChecked) {
            Toast.makeText(
                context,
                de.cyface.camera_service.R.string.experimental_feature_warning,
                Toast.LENGTH_LONG
            ).show()
        }
    }
}