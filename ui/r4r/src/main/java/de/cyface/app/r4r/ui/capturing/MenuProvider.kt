package de.cyface.app.r4r.ui.capturing

import android.content.Intent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import de.cyface.app.r4r.MainActivity
import de.cyface.app.r4r.R
import de.cyface.app.r4r.utils.Constants.SUPPORT_EMAIL
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.energy_settings.TrackingSettings
import de.cyface.synchronization.exception.SynchronisationException

class MenuProvider(
    private val capturingService: CyfaceDataCapturingService,
    private val activity: FragmentActivity,
    private val navController: NavController
) :
    androidx.core.view.MenuProvider {

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
            /*R.id.action_sync -> {
                capturingService.scheduleSyncNow()
                true
            }*/
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
                // FIXME
                /*override fun onAutoCenterMapSettingsChanged() {
                    /**if (mainFragment == null) {
                    Log.d(TAG, "Not updating map's autoCenterMapSettings as mainFragment is null")
                    return
                    }

                    val autoCenterEnabled = preferences!!.getBoolean(PREFERENCES_MOVE_TO_LOCATION_KEY, false)
                    val map: Map = mainFragment.getMap()
                    if (map == null) {
                    Log.d(TAG,"Not updating map's autoCenterMapSettings as map is null")
                    return
                    }

                    map.isAutoCenterMapEnabled = autoCenterEnabled
                    Log.d(TAG,"setAutoCenterMapEnabled to $autoCenterEnabled")*/
                }

                // was in drawer.init
                // setup nav bar setting switches
                val applicationContext: Context = mainActivity.applicationContext
                preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                val zoomToLocationToggle = view.menu
                    .findItem(R.id.drawer_setting_zoom_to_location).actionView as SwitchCompat
                val synchronizationToggle = view.menu
                    .findItem(R.id.drawer_setting_synchronization).actionView as SwitchCompat
                R.id.drawer_setting_zoom_to_location, R.id.drawer_setting_synchronization -> {
                    (item.actionView as SwitchCompat?)!!.toggle()
                }
                zoomToLocationToggle.isChecked =
                    preferences.getBoolean(PREFERENCES_MOVE_TO_LOCATION_KEY, false)
                // SynchronizationEnabled is set to the user preference when account is created
                val syncEnabledPreference: Boolean =
                    preferences.getBoolean(PREFERENCES_SYNCHRONIZATION_KEY, true)
                Log.d(
                    WiFiSurveyor.TAG,
                    "Setting navDrawer switch to syncEnabledPreference: $syncEnabledPreference"
                )
                synchronizationToggle.isChecked = syncEnabledPreference
                zoomToLocationToggle.setOnCheckedChangeListener(AutoCenterMapSettingsChangedListener())
                synchronizationToggle.setOnCheckedChangeListener(SynchronizationToggleListener())
                val drawerToggle = setupDrawerToggle(layout, toolbar)
                layout.addDrawerListener(drawerToggle) // Tie DrawerLayout events to the ActionBarToggle

                private fun setupDrawerToggle(layout: DrawerLayout, toolbar: Toolbar): ActionBarDrawerToggle {
                    val drawerToggle = ActionBarDrawerToggle(
                        mainActivity, layout, toolbar, R.string.app_name,
                        R.string.app_name
                    )
                    // This is necessary to change the icon of the Drawer Toggle upon state change.
                    drawerToggle.syncState()
                    return drawerToggle
                }
                /**
                 * A listener which is called when the "zoom to location on updates" toggle in the [NavDrawer] is clicked.
                 */
                private inner class AutoCenterMapSettingsChangedListener :
                    CompoundButton.OnCheckedChangeListener {
                    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
                        val applicationContext: Context = view.context.applicationContext
                        val editor = preferences.edit()
                        editor.putBoolean(PREFERENCES_MOVE_TO_LOCATION_KEY, isChecked)
                        editor.apply()
                        if (isChecked) {
                            Toast.makeText(
                                applicationContext,
                                de.cyface.app.utils.R.string.zoom_to_location_enabled_toast,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        for (listener in listener) {
                            listener.onAutoCenterMapSettingsChanged()
                        }
                    }
                }
                /**
                 * A listener which is called when the synchronization toggle in the [NavDrawer] is clicked.
                 */
                private inner class SynchronizationToggleListener : CompoundButton.OnCheckedChangeListener {
                    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
                        val applicationContext: Context = buttonView.context.applicationContext

                        // Update both, the preferences and WifiSurveyor's synchronizationEnabled status
                        Log.d(
                            WiFiSurveyor.TAG,
                            (if (isChecked) "Enable" else "Disable") + " sync and update preferences"
                        )
                        mainActivity.capturingService.wiFiSurveyor.isSyncEnabled = isChecked
                        preferences.edit().putBoolean(PREFERENCES_SYNCHRONIZATION_KEY, isChecked).apply()

                        // Show warning to user (storage gets filled)
                        if (!isChecked) {
                            Toast.makeText(
                                applicationContext,
                                de.cyface.app.utils.R.string.sync_disabled_toast,
                                Toast.LENGTH_LONG
                            )
                                .show()
                        }
                    }
                }
                */
                true
            }
            R.id.logout_item -> {
                try {
                    capturingService.removeAccount(capturingService.wiFiSurveyor.account.name)
                } catch (e: SynchronisationException) {
                    throw IllegalStateException(e)
                }
                // Show login screen
                (activity as MainActivity).startSynchronization(activity)
                true
            }
            else -> {
                false
            }
        }
    }
}