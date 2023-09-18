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
package de.cyface.app.r4r.capturing

import android.content.Intent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import de.cyface.app.r4r.MainActivity
import de.cyface.app.r4r.R
import de.cyface.app.r4r.utils.Constants.SUPPORT_EMAIL
import de.cyface.energy_settings.TrackingSettings
import de.cyface.uploader.exception.SynchronisationException

/**
 * The [androidx.core.view.MenuProvider] for the [CapturingFragment] which defines which options are
 * shown in the action bar at the top right.
 *
 * @author Armin Schnabel
 * @version 2.0.1
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
                    Toast.makeText(activity.applicationContext, "Logging out ...", Toast.LENGTH_SHORT).show()
                    // This inform the auth server that the user wants to end its session
                    activity.auth.endSession(activity)
                    //signOut() // instead of `endSession()` to sign out softly for testing
                    activity.capturing.removeAccount(activity.capturing.wiFiSurveyor.account.name)
                } catch (e: SynchronisationException) {
                    throw IllegalStateException(e)
                }
                // Show login screen
                // This is done by MainActivity.onActivityResult -> signOut()
                //(activity as MainActivity).startSynchronization()
                true
            }*/

            else -> {
                false
            }
        }
    }
}