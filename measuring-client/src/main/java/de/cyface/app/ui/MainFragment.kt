/*
 * Copyright 2017-2023 Cyface GmbH
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
package de.cyface.app.ui

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AccountManagerFuture
import android.accounts.AuthenticatorException
import android.accounts.OperationCanceledException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.github.lzyzsd.circleprogress.DonutProgress
import com.google.android.gms.maps.MapView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import de.cyface.app.BuildConfig
import de.cyface.app.R
import de.cyface.app.ui.button.DataCapturingButton
import de.cyface.app.ui.button.SynchronizationButton
import de.cyface.app.ui.dialog.ModalityDialog
import de.cyface.app.ui.notification.CameraEventHandler
import de.cyface.app.ui.notification.DataCapturingEventHandler
import de.cyface.app.utils.Constants
import de.cyface.app.utils.Map
import de.cyface.app.utils.SharedConstants.ACCEPTED_REPORTING_KEY
import de.cyface.app.utils.SharedConstants.DEFAULT_SENSOR_FREQUENCY
import de.cyface.app.utils.SharedConstants.PREFERENCES_MODALITY_KEY
import de.cyface.app.utils.SharedConstants.PREFERENCES_SENSOR_FREQUENCY_KEY
import de.cyface.app.utils.SharedConstants.PREFERENCES_SYNCHRONIZATION_KEY
import de.cyface.camera_service.CameraService
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.exception.SetupException
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.Event
import de.cyface.persistence.model.Modality
import de.cyface.synchronization.ConnectionStatusListener
import de.cyface.synchronization.WiFiSurveyor
import de.cyface.synchronization.exception.SynchronisationException
import de.cyface.utils.Validate
import io.sentry.Sentry
import java.io.IOException

/**
 * A `Fragment` for the main UI used for data capturing and supervision of the capturing process.
 *
 * @author Armin Schnabel
 * @version 1.4.2
 * @since 1.0.0
 */
class MainFragment : Fragment(), ConnectionStatusListener {
    /**
     * The `DataCapturingButton` which allows the user to control the capturing lifecycle.
     */
    private var dataCapturingButton: DataCapturingButton? = null

    /**
     * The `SynchronizationButton` which allows the user to manually trigger the synchronization
     * and to see the synchronization progress.
     */
    private var syncButton: SynchronizationButton? = null

    /**
     * The root element of this `Fragment`s `View`
     */
    private var fragmentRoot: View? = null

    /**
     * The `Map` used to visualize the ongoing capturing.
     */
    var map: Map? = null
        private set

    /**
     * The `SharedPreferences` used to store the user's preferences.
     */
    private var preferences: SharedPreferences? = null

    /**
     * The `DataCapturingService` which represents the API of the Cyface Android SDK.
     */
    var dataCapturingService: CyfaceDataCapturingService? = null
        private set

    /**
     * The `CameraService` which collects camera data if the user did activate this feature.
     */
    private var cameraService: CameraService? = null

