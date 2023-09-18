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
package de.cyface.app.digural

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import de.cyface.app.digural.button.SynchronizationButton
import de.cyface.app.digural.capturing.MenuProvider
import de.cyface.app.digural.databinding.FragmentCapturingBinding
import de.cyface.app.digural.dialog.ModalityDialog
import de.cyface.app.digural.ui.button.DataCapturingButton
import de.cyface.app.utils.Map
import de.cyface.app.utils.ServiceProvider
import de.cyface.camera_service.settings.CameraSettings
import de.cyface.camera_service.foreground.CameraService
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.Event
import de.cyface.persistence.model.Modality
import de.cyface.synchronization.ConnectionStatusListener
import de.cyface.utils.settings.AppSettings
import de.cyface.utils.Validate
import io.sentry.Sentry
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * A `Fragment` for the main UI used for data capturing and supervision of the capturing process.
 *
 * @author Armin Schnabel
 * @version 1.5.1
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
     * The settings used by both, UIs and libraries.
     */
    private lateinit var appSettings: AppSettings

    /**
     * The `SharedPreferences` used to store the camera preferences.
     */
    private lateinit var cameraSettings: CameraSettings

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

    /**
     * The launcher to call after a permission request returns.
     */
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    /**
     * The dialog which inform the user about missing permissions.
     */
    private var permissionDialog: AlertDialog? = null

    /**
     * `true` if remissions are already requested, to avoid endless loop.
     */
    private var isPermissionRequested = false

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
            val reportErrors = runBlocking { appSettings.reportErrorsFlow.first() }
            if (reportErrors) {
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
        cameraSettings =
            if (activity is CameraServiceProvider) {
                (activity as CameraServiceProvider).cameraSettings
            } else {
                throw RuntimeException("Context doesn't support the Fragment, implement `CameraServiceProvider`")
            }

        if (activity is ServiceProvider) {
            capturing = (activity as ServiceProvider).capturing
            appSettings = (activity as ServiceProvider).appSettings
            persistence = capturing.persistenceLayer
            cameraService = (activity as CameraServiceProvider).cameraService
        } else {
            throw RuntimeException("Context doesn't support the Fragment, implement `ServiceProvider`")
        }

        // Location permissions are requested by CapturingFragment/Map to react to results.
        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                val cameraMissing = cameraPermissionMissing(requireContext(), cameraSettings)
                val notificationMissing = notificationPermissionMissing(requireContext())
                val locationMissing =
                    !granted(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                val nonMissing = !cameraMissing && !notificationMissing && !locationMissing
                if (nonMissing) {
                    isPermissionRequested = false
                    // Ensure onMapReadyRunnable is called after permissions are newly granted.
                    // Only if the map already called it's onMapReady
                    if (map!!.googleMap != null) {
                        map!!.onMapReady()
                    }
                } else {
                    showMissingPermissions(cameraMissing, notificationMissing, locationMissing)
                }
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
        dataCapturingButton =
            DataCapturingButton(this, appSettings, cameraSettings)
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
        val modality = runBlocking { Modality.valueOf(appSettings.modalityFlow.first()) }
        if (modality != Modality.UNKNOWN) {
            selectModalityTab()
            return
        }
        val fragmentManager = fragmentManager
        Validate.notNull(fragmentManager)
        val dialog = ModalityDialog(appSettings)
        dialog.setTargetFragment(this, DIALOG_INITIAL_MODALITY_SELECTION_REQUEST_CODE)
        dialog.isCancelable = false
        dialog.show(fragmentManager!!, "MODALITY_DIALOG")
    }

    private fun registerModalityTabSelectionListener() {
        val tabLayout = binding.modalityTabs
        val newModality = arrayOfNulls<Modality>(1)
        tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val oldModalityId = runBlocking { appSettings.modalityFlow.first() }
                val oldModality = Modality.valueOf(oldModalityId)
                when (tab.position) {
                    0 -> newModality[0] = Modality.CAR
                    1 -> newModality[0] = Modality.BICYCLE
                    2 -> newModality[0] = Modality.WALKING
                    3 -> newModality[0] = Modality.BUS
                    4 -> newModality[0] = Modality.TRAIN
                    else -> throw IllegalArgumentException("Unknown tab selected: " + tab.position)
                }
                GlobalScope.launch { appSettings.setModality(newModality[0]!!.databaseIdentifier) }
                if (oldModality == newModality[0]) {
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
        val modality = runBlocking { appSettings.modalityFlow.first() }
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

        // Ensure app is only used with required permissions (e.g. location dismissed too ofter)
        if (!isPermissionRequested) {
            requestMissingPermissions(cameraSettings)
        } else {
            // Dismiss dialog when user gave permissions while app was paused
            if (!missingPermission(requireContext(), cameraSettings)) {
                permissionDialog?.dismiss() // reset previous to show current permission state
                isPermissionRequested = false
            }
        }
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

    private fun showMissingPermissions(
        cameraMissing: @JvmSuppressWildcards Boolean,
        notificationMissing: @JvmSuppressWildcards Boolean,
        locationMissing: @JvmSuppressWildcards Boolean
    ) {
        if (cameraMissing || notificationMissing || locationMissing) {
            val cameraString = this.getString(de.cyface.app.utils.R.string.camera)
            val notificationString =
                this.getString(de.cyface.app.utils.R.string.notification)
            val locationString = this.getString(de.cyface.app.utils.R.string.location)
            val missing = mutableListOf<String>()
            if (cameraMissing) missing.add(cameraString)
            if (notificationMissing) missing.add(notificationString)
            if (locationMissing) missing.add(locationString)

            permissionDialog = AlertDialog.Builder(requireContext())
                .setTitle(this.getString(de.cyface.app.utils.R.string.missing_permissions))
                .setMessage(
                    this.getString(
                        de.cyface.app.utils.R.string.missing_permissions_info,
                        missing.toCustomString()
                    )
                )
                .setPositiveButton(de.cyface.app.utils.R.string.change_permissions) { dialog, _ ->
                    dialog.dismiss()
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", requireContext().packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }
                .setCancelable(false)
                .show()
        }
    }

    /**
     * @return a string in the format: "item1, item2 and item3"
     */
    private fun List<String>.toCustomString(): String {
        val and = requireContext().getString(de.cyface.app.utils.R.string.and)
        return when (size) {
            1 -> this[0]
            2 -> "${this[0]} $and ${this[1]}"
            else -> "${this.dropLast(1).joinToString(", ")} $and ${this.last()}"
        }
    }

    /**
     * Checks and requests missing permissions.
     *
     * @param cameraSettings The camera preferences to check if camera is enabled.
     */
    private fun requestMissingPermissions(cameraSettings: CameraSettings) {
        // Without notification permissions the capturing notification is not shown on Android >= 13
        // But capturing still works.
        val permissionsMissing = missingPermission(requireContext(), cameraSettings)
        if (permissionsMissing) {
            val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            val cameraEnabled = runBlocking { cameraSettings.cameraEnabledFlow.first() } // FIXME
            if (cameraEnabled) {
                permissions.add(Manifest.permission.CAMERA)
            }
            isPermissionRequested = true
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    /**
     * Checks if permissions are missing.
     *
     * @param context The context to check for.
     * @param cameraSettings The camera preferences to check if camera is enable.
     * @return `true` if permissions are missing.
     */
    private fun missingPermission(context: Context, cameraSettings: CameraSettings): Boolean {
        val cameraMissing = cameraPermissionMissing(context, cameraSettings)
        val notificationMissing = notificationPermissionMissing(context)
        val locationMissing = !granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        return cameraMissing || notificationMissing || locationMissing
    }

    private fun notificationPermissionMissing(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            !granted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            false
        }
    }

    private fun cameraPermissionMissing(
        context: Context,
        cameraSettings: CameraSettings
    ): Boolean {
        val cameraEnabled = runBlocking { cameraSettings.cameraEnabledFlow.first() } // FIXME
        return if (cameraEnabled) !granted(context, Manifest.permission.CAMERA) else false
    }

    /**
     * Determine whether you have been granted a particular permission.
     *
     * @param permission The permission to check.
     * @return `true` if the permission was already granted.
     */
    private fun granted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
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