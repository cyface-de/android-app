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
package de.cyface.app.r4r.ui.trips

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.recyclerview.selection.MutableSelection
import de.cyface.app.r4r.R
import de.cyface.app.r4r.utils.Constants
import de.cyface.app.utils.SharedConstants
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.persistence.DefaultPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.synchronization.WiFiSurveyor
import de.cyface.utils.Validate
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * The [androidx.core.view.MenuProvider] for the [TripsFragment] which defines which options are
 * shown in the action bar at the top right.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.2.0
 */
class MenuProvider(
    private val capturingService: CyfaceDataCapturingService,
    private val preferences: SharedPreferences,
    private val adapter: TripListAdapter,
    private val context: WeakReference<Context>
) : androidx.core.view.MenuProvider {

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.trips, menu)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sync -> {
                syncNow()
                true
            }
            R.id.select_all_item -> {
                adapter.selectAll()
                true
            }
            R.id.delete_measurement_item -> {
                deleteSelectedMeasurements()
                true
            }
            else -> {
                false
            }
        }
    }

    /**
     * Triggers synchronization if a network is available and synchronization is enabled.
     */
    private fun syncNow() {
        // Check if syncable network is available
        val isConnected = capturingService.wiFiSurveyor.isConnected
        Log.v(
            WiFiSurveyor.TAG,
            (if (isConnected) "" else "Not ") + "connected to syncable network"
        )
        if (!isConnected) {
            Toast.makeText(
                context.get(),
                context.get()!!
                    .getString(de.cyface.app.utils.R.string.error_message_sync_canceled_no_wifi),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Check is sync is disabled via frontend
        val syncEnabled = capturingService.wiFiSurveyor.isSyncEnabled
        val syncPreferenceEnabled =
            preferences.getBoolean(SharedConstants.PREFERENCES_SYNCHRONIZATION_KEY, true)
        Validate.isTrue(
            syncEnabled == syncPreferenceEnabled,
            "sync " + (if (syncEnabled) "enabled" else "disabled")
                    + " but syncPreference " + if (syncPreferenceEnabled) "enabled" else "disabled"
        )
        if (!syncEnabled) {
            Toast.makeText(
                context.get(),
                context.get()!!
                    .getString(de.cyface.app.utils.R.string.error_message_sync_canceled_disabled),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Request instant Synchronization
        capturingService.scheduleSyncNow()
    }

    /**
     * Deletes the selected measurements from the device.
     *
     * TODO: Does not delete data collected by camera service. (see `MeasurementDeleteController`)
     */
    private fun deleteSelectedMeasurements() {
        if (adapter.tracker!!.selection.isEmpty) {
            Toast.makeText(
                context.get(),
                context.get()!!
                    .getString(de.cyface.app.utils.R.string.delete_data_non_selected),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        GlobalScope.launch {
            val persistence = DefaultPersistenceLayer(
                context.get()!!,
                Constants.AUTHORITY,
                DefaultPersistenceBehaviour()
            )
            val mutableSelection = MutableSelection<Long>()
            adapter.tracker!!.copySelection(mutableSelection)
            mutableSelection.forEach { position ->
                run {
                    val measurementId = adapter.getItem(position.toInt()).id
                    persistence.delete(measurementId)
                    adapter.tracker!!.deselect(position)
                }
            }
        }
    }
}