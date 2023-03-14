package de.cyface.app.r4r.ui.capturing.speed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import de.cyface.app.r4r.R
import de.cyface.app.r4r.ServiceProvider
import de.cyface.app.r4r.databinding.FragmentSpeedBinding
import de.cyface.app.r4r.ui.capturing.CapturingViewModel
import de.cyface.app.r4r.ui.capturing.CapturingViewModelFactory
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.DataCapturingListener
import de.cyface.datacapturing.model.CapturedData
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.datacapturing.ui.Reason
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.utils.DiskConsumption
import kotlin.math.roundToInt

class SpeedFragment : Fragment(), DataCapturingListener {

    private var _binding: FragmentSpeedBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private lateinit var capturingService: CyfaceDataCapturingService

    private lateinit var persistenceLayer: DefaultPersistenceLayer<CapturingPersistenceBehaviour>

    private val capturingViewModel: CapturingViewModel by viewModels {
        CapturingViewModelFactory(persistenceLayer.measurementRepository!!)
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

        val speedView: TextView = binding.liveSpeedView
        capturingViewModel.location.observe(viewLifecycleOwner) {
            val speedMps = it?.speed
            val speedKmPh = speedMps?.times(3.6)?.roundToInt()
            val speedText = if (speedKmPh == null) null else "$speedKmPh km/h"
            speedView.text = speedText ?: getString(R.string.capturing_inactive)
        }
        return root
    }

    override fun onResume() {
        super.onResume()
        capturingService.addDataCapturingListener(this)
    }

    override fun onPause() {
        super.onPause()
        capturingService.removeDataCapturingListener(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onFixAcquired() {
        // Nothing to do
    }

    override fun onFixLost() {
        // Nothing to do
    }

    override fun onNewGeoLocationAcquired(position: ParcelableGeoLocation) {
        capturingViewModel.setLocation(position)
    }

    override fun onNewSensorDataAcquired(data: CapturedData?) {
        // Nothing to do
    }

    override fun onLowDiskSpace(allocation: DiskConsumption?) {
        // Nothing to do here - handled by [CapturingEventHandler]
    }

    override fun onSynchronizationSuccessful() {
        // Nothing to do here
    }

    override fun onErrorState(e: Exception?) {
        throw java.lang.IllegalStateException(e)
    }

    override fun onRequiresPermission(permission: String?, reason: Reason?): Boolean {
        return false
    }

    override fun onCapturingStopped() {
        // Disabled on Android 13+ for workaround, see `stop/pauseCapturing()` [RFR-246]
        //if (Build.VERSION.SDK_INT < 33) {
        capturingViewModel.setLocation(null)
        //}
    }
}