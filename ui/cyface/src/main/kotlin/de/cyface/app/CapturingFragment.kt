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
package de.cyface.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import de.cyface.app.button.SynchronizationButton
import de.cyface.app.capturing.MenuProvider
import de.cyface.app.databinding.FragmentCapturingBinding
import de.cyface.app.dialog.ModalityDialog
import de.cyface.app.ui.button.DataCapturingButton
import de.cyface.app.utils.Map
import de.cyface.app.utils.ServiceProvider
import de.cyface.camera_service.foreground.CameraService
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.Event
import de.cyface.persistence.model.Modality
import de.cyface.synchronization.ConnectionStatusListener
import de.cyface.utils.AppPreferences
import de.cyface.utils.Validate
import io.sentry.Sentry

/**
 * A `Fragment` for the main UI used for data capturing and supervision of the capturing process.
 *
 * @author Armin Schnabel
 * @version 1.4.3
 * @since 1.0.0
 */
class CapturingFragment : Fragment(), ConnectionStatusListener {

    /**
     * This property is only valid between onCreateView and onDestroyView.
     */
    private var _binding: FragmentCapturingBinding? = null

    /**
     * The generated class which holds all bindings from the layout file.
     */
    private val binding get() = _binding!!

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
     * The `Map` used to visualize the ongoing capturing.
     */
    var map: Map? = null
        private set

    /**
     * The `SharedPreferences` used to store the app preferences.
     */
    private lateinit var preferences: AppPreferences

    /**
     * The `DataCapturingService` which represents the API of the Cyface Android SDK.
     */
    private lateinit var capturing: CyfaceDataCapturingService

    /**
     * An implementation of the persistence layer which caches some data during capturing.
     */
    private lateinit var persistence: DefaultPersistenceLayer<CapturingPersistenceBehaviour>

    /**
     * The `CameraService` which collects camera data if the user did activate this feature.
     */
    private lateinit var cameraService: CameraService

    // Ensure onMapReadyRunnable is called after permissions are newly granted
    // This launcher must be launched to request permissions
    private var permissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result.isNotEmpty()) {
                val allGranted = result.values.none { !it }
                if (allGranted) {
                    // Only if the map already called it's onMapReady
                    if (map!!.googleMap != null) {
                        map!!.onMapReady()
                    }
                } else {
                    Toast.makeText(
                        context,
                        requireContext().getString(de.cyface.app.utils.R.string.missing_location_permissions_toast),
                        Toast.LENGTH_LONG
                    ).show()
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
            map!!.render(currentMeasurementsTracks, currentMeasurementsEvents, false, ArrayList())
        } catch (e: NoSuchMeasurementException) {
            if (preferences.getReportingAccepted()) {
                Sentry.captureException(e)
            }
            Log.w(
                TAG,
                "onMapReadyRunnable failed to loadCurrentMeasurementsEvents. Thus, map.renderMeasurement() is not executed. This should only happen when the capturing already stopped."
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (activity is ServiceProvider) {
            capturing = (activity as ServiceProvider).capturing
            persistence = capturing.persistenceLayer
            cameraService = (activity as CameraServiceProvider).cameraService
        } else {
            throw RuntimeException("Context doesn't support the Fragment, implement `ServiceProvider`")
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
    ): View {
        _binding = FragmentCapturingBinding.inflate(inflater, container, false)
        dataCapturingButton = DataCapturingButton(this)
        preferences = AppPreferences(requireContext())
        // Register synchronization listener
        capturing.addConnectionStatusListener(this)
        syncButton = SynchronizationButton(capturing)
        showModalitySelectionDialogIfNeeded()
        dataCapturingButton!!.onCreateView(
            binding.captureDataMainButton,
            null
        )
        map = Map(
            binding.mapView,
            savedInstanceState,
            onMapReadyRunnable,
            permissionLauncher
        )
        syncButton!!.onCreateView(
            binding.dataSyncButton,
            binding.connectionStatusProgress
        )
        dataCapturingButton!!.bindMap(map)

        // Add items to menu (top right)
        // Not using `findNavController()` as `FragmentContainerView` in `activity_main.xml` does not
        // work with with `findNavController()` (https://stackoverflow.com/a/60434988/5815054).
        val navHostFragment =
            requireActivity().supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        requireActivity().addMenuProvider(
            MenuProvider(
                requireActivity() as MainActivity,
                navHostFragment.navController
            ),
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )

        return binding.root
    }

    private fun showModalitySelectionDialogIfNeeded() {
        registerModalityTabSelectionListener()
        if (preferences.getModality() != null) {
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
        val tabLayout = binding.modalityTabs
        val newModality = arrayOfNulls<Modality>(1)
        tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val oldModalityId = preferences.getModality()
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
                preferences.saveModality(newModality[0]!!.databaseIdentifier)
                if (oldModality != null && oldModality == newModality[0]) {
                    Log.d(
                        TAG,
                        "changeModalityType(): old (" + oldModality + " and new Modality (" + newModality[0]
                                + ") types are equal not recording event."
                    )
                    return
                }
                capturing.changeModalityType(newModality[0]!!)

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
    @Deprecated("Deprecated in Java")
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
        val tabLayout = binding.modalityTabs
        val modality = preferences.getModality()
        Validate.notNull(modality, "Modality should already be set but isn't.")

        // Select the Modality tab
        val tab: TabLayout.Tab? = when (modality) {
            Modality.CAR.name -> {
                tabLayout.getTabAt(0)
            }

            Modality.BICYCLE.name -> {
                tabLayout.getTabAt(1)
            }

            Modality.WALKING.name -> {
                tabLayout.getTabAt(2)
            }

            Modality.BUS.name -> {
                tabLayout.getTabAt(3)
            }

            Modality.TRAIN.name -> {
                tabLayout.getTabAt(4)
            }

            else -> {
                throw IllegalArgumentException("Unknown Modality id: $modality")
            }
        }
        Validate.notNull(tab)
        tab!!.select()
    }

    override fun onResume() {
        super.onResume()
        syncButton!!.onResume()
        capturing.addConnectionStatusListener(this)
        map!!.onResume()
        dataCapturingButton!!.onResume(capturing, cameraService)
    }

    override fun onPause() {
        map!!.onPause()
        dataCapturingButton!!.onPause()
        capturing.removeConnectionStatusListener(this)
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

        capturing.removeConnectionStatusListener(this)
        Log.d(TAG, "onDestroyView: stopped CyfaceDataCapturingService")
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        var imageView = binding.captureDataMainButton
        outState.putInt("capturing_button_resource_id", imageView.id)
        imageView = binding.dataSyncButton
        outState.putInt("data_sync_button_id", imageView.id)
        try {
            val donutProgress = binding.connectionStatusProgress
            outState.putInt(
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
    }
}