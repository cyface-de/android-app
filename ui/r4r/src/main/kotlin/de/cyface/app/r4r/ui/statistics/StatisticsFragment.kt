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
package de.cyface.app.r4r.ui.statistics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import de.cyface.app.utils.ServiceProvider
import de.cyface.app.r4r.databinding.FragmentStatisticsBinding
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import kotlin.math.max

/**
 * The [Fragment] which shows the statistics of all finished measurements.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.2.0
 */
class StatisticsFragment : Fragment() {

    /**
     * This property is only valid between onCreateView and onDestroyView.
     */
    private var _binding: FragmentStatisticsBinding? = null

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
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val measurements = persistence.loadCompletedMeasurements()

        // Statistics calculation
        var totalDistanceKm = 0.0
        var maxDistanceKm = 0.0
        var totalDurationMillis = 0L
        var maxDurationMillis = 0L
        var maxAscend = 0.0
        var totalAscend = 0.0
        measurements.forEach { measurement ->
            val distanceKm = measurement.distance.div(1000.0)
            val durationMillis = persistence.loadDuration(measurement.id)
            totalDistanceKm += distanceKm
            maxDistanceKm = max(distanceKm, maxDistanceKm)
            maxDurationMillis = max(durationMillis, maxDurationMillis)
            totalDurationMillis += durationMillis
            val ascend = persistence.loadAscend(measurement.id)
            totalAscend += if (ascend !== null) ascend else 0.0
            maxAscend = if (ascend !== null) max(ascend, maxAscend) else maxAscend
        }

        // UI binding
        val averageDistanceKm =
            if (measurements.isNotEmpty()) totalDistanceKm / measurements.size else 0.0
        binding.distanceView.text =
            getString(de.cyface.app.utils.R.string.distanceKmWithAverage, maxDistanceKm, averageDistanceKm)
        val averageDuration =
            if (measurements.isNotEmpty()) duration(totalDurationMillis / measurements.size) else 0L
        binding.durationView.text =
            getString(de.cyface.app.utils.R.string.durationWithAverage, duration(maxDurationMillis), averageDuration)
        val averageAscend = if (measurements.isNotEmpty()) totalAscend / measurements.size else 0.0
        binding.ascendView.text =
            getString(de.cyface.app.utils.R.string.ascendMetersWithAverage, maxAscend, averageAscend)
        val totalCo2Kg = totalDistanceKm.times(95).div(1000)
        val maxCo2Kg = maxDistanceKm.times(95).div(1000)
        val averageCo2Kg = if (measurements.isNotEmpty()) totalCo2Kg / measurements.size else 0.0
        binding.totalCo2View.text = getString(de.cyface.app.utils.R.string.co2kg, totalCo2Kg)
        binding.maxCo2View.text = getString(de.cyface.app.utils.R.string.co2kgWithAverage, maxCo2Kg, averageCo2Kg)

        return root
    }

    /**
     * Converts duration from milliseconds to a [String] in the format `[>0 h] [>0 m] >=0 s`.
     *
     * @param millis The milliseconds to convert.
     */
    private fun duration(millis: Long): String {
        val seconds = millis.div(1000)
        val minutes = seconds.div(60)
        val hours = minutes.div(60)
        val hoursText = if (hours > 0) getString(de.cyface.app.utils.R.string.hours, hours) + " " else ""
        val minutesText = if (minutes > 0) getString(de.cyface.app.utils.R.string.minutes, minutes % 60) + " " else ""
        val secondsText = getString(de.cyface.app.utils.R.string.seconds, seconds % 60)
        return hoursText + minutesText + secondsText
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}