    // Ensure onMapReadyRunnable is called after permissions are newly granted
    // This launcher must be launched to request permissions
    private var permissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.isNotEmpty()) {
                val allGranted = result.values.none { !it }
                if (allGranted) {
                    // Only if the map already called it's onMapReady
                    if (map!!.googleMap != null) {
                        map!!.onMapReady()
                    }
                } else {
                    Toast.makeText(context, "Location permission repeatedly denies", Toast.LENGTH_LONG).show()
                    // Close Cyface if permission has not been granted.
                    // When the user repeatedly denies the location permission, the app won't start
                    // and only starts again if the permissions are granted manually.
                    // It was always like this, but if this is a problem we need to add a screen
                    // which explains the user that this can happen.
                    requireActivity().finish()
                }
            }
        }

    /**
     * The `Runnable` triggered when the `Map` is loaded and ready.
     */
    private val onMapReadyRunnable = Runnable {
        val currentMeasurementsTracks =
            dataCapturingButton!!.currentMeasurementsTracks ?: return@Runnable
        val currentMeasurementsEvents: List<Event>
        try {
            currentMeasurementsEvents = dataCapturingButton!!.loadCurrentMeasurementsEvents()
            map!!.renderMeasurement(currentMeasurementsTracks, currentMeasurementsEvents, false)
        } catch (e: NoSuchMeasurementException) {
            val isReportingEnabled = preferences!!.getBoolean(ACCEPTED_REPORTING_KEY, false)
            if (isReportingEnabled) {
                Sentry.captureException(e)
            }
            Log.w(
                TAG,
                "onMapReadyRunnable failed to loadCurrentMeasurementsEvents. Thus, map.renderMeasurement() is not executed. This should only happen when the capturing already stopped."
            )
        }
    }

    /**
     * All non-graphical initializations should go into onCreate (which might be called before Activity's onCreate
     * finishes). All view-related initializations go into onCreateView and final initializations which depend on the
     * Activity's onCreate and the fragment's onCreateView to be finished belong into the onActivityCreated method
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentRoot = inflater.inflate(R.layout.fragment_capturing, container, false)
        dataCapturingButton = DataCapturingButton(this)
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val sensorFrequency = preferences!!.getInt(
            PREFERENCES_SENSOR_FREQUENCY_KEY,
            DEFAULT_SENSOR_FREQUENCY
        )

        // Start DataCapturingService and CameraService
        try {
            dataCapturingService = CyfaceDataCapturingService(
                requireContext(),
                Constants.AUTHORITY,
                Constants.ACCOUNT_TYPE,
                BuildConfig.cyfaceServer,
                DataCapturingEventHandler(),
                dataCapturingButton!!,
                sensorFrequency
            )
            // Needs to be called after new CyfaceDataCapturingService() for the SDK to check and throw
            // a specific exception when the LOGIN_ACTIVITY was not set from the SDK using app.
            startSynchronization(requireContext())
            dataCapturingService!!.addConnectionStatusListener(this)
            cameraService = CameraService(
                requireContext(), Constants.AUTHORITY, CameraEventHandler(),
                dataCapturingButton!!
            )
        } catch (e: SetupException) {
            throw IllegalStateException(e)
        }
        syncButton = SynchronizationButton(dataCapturingService!!)
        showModalitySelectionDialogIfNeeded()
        dataCapturingButton!!.onCreateView(
            fragmentRoot!!.findViewById(R.id.capture_data_main_button),
            null
        )
        map = Map(
            fragmentRoot!!.findViewById<MapView>(R.id.mapView),
            savedInstanceState,
            onMapReadyRunnable,
            permissionLauncher
        )
        syncButton!!.onCreateView(
            fragmentRoot!!.findViewById(R.id.data_sync_button),
            fragmentRoot!!.findViewById(R.id.connection_status_progress)
        )
        dataCapturingButton!!.bindMap(map)
        return fragmentRoot
    }

    /**
     * Starts the synchronization. Creates an [Account] if there is none as an account is required
     * for synchronization. If there was no account the synchronization is started when the async account
     * creation future returns to ensure the account is available at that point.
     *
     * @param context the [Context] to load the [AccountManager]
     */
    fun startSynchronization(context: Context?) {
        val accountManager = AccountManager.get(context)
        val validAccountExists = accountWithTokenExists(accountManager)
        if (validAccountExists) {
            try {
                Log.d(TAG, "startSynchronization: Starting WifiSurveyor with exiting account.")
                dataCapturingService!!.startWifiSurveyor()
            } catch (e: SetupException) {
                throw IllegalStateException(e)
            }
            return
        }

        // The LoginActivity is called by Android which handles the account creation
        Log.d(TAG, "startSynchronization: No validAccountExists, requesting LoginActivity")
        accountManager.addAccount(
            Constants.ACCOUNT_TYPE,
            de.cyface.synchronization.Constants.AUTH_TOKEN_TYPE,
            null,
            null,
            MainActivity.getMainActivityFromContext(context),
            { future: AccountManagerFuture<Bundle?> ->
                val accountManager1 = AccountManager.get(context)
                try {
                    // allows to detect when LoginActivity is closed
                    future.result

                    // The LoginActivity created a temporary account which cannot be used for synchronization.
                    // As the login was successful we now register the account correctly:
                    val account = accountManager1.getAccountsByType(Constants.ACCOUNT_TYPE)[0]
                    Validate.notNull(account)

                    // Set synchronizationEnabled to the current user preferences
                    val syncEnabledPreference = preferences!!
                        .getBoolean(PREFERENCES_SYNCHRONIZATION_KEY, true)
                    Log.d(
                        WiFiSurveyor.TAG,
                        "Setting syncEnabled for new account to preference: $syncEnabledPreference"
                    )
                    dataCapturingService!!.wiFiSurveyor.makeAccountSyncable(
                        account,
                        syncEnabledPreference
                    )
                    Log.d(TAG, "Starting WifiSurveyor with new account.")
                    dataCapturingService!!.startWifiSurveyor()
                } catch (e: OperationCanceledException) {
                    // Remove temp account when LoginActivity is closed during login [CY-5087]
                    val accounts = accountManager1.getAccountsByType(Constants.ACCOUNT_TYPE)
                    if (accounts.size > 0) {
                        val account = accounts[0]
                        accountManager1.removeAccount(account, null, null)
                    }
                    // This closes the app when the LoginActivity is closed
                    MainActivity.getMainActivityFromContext(context).finish()
                } catch (e: AuthenticatorException) {
                    throw IllegalStateException(e)
                } catch (e: IOException) {
                    throw IllegalStateException(e)
                } catch (e: SetupException) {
                    throw IllegalStateException(e)
                }
            },
            null
        )
    }

    private fun showModalitySelectionDialogIfNeeded() {
        registerModalityTabSelectionListener()
        val selectedModality = preferences!!.getString(PREFERENCES_MODALITY_KEY, null)
        if (selectedModality != null) {
            selectModalityTab()
            return
        }
        val fragmentManager = fragmentManager
        Validate.notNull(fragmentManager)
        val dialog = ModalityDialog()
        dialog.setTargetFragment(this, DIALOG_INITIAL_MODALITY_SELECTION_REQUEST_CODE)
        dialog.isCancelable = false
        dialog.show(fragmentManager!!, "MODALITY_DIALOG")
    }

    private fun registerModalityTabSelectionListener() {
        val tabLayout = fragmentRoot!!.findViewById<TabLayout>(R.id.tab_layout)
        val newModality = arrayOfNulls<Modality>(1)
        tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val oldModalityId = preferences!!.getString(PREFERENCES_MODALITY_KEY, null)
                val oldModality =
                    if (oldModalityId == null) null else Modality.valueOf(oldModalityId)
                when (tab.position) {
                    0 -> newModality[0] = Modality.CAR
                    1 -> newModality[0] = Modality.BICYCLE
                    2 -> newModality[0] = Modality.WALKING
                    3 -> newModality[0] = Modality.BUS
                    4 -> newModality[0] = Modality.TRAIN
                    else -> throw IllegalArgumentException("Unknown tab selected: " + tab.position)
                }
                preferences!!.edit()
                    .putString(PREFERENCES_MODALITY_KEY, newModality[0]!!.databaseIdentifier)
                    .apply()
                if (oldModality != null && oldModality == newModality[0]) {
                    Log.d(
                        TAG,
                        "changeModalityType(): old (" + oldModality + " and new Modality (" + newModality[0]
                                + ") types are equal not recording event."
                    )
                    return
                }
                dataCapturingService!!.changeModalityType(newModality[0]!!)

                // Deactivated for pro app until we show them their own tiles:
                // if (map != null) { map.loadCyfaceTiles(newModality[0].getDatabaseIdentifier()); }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                // Nothing to do here
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
                // Nothing to do here
            }
        })
    }

    /**
     * We use the activity result method as a callback from the `Modality` dialog to the main fragment
     * to setup the tabs as soon as a [Modality] type is selected the first time
     *
     * @param requestCode is used to differentiate and identify requests
     * @param resultCode is used to describe the request's result
     * @param data an intent which may contain result data
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DIALOG_INITIAL_MODALITY_SELECTION_REQUEST_CODE) {
            selectModalityTab()
        }
    }

    /**
     * Depending on which [Modality] is selected in the preferences the respective tab is selected.
     * Also, the tiles relative to the selected `Modality` are loaded onto the map, if enabled.
     *
     * Make sure the order (0, 1, 2 from left(start) to right(end)) in the TabLayout xml file is consistent
     * with the order here to map the correct enum to each tab.
     */
    private fun selectModalityTab() {
        val tabLayout = fragmentRoot!!.findViewById<TabLayout>(R.id.tab_layout)
        val modality = preferences!!.getString(PREFERENCES_MODALITY_KEY, null)
        Validate.notNull(modality, "Modality should already be set but isn't.")

        // Select the Modality tab
        val tab: TabLayout.Tab?
        tab = if (modality == Modality.CAR.name) {
            tabLayout.getTabAt(0)
        } else if (modality == Modality.BICYCLE.name) {
            tabLayout.getTabAt(1)
        } else if (modality == Modality.WALKING.name) {
            tabLayout.getTabAt(2)
        } else if (modality == Modality.BUS.name) {
            tabLayout.getTabAt(3)
        } else if (modality == Modality.TRAIN.name) {
            tabLayout.getTabAt(4)
        } else {
            throw IllegalArgumentException("Unknown Modality id: $modality")
        }
        Validate.notNull(tab)
        tab!!.select()
    }

    override fun onResume() {
        super.onResume()
        syncButton!!.onResume()
        dataCapturingService!!.addConnectionStatusListener(this)
        map!!.onResume()
        dataCapturingButton!!.onResume(dataCapturingService!!, cameraService!!)
    }

    override fun onPause() {
        map!!.onPause()
        dataCapturingButton!!.onPause()
        dataCapturingService!!.removeConnectionStatusListener(this)
        super.onPause()
    }

    override fun onSyncStarted() {
        if (isAdded) {
            syncButton!!.isSynchronizingChanged(true)
        } else {
            Log.w(TAG, "onSyncStarted called but fragment is not attached")
        }
    }

    override fun onSyncFinished() {
        if (isAdded) {
            syncButton!!.isSynchronizingChanged(false)
        } else {
            Log.w(TAG, "onSyncFinished called but fragment is not attached")
        }
    }

    override fun onProgress(percent: Float, measurementId: Long) {
        if (isAdded) {
            Log.v(TAG, "Sync progress received: $percent %, mid: $measurementId")
            syncButton!!.updateProgress(percent)
        }
    }

    override fun onDestroyView() {
        syncButton!!.onDestroyView()
        dataCapturingButton!!.onDestroyView()

        // Clean up CyfaceDataCapturingService
        try {
            // As the WifiSurveyor WiFiSurveyor.startSurveillance() tells us to
            dataCapturingService!!.shutdownDataCapturingService()
        } catch (e: SynchronisationException) {
            val isReportingEnabled = preferences!!.getBoolean(ACCEPTED_REPORTING_KEY, false)
            if (isReportingEnabled) {
                Sentry.captureException(e)
            }
            Log.w(TAG, "Failed to shut down CyfaceDataCapturingService. ", e)
        }
        dataCapturingService!!.removeConnectionStatusListener(this)
        Log.d(TAG, "onDestroyView: stopped CyfaceDataCapturingService")
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        var imageView =
            fragmentRoot!!.findViewById<View>(R.id.capture_data_main_button).tag as ImageView
        if (imageView != null) outState.putInt("capturing_button_resource_id", imageView.id)
        imageView = fragmentRoot!!.findViewById<View>(R.id.data_sync_button).tag as ImageView
        if (imageView != null) outState.putInt("data_sync_button_id", imageView.id)
        try {
            val donutProgress = fragmentRoot!!
                .findViewById<View>(R.id.connection_status_progress).tag as DonutProgress
            if (imageView != null) outState.putInt(
                "connection_status_progress_id",
                donutProgress.id
            )
        } catch (e: NullPointerException) {
            Log.w(TAG, "Failed to save donutProgress view state")
        }
        super.onSaveInstanceState(outState)
    }

    companion object {
        /**
         * The identifier for the [ModalityDialog] request which asks the user (initially) to select a
         * [Modality] preference.
         */
        const val DIALOG_INITIAL_MODALITY_SELECTION_REQUEST_CODE = 201909191

        /**
         * The tag used to identify logging from this class.
         */
        private const val TAG = "de.cyface.app.frag.main"

        /**
         * Checks if there is an account with an authToken.
         *
         * @param accountManager A reference to the [AccountManager]
         * @return true if there is an account with an authToken
         * @throws RuntimeException if there is more than one account
         */
        @JvmStatic
        fun accountWithTokenExists(accountManager: AccountManager): Boolean {
            val existingAccounts = accountManager.getAccountsByType(Constants.ACCOUNT_TYPE)
            Validate.isTrue(existingAccounts.size < 2, "More than one account exists.")
            return existingAccounts.size != 0
        }
    }
}