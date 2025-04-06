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
package de.cyface.app.utils.trips

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import de.cyface.app.utils.R
import de.cyface.app.utils.ServiceProvider
import de.cyface.app.utils.databinding.FragmentTripsDetailsBinding
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.strategy.DefaultLocationCleaning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * The [Fragment] which shows details about a single, finished measurement.
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 3.2.0
 */
class DetailsFragment : Fragment() {

    /**
     * This property is only valid between onCreateView and onDestroyView.
     */
    private var _binding: FragmentTripsDetailsBinding? = null

    /**
     * The generated class which holds all bindings from the layout file.
     */
    private val binding get() = _binding!!

    /**
     * The capturing service object which controls data capturing and synchronization.
     */
    private lateinit var capturing: CyfaceDataCapturingService

    /**
     * An implementation of the persistence layer which caches some data during capturing.
     */
    private lateinit var persistence: DefaultPersistenceLayer<CapturingPersistenceBehaviour>

    // For some reasons the Args class is not generated by safeargs
    //private val args: `DetailsFragmentArgs` by `navArgs()` so we call `requireArguments()` below.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (activity is ServiceProvider) {
            capturing = (activity as ServiceProvider).capturing
            persistence = capturing.persistenceLayer
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

        lifecycleScope.launch {

            val measurement =
                withContext(Dispatchers.IO) { persistence.loadMeasurement(measurementId) }

            // Statistics
            val distanceKm = measurement?.distance?.div(1000.0)
            binding.distanceView.text =
                if (distanceKm == null) "" else getString(R.string.distanceKm, distanceKm)

            val co2Kg = distanceKm?.times(95)?.div(1000)
            binding.co2View.text = if (co2Kg == null) "" else getString(R.string.co2kg, co2Kg)

            val millis = if (measurement == null) null else withContext(Dispatchers.IO) {
                persistence.loadDuration(measurement.id)
            }
            val seconds = millis?.div(1000)
            val minutes = seconds?.div(60)
            val hours = minutes?.div(60)
            val hoursText =
                if (hours == null || hours == 0L) "" else getString(R.string.hours, hours) + " "
            val minutesText = if (minutes == null || minutes == 0L) "" else getString(
                R.string.minutes,
                minutes % 60
            ) + " "
            val secondsText = if (seconds == null) "" else getString(R.string.seconds, seconds % 60)
            val durationText = hoursText + minutesText + secondsText
            binding.durationView.text = durationText

            val ascendText: String?
            val averageSpeedKmh = withContext(Dispatchers.IO) {
                persistence.loadAverageSpeed(
                    measurementId,
                    DefaultLocationCleaning()
                ) * 3.6
            }

            val ongoingCapturing = measurement != null
            val ascend = if (ongoingCapturing) withContext(Dispatchers.IO) {
                persistence.loadAscend(measurementId)
            } else null
            ascendText = getString(R.string.ascendMeters, ascend ?: 0.0)

            val maxSpeedMps = withContext(Dispatchers.IO) {
                persistence.loadMaxSpeed(
                    measurementId,
                    DefaultLocationCleaning()
                )
            }
            val maxSpeedKmPh = maxSpeedMps.times(3.6)
            binding.speedView.text =
                getString(R.string.speedKphWithAverage, maxSpeedKmPh, averageSpeedKmh)
            binding.ascendView.text = ascendText

            // Chart
            val chart = root.findViewById(R.id.chart) as LineChart
            val altitudes = withContext(Dispatchers.IO) { persistence.loadAltitudes(measurementId) }
            if (altitudes.isNullOrEmpty()) {
                binding.elevationProfileTitle.text = getString(R.string.elevation_profile_no_data)
                chart.visibility = GONE
            } else {
                // We could also show the relative elevation profile (starting at elevation 0)
                val allEntries = mutableListOf<List<Entry>>()
                var x = 1
                val values = altitudes.sumOf { trackAltitudes -> trackAltitudes.count() }
                altitudes.forEach { trackAltitudes ->
                    val entries = mutableListOf<Entry>()
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
                val datasets: List<LineDataSet> = mutableListOf()
                allEntries.forEach {
                    val dataSet = LineDataSet(it, "sub-track")
                    dataSet.color = resources.getColor(R.color.text)
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

                chart.invalidate() // ensures chart is re-rendered else "no chart data" until touch
            }
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}