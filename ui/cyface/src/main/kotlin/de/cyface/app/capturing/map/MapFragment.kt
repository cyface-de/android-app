/*
 * Copyright 2023-2025 Cyface GmbH
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
package de.cyface.app.capturing.map

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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.MapsInitializer
import de.cyface.app.capturing.CapturingViewModel
import de.cyface.app.capturing.CapturingViewModelFactory
import de.cyface.app.databinding.FragmentMapBinding
import de.cyface.app.utils.Constants.TAG
import de.cyface.app.utils.Map
import de.cyface.app.utils.ServiceProvider
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.Track
import de.cyface.utils.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * The [Fragment] which shows a map to the user.
 *
 * @author Armin Schnabel
 * @version 1.0.2
 * @since 3.2.0
 */
class MapFragment : Fragment() {

    /**
     * This property is only valid between onCreateView and onDestroyView.
     */
    private var _binding: FragmentMapBinding? = null

    /**
     * The generated class which holds all bindings from the layout file.
     */
    private val binding get() = _binding!!

    /**
     * The `Map` used to visualize the ongoing capturing.
     */
    private var map: Map? = null

    /**
     * The capturing service object which controls data capturing and synchronization.
     */
    private lateinit var capturing: CyfaceDataCapturingService

    /**
     * An implementation of the persistence layer which caches some data during capturing.
     */
    private lateinit var persistence: DefaultPersistenceLayer<CapturingPersistenceBehaviour>

