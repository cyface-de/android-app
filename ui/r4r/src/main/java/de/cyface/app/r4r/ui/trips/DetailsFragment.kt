package de.cyface.app.r4r.ui.trips

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import de.cyface.app.r4r.R
import de.cyface.app.r4r.ServiceProvider
import de.cyface.app.r4r.databinding.FragmentTripsDetailsBinding
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.strategy.DefaultLocationCleaning
import kotlin.math.round
import kotlin.math.roundToInt


class DetailsFragment : Fragment() {

    private var _binding: FragmentTripsDetailsBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private lateinit var capturingService: CyfaceDataCapturingService

    private lateinit var persistenceLayer: DefaultPersistenceLayer<CapturingPersistenceBehaviour>

    // For some reasons the Args class is not generated by safeargs
    //private val args: DetailsFragmentArgs by navArgs()

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
        _binding = FragmentTripsDetailsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Get measurementId from calling fragment
        val measurementId = requireArguments().getLong("measurementId")
        binding.tripTitle.text = requireContext().getString(R.string.trip_id, measurementId)

        // Load measurement
        val measurement = persistenceLayer.loadMeasurement(measurementId)

        // FIXME: Code duplication
        val distanceMeter = measurement?.distance
        val distanceKm = distanceMeter?.div(1000.0)
        val distanceText =
            if (distanceKm == null) null else "${(distanceKm * 100).roundToInt() / 100.0} km"
        binding.distanceView.text = distanceText ?: ""

        val durationMillis =
            if (measurement == null) null else persistenceLayer.loadDuration(measurementId)
        val durationSeconds = durationMillis?.div(1000)
        val durationMinutes = durationSeconds?.div(60)
        val durationHours = durationMinutes?.div(60)
        val hoursText =
            if (durationHours == null) null else if (durationHours > 0) durationHours.toString() + "h " else ""
        val minutesText =
            if (durationMinutes == null) null else if (durationMinutes > 0) (durationMinutes % 60).toString() + "m " else ""
        val secondsText =
            if (durationSeconds == null) null else (durationSeconds % 60).toString() + "s"
        val durationText =
            if (hoursText == null) null else hoursText + minutesText + secondsText
        binding.durationView.text = durationText ?: ""

        // 95 g / km
        // https://de.statista.com/infografik/25742/durchschnittliche-co2-emission-von-pkw-in-deutschland-im-jahr-2020/
        val co2Gram = distanceKm?.times(95)
        val co2Kg = co2Gram?.div(1000)
        val co2Text = if (co2Kg == null) null else "${(co2Kg * 10).roundToInt() / 10.0} kg"
        binding.co2View.text = co2Text ?: ""

        val averageSpeedText: String?
        val ascendText: String?
        // FIXME: we only need to cache the current measurement id and then observe that measurement
        val averageSpeedKmh =
            persistenceLayer.loadAverageSpeed(
                measurementId,
                DefaultLocationCleaning()
            ) * 3.6
        averageSpeedText = averageSpeedKmh.roundToInt().toString() + " km/h"

        val ascend = if (measurement != null) persistenceLayer.loadAscend(measurementId) else null
        ascendText = if (ascend == null) null else "+ ${ascend.roundToInt()} m"

        val maxSpeedMps = persistenceLayer.loadMaxSpeed(
            measurementId,
            DefaultLocationCleaning()
        )
        val maxSpeedKmPh = maxSpeedMps.times(3.6).roundToInt()
        val speedText = "$maxSpeedKmPh km/h (Ø $averageSpeedText)"
        binding.speedView.text = speedText
        binding.ascendView.text = ascendText ?: "+ 0 m"

        // Chart
        val chart = root.findViewById(R.id.chart) as LineChart
        val altitudes = persistenceLayer.loadAltitudes(measurementId)
        if (altitudes == null || altitudes.isEmpty()) {
            binding.elevationProfileTitle.text = getString(R.string.elevation_profile_no_data)
            chart.visibility = GONE
        } else {
            val allEntries = ArrayList<List<Entry>>()
            var x = 1
            val values = altitudes.sumOf { trackAltitudes -> trackAltitudes.count() }
            altitudes.forEach { trackAltitudes ->
                val entries = ArrayList<Entry>()
                trackAltitudes.forEach {
                    entries.add(Entry(x.toFloat(), it.toFloat()))
                    x++
                }
                x += round(values * 0.05).roundToInt() // 5 % gap between sub-tracks
                if (entries.isNotEmpty()) {
                    allEntries.add(entries)
                }
            }
            val textColor = resources.getColor(R.color.text)
            val resources = requireContext().resources
            val datasets: List<LineDataSet> = ArrayList()
            allEntries.forEach {
                val dataSet = LineDataSet(it, "sub-track")
                dataSet.color = resources.getColor(R.color.green_700)
                dataSet.setDrawCircles(false)
                (datasets as ArrayList<LineDataSet>).add(dataSet)
            }
            val data = LineData(datasets)
            data.setValueTextColor(textColor)
            chart.data = data
            chart.axisLeft.textColor = textColor
            chart.axisRight.textColor = textColor
            chart.description.text = resources.getString(R.string.chart_label)
            chart.description.textColor = textColor
            chart.legend.isEnabled = false
            chart.xAxis.isEnabled = false
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}