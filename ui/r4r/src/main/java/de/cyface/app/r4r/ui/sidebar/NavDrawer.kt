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
package de.cyface.app.r4r.ui.sidebar

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import android.view.MenuItem
import android.widget.CompoundButton
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import de.cyface.app.r4r.MainActivity
import de.cyface.app.r4r.R
import de.cyface.app.utils.SharedConstants.PREFERENCES_MOVE_TO_LOCATION_KEY
import de.cyface.app.utils.SharedConstants.PREFERENCES_SYNCHRONIZATION_KEY
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.synchronization.WiFiSurveyor
import de.cyface.synchronization.exception.SynchronisationException

/**
 * The Nav Drawer is a menu which allows the user to switch settings with one click and to access other
 * `Fragment`s of the app.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.2.0
 */
class NavDrawer(
    mainActivity: MainActivity, view: NavigationView, layout: DrawerLayout,
    toolbar: Toolbar
) : NavigationView.OnNavigationItemSelectedListener {
    private val layout: DrawerLayout
    private val listener: MutableCollection<NavDrawerListener>
    private val preferences: SharedPreferences
    private val mainActivity: MainActivity
    private val view: NavigationView

    init {
        this.view = view
        listener = HashSet()
        this.layout = layout
        this.mainActivity = mainActivity
        view.setNavigationItemSelectedListener(this)

        // setup nav bar setting switches
        val applicationContext: Context = mainActivity.applicationContext
        preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val zoomToLocationToggle = view.menu
            .findItem(R.id.drawer_setting_zoom_to_location).actionView as SwitchCompat
        val synchronizationToggle = view.menu
            .findItem(R.id.drawer_setting_synchronization).actionView as SwitchCompat

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
    }

    fun addNavDrawerListener(navDrawerListener: NavDrawerListener?) {
        checkNotNull(navDrawerListener) { "Invalid value for nav drawer listener: null." }
        listener.add(navDrawerListener)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.drawer_item_guide -> guideSelected(item)
            R.id.drawer_item_feedback -> feedbackSelected(item)
            R.id.drawer_item_imprint -> imprintSelected(item)
            R.id.drawer_item_logout -> logoutSelected(item)
            R.id.drawer_setting_zoom_to_location, R.id.drawer_setting_synchronization -> (item.actionView as SwitchCompat?)!!.toggle()
            else -> return false
        }
        return true
    }

    private fun guideSelected(item: MenuItem) {
        for (listener in listener) {
            listener.guideSelected()
        }
        finishSelection(item)
    }

    private fun feedbackSelected(item: MenuItem) {
        for (listener in listener) {
            listener.feedbackSelected()
        }
        finishSelection(item)
    }

    private fun imprintSelected(item: MenuItem) {
        for (listener in listener) {
            listener.imprintSelected()
        }
        finishSelection(item)
    }

    private fun logoutSelected(item: MenuItem) {
        item.isEnabled = false
        for (listener in listener) {
            listener.logoutSelected()
        }
        val dataCapturingService: CyfaceDataCapturingService =
            mainActivity.capturingService
        try {
            dataCapturingService.removeAccount(dataCapturingService.wiFiSurveyor.account.name)
        } catch (e: SynchronisationException) {
            throw IllegalStateException(e)
        }
        // Show login screen
        mainActivity.startSynchronization(view.context)
        item.isEnabled = true
        layout.closeDrawers()
    }

    private fun finishSelection(item: MenuItem) {
        item.isChecked = true // Highlight the selected item has been done by NavigationView
        layout.closeDrawers()
    }

    fun closeIfOpen(): Boolean {
        return if (layout.isDrawerOpen(GravityCompat.START)) {
            layout.closeDrawers()
            true
        } else {
            false
        }
    }

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

    fun getView(): NavigationView {
        return view
    }
}