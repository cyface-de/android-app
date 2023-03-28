package de.cyface.app.r4r.ui.capturing.map

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.maps.MapsInitializer
import de.cyface.app.r4r.ServiceProvider
import de.cyface.app.r4r.databinding.FragmentMapBinding
import de.cyface.app.r4r.ui.capturing.CapturingViewModel
import de.cyface.app.r4r.ui.capturing.CapturingViewModelFactory
import de.cyface.app.r4r.utils.Constants.TAG
import de.cyface.app.utils.Map
import de.cyface.app.utils.SharedConstants
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.Event
import de.cyface.persistence.model.EventType
import de.cyface.persistence.model.Track

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    /**
     * The `Map` used to visualize the ongoing capturing.
     */
    private var map: Map? = null

    private lateinit var capturingService: CyfaceDataCapturingService

    private lateinit var persistenceLayer: DefaultPersistenceLayer<CapturingPersistenceBehaviour>

    private var preferences: SharedPreferences? = null

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

    private val capturingViewModel: CapturingViewModel by activityViewModels {
        CapturingViewModelFactory(
            persistenceLayer.measurementRepository!!,
            persistenceLayer.eventRepository!!
        )
    }

    /**
     * The `Runnable` triggered when the `Map` is loaded and ready.
     */
    private val onMapReadyRunnable = Runnable {
        try {
            val measurement = persistenceLayer.loadCurrentlyCapturedMeasurement()
            val tracks: List<Track> = persistenceLayer.loadTracks(measurement.id)
            capturingViewModel.setTracks(tracks as ArrayList<Track>) // FIXME: we did not do this before here
            //val events: List<Event> = loadCurrentMeasurementsEvents()
            //map!!.renderMeasurement(tracks, arrayListOf()/* events FIXME*/, false)

            capturingViewModel.tracks.observe(viewLifecycleOwner) {
                if (it != null) {
                    map!!.renderMeasurement(it, ArrayList()/*FIXME*/, false)
                } else {
                    map!!.clearMap()
                }
            }
        } catch (e: NoSuchMeasurementException) {
            Log.d(
                TAG, "onMapReadyRunnable: no measurement found, skipping map.renderMeasurement()."
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (activity is ServiceProvider) {
            capturingService = (activity as ServiceProvider).capturingService
            persistenceLayer = capturingService.persistenceLayer
        } else {
            throw RuntimeException("Context does not support the Fragment, implement ServiceProvider")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        @Suppress("SpellCheckingInspection")
        // Using the new map renderer, which must be called before MapView is initialized:
        // https://developers.google.com/maps/documentation/android-sdk/renderer
        MapsInitializer.initialize(requireContext(), MapsInitializer.Renderer.LATEST) {}

        _binding = FragmentMapBinding.inflate(inflater, container, false)
        val root: View = binding.root

        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
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

    @Throws(NoSuchMeasurementException::class)
    fun loadCurrentMeasurementsEvents(): List<Event> {
        val (id) = capturingService.loadCurrentlyCapturedMeasurement()
        return persistenceLayer.loadEvents(id, EventType.MODALITY_TYPE_CHANGE)!!
    }
}