package de.cyface.app.r4r.ui.capturing.map

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    private var activityResultLauncher: ActivityResultLauncher<Array<String>>

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

    init {
        // Ensure onMapReadyRunnable is called after permissions are newly granted
        this.activityResultLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val allGranted = result.values.none { !it }
            if (allGranted) {
                map!!.onMapReady()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (activity is ServiceProvider) {
            capturingService = (activity as ServiceProvider).capturingService
            persistenceLayer = capturingService.persistenceLayer
        } else {
            throw RuntimeException("Context does not support the Fragment, implement MyDependencies")
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
        map = Map(binding.mapView, savedInstanceState, onMapReadyRunnable)

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