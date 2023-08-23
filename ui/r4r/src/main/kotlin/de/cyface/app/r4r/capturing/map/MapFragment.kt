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
package de.cyface.app.r4r.capturing.map

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.google.android.gms.maps.MapsInitializer
import de.cyface.app.r4r.capturing.CapturingViewModel
import de.cyface.app.r4r.capturing.CapturingViewModelFactory
import de.cyface.app.r4r.capturing.marker.MarkerFragment
import de.cyface.app.r4r.databinding.FragmentMapBinding
import de.cyface.app.r4r.utils.Constants.TAG
import de.cyface.app.utils.Map
import de.cyface.app.utils.ServiceProvider
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.Track
import de.cyface.utils.AppPreferences

/**
 * The [Fragment] which shows a map to the user.
 *
 * @author Armin Schnabel
 * @version 1.0.0
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
     * The data holder for users preferences.
     */
    private lateinit var preferences: AppPreferences

    /**
     * Shared instance of the [CapturingViewModel] which is used by multiple `Fragments.
     */
    private val capturingViewModel: CapturingViewModel by activityViewModels {
        CapturingViewModelFactory(
            persistence.measurementRepository!!,
            persistence.eventRepository!!,
            AppPreferences(requireContext()).getReportingAccepted()
        )
    }

    /**
     * Can be launched to request permissions.
     *
     * The launcher ensures `map.onMapReady` is called after permissions are newly granted.
     */
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
        // Ignore this async runnable when the fragment is detached from the activity [RFR-629]
        if (this.isDetached) {
            return@Runnable
        }

        map!!.renderMarkers(MarkerFragment.markers())
        observeTracks()

        // Only load track if there is an ongoing measurement
        try {
            val measurement = persistence.loadCurrentlyCapturedMeasurement()
            val tracks: List<Track> = persistence.loadTracks(measurement.id)
            capturingViewModel.setTracks(tracks)
        } catch (e: NoSuchMeasurementException) {
            Log.d(
                TAG, "onMapReadyRunnable: no measurement found, skipping map.renderMeasurement()."
            )
        }
    }

    /**
     * Observes the tracks of the currently captured measurement and renders the tracks on the map.
     */
    private fun observeTracks() {
        val observer = Observer<ArrayList<Track>?> {
            if (it != null) {
                //val events: List<Event> = loadCurrentMeasurementsEvents()
                map!!.render(it, ArrayList()/* events */, false, MarkerFragment.markers())
            } else {
                map!!.clearMap()
                map!!.renderMarkers(MarkerFragment.markers())
            }
        }
        capturingViewModel.tracks.observe(viewLifecycleOwner, observer)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (activity is ServiceProvider) {
            capturing = (activity as ServiceProvider).capturing
            persistence = capturing.persistenceLayer
        } else {
            throw RuntimeException("Context does not support the Fragment, implement ServiceProvider")
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

        preferences = AppPreferences(requireContext())
        map = Map(binding.mapView, savedInstanceState, onMapReadyRunnable, permissionLauncher)

        return root
    }

    override fun onResume() {
        super.onResume()
        map!!.onResume()
    }

    override fun onPause() {
        super.onPause()
        map!!.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /*@Throws(NoSuchMeasurementException::class)
    fun loadCurrentMeasurementsEvents(): List<Event> {
        val (id) = capturing.loadCurrentlyCapturedMeasurement()
        return persistence.loadEvents(id, EventType.MODALITY_TYPE_CHANGE)!!
    }*/
}