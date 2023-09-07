package de.cyface.app.digural.capturing.settings

import android.util.Log
import android.view.View
import androidx.fragment.app.FragmentManager
import de.cyface.app.digural.capturing.settings.SettingsFragment.Companion.DIALOG_EXPOSURE_TIME_SELECTION_REQUEST_CODE
import de.cyface.app.digural.capturing.settings.SettingsFragment.Companion.TAG
import de.cyface.app.digural.dialog.ExposureTimeDialog
import de.cyface.utils.Validate

/**
 * Handles UI clicks on the exposure time used to adjust the 'exposure time' setting.
 */
class StaticExposureTimeClickHandler(
    private val fragmentManager: FragmentManager?,
    private val settingsFragment: SettingsFragment
) : View.OnClickListener {
    override fun onClick(v: View) {
        Log.d(TAG, "StaticExposureTimeClickHandler triggered, showing ExposureTimeDialog")
        Validate.notNull(fragmentManager)
        val dialog = ExposureTimeDialog(settingsFragment.cameraSettings)
        dialog.setTargetFragment(
            settingsFragment,
            DIALOG_EXPOSURE_TIME_SELECTION_REQUEST_CODE
        )
        dialog.isCancelable = true
        dialog.show(fragmentManager!!, "EXPOSURE_TIME_DIALOG")
    }
}