    /**
     * The settings used by both, UIs and libraries.
     */
    private lateinit var appSettings: AppSettings

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
     * Shared instance of the [CapturingViewModel] which is used by multiple `Fragments.
     */
    private val capturingViewModel: CapturingViewModel by activityViewModels {
        // Synchronously to ensure viewModel is available when needed.
        val reportErrors = runBlocking { appSettings.reportErrorsFlow.first() }
        CapturingViewModelFactory(
            persistence.measurementRepository!!,
            persistence.eventRepository!!,
            reportErrors
        )
    }

    /**
     * The `Runnable` triggered when the `Map` is loaded and ready.
     */
    private val onMapReadyRunnable = Runnable {
        // Ignore this async runnable when the fragment is detached from the activity [RFR-629]
        if (this.isDetached) {
            return@Runnable
        }

        map!!.renderMarkers(emptyList() /* TODO */)
        observeTracks()

        // Only load track if there is an ongoing measurement
        try {
            lifecycleScope.launch {
                val measurement = withContext(Dispatchers.IO) { persistence.loadCurrentlyCapturedMeasurement() }
                val tracks = withContext(Dispatchers.IO) { persistence.loadTracks(measurement.id) }
                capturingViewModel.setTracks(tracks.toMutableList())
            }
        } catch (e: NoSuchMeasurementException) {
            Log.d(
                TAG, "onMapReadyRunnable: no measurement found, skipping map.renderMeasurement().",
                e,
            )
        }
    }

    /**
     * Observes the tracks of the currently captured measurement and renders the tracks on the map.
     */
    private fun observeTracks() {
        val observer = Observer<MutableList<Track>?> {
            if (it != null) {
                //val events: List<Event> = loadCurrentMeasurementsEvents()
                map!!.render(it, mutableListOf()/* events */, false, emptyList() /* TODO */)
            } else {
                map!!.clearMap()
                map!!.renderMarkers(emptyList() /* TODO */)
            }
        }
        capturingViewModel.tracks.observe(viewLifecycleOwner, observer)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (activity is ServiceProvider) {
            capturing = (activity as ServiceProvider).capturing
            appSettings = (activity as ServiceProvider).appSettings
            persistence = capturing.persistenceLayer
        } else {
            error("Context does not support the Fragment, implement ServiceProvider")
        }

        // Location permissions are requested by CapturingFragment/Map to react to results.
        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                //val cameraMissing = cameraPermissionMissing(requireContext()/*, cameraPreferences*/)
                val notificationMissing = notificationPermissionMissing(requireContext())
                val locationMissing =
                    !granted(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                val nonMissing = /*!cameraMissing &&*/ !notificationMissing && !locationMissing
                if (nonMissing) {
                    isPermissionRequested = false
                    // Ensure onMapReadyRunnable is called after permissions are newly granted.
                    // Only if the map already called it's onMapReady
                    if (map!!.googleMap != null) {
                        map!!.onMapReady()
                    }
                } else {
                    showMissingPermissions(/*cameraMissing,*/ notificationMissing, locationMissing)
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Using the new map renderer, which must be called before MapView is initialized:
        // https://developers.google.com/maps/documentation/android-sdk/renderer
        MapsInitializer.initialize(requireContext(), MapsInitializer.Renderer.LATEST) {}

        _binding = FragmentMapBinding.inflate(inflater, container, false)
        val root: View = binding.root

        map = Map(binding.mapView, savedInstanceState, onMapReadyRunnable, viewLifecycleOwner, permissionLauncher)

        return root
    }

    override fun onResume() {
        super.onResume()
        map!!.onResume()

        // Ensure app is only used with required permissions (e.g. location dismissed too ofter)
        if (!isPermissionRequested) {
            requestMissingPermissions(/*cameraPreferences*/)
        } else {
            // Dismiss dialog when user gave permissions while app was paused
            if (!missingPermission(requireContext()/*, cameraPreferences*/)) {
                permissionDialog?.dismiss() // reset previous to show current permission state
                isPermissionRequested = false
            }
        }
    }

    override fun onPause() {
        super.onPause()
        map!!.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showMissingPermissions(
        //cameraMissing: @JvmSuppressWildcards Boolean,
        notificationMissing: @JvmSuppressWildcards Boolean,
        locationMissing: @JvmSuppressWildcards Boolean
    ) {
        if (/*cameraMissing ||*/ notificationMissing || locationMissing) {
            val cameraString = this.getString(de.cyface.app.utils.R.string.camera)
            val notificationString =
                this.getString(de.cyface.app.utils.R.string.notification)
            val locationString = this.getString(de.cyface.app.utils.R.string.location)
            val missing = mutableListOf<String>()
            //if (cameraMissing) missing.add(cameraString)
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
     * @ param cameraPreferences The camera preferences to check if camera is enabled.
     */
    private fun requestMissingPermissions(/*cameraPreferences: CameraPreferences*/) {
        // Without notification permissions the capturing notification is not shown on Android >= 13
        // But capturing still works.
        val permissionsMissing = missingPermission(requireContext()/*, cameraPreferences*/)
        if (permissionsMissing) {
            val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            /*val cameraEnabled = cameraPreferences.getCameraEnabled()
            if (cameraEnabled) {
                permissions.add(Manifest.permission.CAMERA)
            }*/
            isPermissionRequested = true
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    /**
     * Checks if permissions are missing.
     *
     * @param context The context to check for.
     * @ param cameraPreferences The camera preferences to check if camera is enable.
     * @return `true` if permissions are missing.
     */
    private fun missingPermission(context: Context/*, cameraPreferences: CameraPreferences*/): Boolean {
        //val cameraMissing = cameraPermissionMissing(context, cameraPreferences)
        val notificationMissing = notificationPermissionMissing(context)
        val locationMissing = !granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        return /*cameraMissing ||*/ notificationMissing || locationMissing
    }

    private fun notificationPermissionMissing(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            !granted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            false
        }
    }

    /*private fun cameraPermissionMissing(
        context: Context,
        cameraPreferences: CameraPreferences
    ): Boolean {
        val cameraEnabled = cameraPreferences.getCameraEnabled()
        return if (cameraEnabled) !granted(context, Manifest.permission.CAMERA) else false
    }*/

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

    /*@Throws(NoSuchMeasurementException::class)
    fun loadCurrentMeasurementsEvents(): List<Event> {
        val (id) = capturing.loadCurrentlyCapturedMeasurement()
        return persistence.loadEvents(id, EventType.MODALITY_TYPE_CHANGE)!!
    }*/
}