/*
 * Copyright 2023-2025 Cyface GmbH
 *
 * This file is part of the Cyface App for Android.
 *
 * The Cyface App for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface App for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface App for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.app.digural.capturing

import android.content.Intent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import de.cyface.app.digural.R
import de.cyface.app.digural.MainActivity
import de.cyface.app.digural.utils.Constants.SUPPORT_EMAIL
import de.cyface.energy_settings.TrackingSettings

/**
 * The [androidx.core.view.MenuProvider] for the [de.cyface.app.CapturingFragment] which defines which
 * options are shown in the action bar at the top right.
 *
 * @author Armin Schnabel
 * @version 2.0.2
 * @since 3.2.0
 */
class MenuProvider(
    private val activity: MainActivity,
    private val navController: NavController
) : androidx.core.view.MenuProvider {

    /**
     * The `Intent` used when the user wants to send feedback.
     */
    private lateinit var emailIntent: Intent

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.capturing, menu)

        // Setting up feedback email template
        emailIntent = TrackingSettings.generateFeedbackEmailIntent(
            activity,
            activity.getString(de.cyface.energy_settings.R.string.feedback_error_description),
            SUPPORT_EMAIL
        )
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.guide_item -> {
                if (!TrackingSettings.showGnssWarningDialog(activity) &&
                    !TrackingSettings.showEnergySaferWarningDialog(activity) &&
                    !TrackingSettings.showRestrictedBackgroundProcessingWarningDialog(activity) &&
                    !TrackingSettings.showProblematicManufacturerDialog(
                        activity,
                        true,
                        SUPPORT_EMAIL,
                        activity.lifecycleScope
                    )
                ) {
                    TrackingSettings.showNoGuidanceNeededDialog(activity, SUPPORT_EMAIL)
                }
                true
            }
            R.id.feedback_item -> {
                activity.startActivity(
                    Intent.createChooser(
                        emailIntent,
                        activity.getString(de.cyface.energy_settings.R.string.feedback_choose_email_app)
                    )
                )
                true
            }
            R.id.imprint_item -> {
                val action = CapturingFragmentDirections.actionCapturingToImprint()
                navController.navigate(action)
                true
            }
            R.id.settings_item -> {
                val action = CapturingFragmentDirections.actionCapturingToSettings()
                navController.navigate(action)
                true
            }
            /*R.id.logout_item -> {
                try {
                    activity.capturing.removeAccount(activity.capturing.wiFiSurveyor.account.name)
                } catch (e: SynchronisationException) {
                    throw IllegalStateException(e)
                }
                // Show login screen
                true
            }*/
            else -> {
                false
            }
        }
    }
}