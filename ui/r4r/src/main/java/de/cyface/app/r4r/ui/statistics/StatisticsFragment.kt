package de.cyface.app.r4r.ui.statistics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import de.cyface.app.r4r.ServiceProvider
import de.cyface.app.r4r.databinding.FragmentStatisticsBinding
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import kotlin.math.max
import kotlin.math.roundToInt

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
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Load measurements
        val measurements = persistenceLayer.loadMeasurements()

        // Statistics calculation
        var totalDistanceKm = 0.0
        var maxDistanceKm = 0.0
        var totalDurationMillis = 0L
        var maxAscend = 0.0
        var totalAscend = 0.0
        measurements.forEach { measurement ->
            // FIXME: Code duplication (UnitConversion)
            val distanceKm = measurement.distance.div(1000.0)
            totalDistanceKm += distanceKm
            maxDistanceKm = max(distanceKm, maxDistanceKm)
            totalDurationMillis += persistenceLayer.loadDuration(measurement.id)
            val ascend = persistenceLayer.loadAscend(measurement.id)
            totalAscend += if (ascend !== null) ascend else 0.0
            maxAscend = if (ascend !== null) max(ascend, maxAscend) else maxAscend
        }

        // UI binding
        val averageDistanceKm = if (measurements.isNotEmpty()) (totalDistanceKm / measurements.size * 100).roundToInt() / 100.0 else 0.0
        binding.distanceView.text = "${(totalDistanceKm * 100).roundToInt() / 100.0} km (Ø $averageDistanceKm km)"
        val averageDuration = if (measurements.isNotEmpty()) duration(totalDurationMillis / measurements.size) else 0L
        binding.durationView.text = "${duration(totalDurationMillis)} (Ø $averageDuration)"
        val averageAscend = if(measurements.isNotEmpty()) (totalAscend / measurements.size).roundToInt() else 0.0
        binding.ascendView.text = "max ${maxAscend.roundToInt()} m (Ø $averageAscend m)"
        // 95 g / km
        // https://de.statista.com/infografik/25742/durchschnittliche-co2-emission-von-pkw-in-deutschland-im-jahr-2020/
        val totalCo2Gram = totalDistanceKm.times(95)
        val maxCo2Gram = maxDistanceKm.times(95)
        val totalCo2Kg = totalCo2Gram.div(1000)
        val maxCo2Kg = maxCo2Gram.div(1000)
        val totalCo2Text = "${(totalCo2Kg * 10).roundToInt() / 10.0} kg"
        val averageCo2Kg = if(measurements.isNotEmpty()) (totalCo2Kg / measurements.size * 10).roundToInt() / 10.0 else 0.0
        val maxCo2Text = "${(maxCo2Kg * 10).roundToInt() / 10.0} kg (Ø $averageCo2Kg kg)"
        binding.totalCo2View.text = totalCo2Text
        binding.maxCo2View.text = maxCo2Text

        return root
    }

    private fun duration(millis: Long): String {
        val durationSeconds = millis.div(1000)
        val durationMinutes = durationSeconds.div(60)
        val durationHours = durationMinutes.div(60)
        val hoursText = if (durationHours > 0) durationHours.toString() + "h " else ""
        val minutesText = if (durationMinutes > 0) (durationMinutes % 60).toString() + "m " else ""
        val secondsText = (durationSeconds % 60).toString() + "s"
        return hoursText + minutesText + secondsText
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}