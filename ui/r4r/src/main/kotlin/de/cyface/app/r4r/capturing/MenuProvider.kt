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
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import de.cyface.app.r4r.MainActivity.Companion.END_SESSION_REQUEST_CODE
import de.cyface.app.r4r.R
import de.cyface.app.r4r.utils.Constants.SUPPORT_EMAIL
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.energy_settings.TrackingSettings
import de.cyface.synchronization.AuthStateManager
import de.cyface.synchronization.Configuration
import de.cyface.uploader.exception.SynchronisationException
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.EndSessionRequest

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
     * The service used for authorization.
     */
    private lateinit var mAuthService: AuthorizationService

    /**
     * The authorization state.
     */
    private lateinit var mStateManager: AuthStateManager

    /**
     * The configuration of the OAuth 2 endpoint to authorize against.
     */
    private lateinit var mConfiguration: Configuration

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.capturing, menu)

        // Setting up feedback email template
        emailIntent = TrackingSettings.generateFeedbackEmailIntent(
            activity,
            activity.getString(de.cyface.energy_settings.R.string.feedback_error_description),
            SUPPORT_EMAIL
        )

        // Authorization
        val context = activity.applicationContext
        mStateManager = AuthStateManager.getInstance(context)
        //mExecutor = Executors.newSingleThreadExecutor()
        mConfiguration = Configuration.getInstance(context)
        val config = Configuration.getInstance(context)
        /*if (config.hasConfigurationChanged()) {
            // This happens when starting the app after a fresh installation
            //throw IllegalArgumentException("config changed (MenuProvider)")
            Toast.makeText(context, "Ignoring: config changed (SyncAdapter)", Toast.LENGTH_SHORT).show()
            //Handler().postDelayed({ signOut() }, 2000)
            //return
        }*/
        mAuthService = AuthorizationService(
            activity.applicationContext,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(config.connectionBuilder)
                .build()
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
                    Toast.makeText(activity.applicationContext, "Logging out ...", Toast.LENGTH_SHORT).show()
                    // This inform the auth server that the user wants to end its session
                    endSession()
                    //signOut() // instead of `endSession()` to sign out softly for testing
                } catch (e: SynchronisationException) {
                    throw IllegalStateException(e)
                }
                // Show login screen
                //(activity as MainActivity).startSynchronization() FIXME: This is already done be endSession()
                true
            }

            else -> {
                false
            }
        }
    }

    @MainThread
    private fun endSession() {
        val currentState: AuthState = mStateManager.current
        val config: AuthorizationServiceConfiguration =
            currentState.authorizationServiceConfiguration!!
        if (config.endSessionEndpoint != null) {
            val endSessionIntent: Intent = mAuthService.getEndSessionRequestIntent(
                EndSessionRequest.Builder(config)
                    .setIdTokenHint(currentState.idToken)
                    .setPostLogoutRedirectUri(mConfiguration.endSessionRedirectUri)
                    .build()
            )
            // This opens a browser window to inform the auth server that the user wants to log out.
            // The window closes after a split second and calls `MainActivity.onActivityResult`
            // where `signOut()` is executed which also removes the account from the account manager.
            activity.startActivityForResult(endSessionIntent, END_SESSION_REQUEST_CODE)
        } else {
            throw IllegalStateException("Auth server does not provide an end session endpoint")
            //signOut()
        }
    }
}