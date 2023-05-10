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

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import de.cyface.app.utils.R
import de.cyface.app.utils.ServiceProvider
import de.cyface.app.utils.databinding.FragmentTripsBinding
import de.cyface.datacapturing.CyfaceDataCapturingService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.Double.min
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

/**
 * The [Fragment] which shows all finished measurements to the user.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.2.0
 */
class TripsFragment : Fragment() {

    /**
     * This property is only valid between onCreateView and onDestroyView.
     */
    private var _binding: FragmentTripsBinding? = null

    /**
     * The generated class which holds all bindings from the layout file.
     */
    private val binding get() = _binding!!

    /**
     * The capturing service object which controls data capturing and synchronization.
     */
    private lateinit var capturing: CyfaceDataCapturingService

    /**
     * The data holder for the user's preferences.
     */
    private lateinit var preferences: SharedPreferences

    /**
     * Tracker for selected items in the list.
     */
    private var tracker: androidx.recyclerview.selection.SelectionTracker<Long>? = null

    /**
     * The [TripsViewModel] which holds the UI data.
     */
    private val tripsViewModel: TripsViewModel by viewModels {
        TripsViewModelFactory(capturing.persistenceLayer.measurementRepository!!)
    }

    // This launcher must be launched to request permissions
    private var exportPermissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result.isNotEmpty()) {
                val allGranted = result.values.none { !it }
                if (allGranted) {
                    GlobalScope.launch { Exporter(requireContext()).export() }
                } else {
                    Toast.makeText(
                        context,
                        requireContext().getString(de.cyface.app.utils.R.string.export_data_no_permission),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) tracker?.onRestoreInstanceState(savedInstanceState)

        if (activity is ServiceProvider) {
            capturing = (activity as ServiceProvider).capturing
        } else {
            throw RuntimeException("Context does not support the Fragment, implement ServiceProvider")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTripsBinding.inflate(inflater, container, false)
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context)

        // Bind the UI element to the adapter
        val tripsList = binding.tripsList
        val adapter = TripListAdapter()
        tripsList.adapter = adapter
        tripsList.layoutManager = LinearLayoutManager(context)

        // Support list selection
        val tracker = androidx.recyclerview.selection.SelectionTracker.Builder(
            "tripsListSelection",
            tripsList,
            androidx.recyclerview.selection.StableIdKeyProvider(tripsList),
            TripListAdapter.ItemsDetailsLookup(tripsList),
            androidx.recyclerview.selection.StorageStrategy.createLongStorage()
        ).withSelectionPredicate(
            androidx.recyclerview.selection.SelectionPredicates.createSelectAnything() // allows multiple choice
        ).build()
        adapter.tracker = tracker

        // Add divider between list items
        val divider = DividerItemDecoration(
            context,
            (tripsList.layoutManager as LinearLayoutManager).orientation
        )
        tripsList.addItemDecoration(divider)

        // Update adapters with the updates from the ViewModel
        val showAchievements = requireContext().packageName.equals("de.cyface.app.r4r")
        tripsViewModel.measurements.observe(viewLifecycleOwner) { measurements ->
            measurements?.let { adapter.submitList(it) }

            // Achievements
            if (showAchievements) {
                binding.achievements.visibility = VISIBLE

                // Calculate achievements progress
                var totalDistanceKm = 0.0
                measurements.forEach { measurement ->
                    val distanceKm = measurement.distance.div(1000.0)
                    totalDistanceKm += distanceKm
                }
                val distanceGoalKm = 1.0 // FIXME
                val progress = min(totalDistanceKm / distanceGoalKm * 100.0, 100.0)
                if (progress < 100) {
                    val missingKm = distanceGoalKm - totalDistanceKm
                    binding.achievementsProgressContent.visibility = VISIBLE
                    binding.achievementsProgressContent.text = getString(R.string.achievements_progress, missingKm)
                    binding.achievementsProgress.visibility = VISIBLE
                    binding.achievementsProgress.progress = progress.roundToInt()
                    binding.achievementsUnlockedContent.visibility = GONE
                    binding.unlockAchievement.visibility = GONE
                } else {
                    // FIXME: check if voucher received
                    binding.achievementsProgressContent.visibility = GONE
                    binding.achievementsProgress.visibility = GONE
                    binding.achievementsUnlockedContent.visibility = VISIBLE
                    binding.unlockAchievement.visibility = VISIBLE
                }
            }
        }

        // Add items to menu (top right)
        requireActivity().addMenuProvider(
            MenuProvider(
                capturing,
                preferences,
                adapter,
                exportPermissionLauncher,
                WeakReference<Context>(requireContext().applicationContext)
            ), viewLifecycleOwner, Lifecycle.State.RESUMED
        )

        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        tracker?.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}