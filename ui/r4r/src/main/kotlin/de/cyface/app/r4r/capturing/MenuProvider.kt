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
import androidx.annotation.MainThread
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import de.cyface.app.r4r.MainActivity
import de.cyface.app.r4r.R
import de.cyface.synchronization.AuthStateManager
import de.cyface.app.r4r.auth.LoginActivity
import de.cyface.app.r4r.utils.Constants.SUPPORT_EMAIL
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.energy_settings.TrackingSettings
import de.cyface.uploader.exception.SynchronisationException
import net.openid.appauth.AuthState

/**
 * The [androidx.core.view.MenuProvider] for the [CapturingFragment] which defines which options are
 * shown in the action bar at the top right.
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

    /**
     * The authorization state.
     */
    private lateinit var mStateManager: AuthStateManager

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.capturing, menu)

        // Setting up feedback email template
        emailIntent = TrackingSettings.generateFeedbackEmailIntent(
            activity,
            activity.getString(de.cyface.energy_settings.R.string.feedback_error_description),
            SUPPORT_EMAIL
        )

        // Authorization
        mStateManager = AuthStateManager.getInstance(activity)
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
                    //FIXME: capturingService.removeAccount(capturingService.wiFiSurveyor.account.name)
                    signOut()
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

    @MainThread
    private fun signOut() {
        // discard the authorization and token state, but retain the configuration and
        // dynamic client registration (if applicable), to save from retrieving them again.
        val currentState: AuthState = mStateManager.current
        val clearedState = AuthState(currentState.authorizationServiceConfiguration!!)
        if (currentState.lastRegistrationResponse != null) {
            clearedState.update(currentState.lastRegistrationResponse)
        }
        mStateManager.replace(clearedState)
        val mainIntent = Intent(activity, LoginActivity::class.java)
        mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        activity.startActivity(mainIntent)
        activity.finish()
    }
}