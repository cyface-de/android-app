package de.cyface.app.r4r.ui.capturing.map

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.gms.maps.MapsInitializer
import de.cyface.app.r4r.ServiceProvider
import de.cyface.app.r4r.databinding.FragmentMapBinding
import de.cyface.app.r4r.ui.capturing.CapturingViewModel
import de.cyface.app.r4r.ui.capturing.CapturingViewModelFactory
import de.cyface.app.r4r.utils.Constants.ACCEPTED_REPORTING_KEY
import de.cyface.app.r4r.utils.Constants.TAG
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.Event
import de.cyface.persistence.model.EventType
import de.cyface.persistence.model.Track
import de.cyface.utils.CursorIsNullException
import io.sentry.Sentry

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

    private val capturingViewModel: CapturingViewModel by viewModels {
        CapturingViewModelFactory(persistenceLayer.measurementRepository!!)
    }

    /**
     * The `Runnable` triggered when the `Map` is loaded and ready.
     */
    private val onMapReadyRunnable = Runnable {
        val currentMeasurementsTracks: List<Track> =
            capturingViewModel.currentMeasurementsTracks ?: return@Runnable
        val currentMeasurementsEvents: List<Event>
        try {
            currentMeasurementsEvents = loadCurrentMeasurementsEvents()
            map!!.renderMeasurement(currentMeasurementsTracks, currentMeasurementsEvents, false)
        } catch (e: NoSuchMeasurementException) {
            val isReportingEnabled: Boolean =
                preferences!!.getBoolean(ACCEPTED_REPORTING_KEY, false)
            if (isReportingEnabled) {
                Sentry.captureException(e)
            }
            Log.w(
                TAG,
                "onMapReadyRunnable failed to loadCurrentMeasurementsEvents. Thus, map.renderMeasurement() is not executed. This should only happen when the capturing already stopped."
            )
        } catch (e: CursorIsNullException) {
            val isReportingEnabled: Boolean =
                preferences!!.getBoolean(ACCEPTED_REPORTING_KEY, false)
            if (isReportingEnabled) {
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

        // FIXME: see CapturingFragment how to initialize the ViewModel with a repository
        //val mapViewModel = ViewModelProvider(this)[MapViewModel::class.java]
        /*val textView: TextView = binding.textView9
        capturingViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }*/
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

    @Throws(CursorIsNullException::class, NoSuchMeasurementException::class)
    fun loadCurrentMeasurementsEvents(): List<Event> {
        val (id) = capturingService.loadCurrentlyCapturedMeasurement()
        return persistenceLayer.loadEvents(id, EventType.MODALITY_TYPE_CHANGE)!!
    }
}