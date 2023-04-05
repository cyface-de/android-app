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
package de.cyface.app.utils.trips

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.recyclerview.selection.MutableSelection
import de.cyface.app.utils.R
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
    private val exportPermissionLauncher: ActivityResultLauncher<Array<String>>,
    private val context: WeakReference<Context>
) : androidx.core.view.MenuProvider {

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.trips, menu)
        val showExport = context.get()!!.packageName.equals("de.cyface.app")
        if (showExport) {
            menu.findItem(R.id.export).isVisible = true
        }
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.sync -> {
                syncNow()
                true
            }
            R.id.export -> {
                // Permission requirements: https://developer.android.com/training/data-storage
                val requiresWritePermission =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                if (requiresWritePermission) {
                    if (ContextCompat.checkSelfPermission(
                            context.get()!!,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                            context.get()!!,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {

                        exportPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            )
                        )
                    } else {
                        GlobalScope.launch { Exporter(context.get()!!).export() }
                    }
                } else {
                    GlobalScope.launch { Exporter(context.get()!!).export() }
                }

                true
            }
            R.id.select_all -> {
                adapter.selectAll()
                true
            }
            R.id.delete -> {
                deleteSelectedMeasurements()
                true
            }
            else -> {
                false
            }
        }
    }

    /*@Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        val fragmentActivity = activity
        Validate.notNull(fragmentActivity)
        when (requestCode) {
            SharedConstants.PERMISSION_REQUEST_EXTERNAL_STORAGE_FOR_EXPORT -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                    fragmentActivity,
                    fragmentActivity!!.getString(de.cyface.app.utils.R.string.export_data),
                    Toast.LENGTH_LONG
                ).show()
                ExportTask(fragmentActivity).execute()
            } else {
                Toast.makeText(
                    fragmentActivity,
                    fragmentActivity!!.getString(de.cyface.app.utils.R.string.export_data_no_permission),
                    Toast.LENGTH_LONG
                ).show()
            }
            else -> {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
        }
    }*/

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
                    .getString(R.string.error_message_sync_canceled_no_wifi),
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
                    .getString(R.string.error_message_sync_canceled_disabled),
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
                    .getString(R.string.delete_data_non_selected),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        GlobalScope.launch {
            val persistence = DefaultPersistenceLayer(
                context.get()!!,
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