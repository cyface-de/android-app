package de.cyface.app.r4r.ui.statistics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import de.cyface.app.r4r.R
import de.cyface.app.r4r.ServiceProvider
import de.cyface.app.r4r.databinding.FragmentStatisticsBinding
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import kotlin.math.max

class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var capturingService: CyfaceDataCapturingService

    private lateinit var persistenceLayer: DefaultPersistenceLayer<CapturingPersistenceBehaviour>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (activity is ServiceProvider) {
            capturingService = (activity as ServiceProvider).capturing
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
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Load measurements
        val measurements = persistenceLayer.loadCompletedMeasurements()

        // Statistics calculation
        var totalDistanceKm = 0.0
        var maxDistanceKm = 0.0
        var totalDurationMillis = 0L
        var maxDurationMillis = 0L
        var maxAscend = 0.0
        var totalAscend = 0.0
        measurements.forEach { measurement ->
            // FIXME: Code duplication (UnitConversion)
            val distanceKm = measurement.distance.div(1000.0)
            val durationMillis = persistenceLayer.loadDuration(measurement.id)
            totalDistanceKm += distanceKm
            maxDistanceKm = max(distanceKm, maxDistanceKm)
            maxDurationMillis = max(durationMillis, maxDurationMillis)
            totalDurationMillis += durationMillis
            val ascend = persistenceLayer.loadAscend(measurement.id)
            totalAscend += if (ascend !== null) ascend else 0.0
            maxAscend = if (ascend !== null) max(ascend, maxAscend) else maxAscend
        }

        // UI binding
        val averageDistanceKm = if (measurements.isNotEmpty()) totalDistanceKm / measurements.size else 0.0
        binding.distanceView.text = getString(R.string.distanceKmWithAverage, maxDistanceKm, averageDistanceKm)
        val averageDuration = if (measurements.isNotEmpty()) duration(totalDurationMillis / measurements.size) else 0L
        binding.durationView.text = getString(R.string.durationWithAverage, duration(maxDurationMillis), averageDuration)
        val averageAscend = if (measurements.isNotEmpty()) totalAscend / measurements.size else 0.0
        binding.ascendView.text = getString(R.string.ascendMetersWithAverage, maxAscend, averageAscend)
        val totalCo2Kg = totalDistanceKm.times(95).div(1000)
        val maxCo2Kg = maxDistanceKm.times(95).div(1000)
        val averageCo2Kg = if (measurements.isNotEmpty()) totalCo2Kg / measurements.size else 0.0
        binding.totalCo2View.text = getString(R.string.co2kg, totalCo2Kg)
        binding.maxCo2View.text = getString(R.string.co2kgWithAverage, maxCo2Kg, averageCo2Kg)

        return root
    }

    private fun duration(millis: Long): String {
        val seconds = millis.div(1000)
        val minutes = seconds.div(60)
        val hours = minutes.div(60)
        val hoursText = if (hours > 0) getString(R.string.hours, hours) + " " else ""
        val minutesText = if (minutes > 0) getString(R.string.minutes, minutes % 60) + " " else ""
        val secondsText = getString(R.string.seconds, seconds % 60)
        return hoursText + minutesText + secondsText
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}