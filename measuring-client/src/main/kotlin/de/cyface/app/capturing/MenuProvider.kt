/*
 * Copyright 2023 Cyface GmbH
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
package de.cyface.app.capturing

import android.content.Intent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import de.cyface.app.R
import de.cyface.app.ui.CapturingFragmentDirections
import de.cyface.app.ui.MainActivity
import de.cyface.app.utils.Constants.SUPPORT_EMAIL
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.energy_settings.TrackingSettings
import de.cyface.synchronization.exception.SynchronisationException

/**
 * The [androidx.core.view.MenuProvider] for the [de.cyface.app.ui.CapturingFragment] which defines which
 * options are shown in the action bar at the top right.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.2.0
 */
class MenuProvider(
    private val capturingService: CyfaceDataCapturingService,
    private val activity: FragmentActivity,
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
                        SUPPORT_EMAIL
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
            R.id.logout_item -> {
                try {
                    capturingService.removeAccount(capturingService.wiFiSurveyor.account.name)
                } catch (e: SynchronisationException) {
                    throw IllegalStateException(e)
                }
                // Show login screen
                (activity as MainActivity).startSynchronization()
                true
            }
            else -> {
                false
            }
        }
    }
}