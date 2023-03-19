package de.cyface.app.r4r.ui.capturing.speed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import de.cyface.app.r4r.R
import de.cyface.app.r4r.ServiceProvider
import de.cyface.app.r4r.databinding.FragmentSpeedBinding
import de.cyface.app.r4r.ui.capturing.CapturingViewModel
import de.cyface.app.r4r.ui.capturing.CapturingViewModelFactory
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import kotlin.math.roundToInt

class SpeedFragment : Fragment() {

    private var _binding: FragmentSpeedBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private lateinit var capturingService: CyfaceDataCapturingService

    private lateinit var persistenceLayer: DefaultPersistenceLayer<CapturingPersistenceBehaviour>

    // Get shared `ViewModel` instance from Activity
    private val capturingViewModel: CapturingViewModel by activityViewModels {
        CapturingViewModelFactory(
            persistenceLayer.measurementRepository!!,
            persistenceLayer.eventRepository!!
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (activity is ServiceProvider) {
            capturingService = (activity as ServiceProvider).capturingService
            persistenceLayer = capturingService.persistenceLayer
        } else {
            throw RuntimeException("Context doesn't support the Fragment, implement `ServiceProvider`")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpeedBinding.inflate(inflater, container, false)
        val root: View = binding.root

        capturingViewModel.location.observe(viewLifecycleOwner) {
            val speedMps = it?.speed
            val speedKmPh = speedMps?.times(3.6)?.roundToInt()
            val speedText = if (speedKmPh == null) null else "$speedKmPh km/h"
            binding.liveSpeedView.text = speedText ?: getString(R.string.capturing_inactive)
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}