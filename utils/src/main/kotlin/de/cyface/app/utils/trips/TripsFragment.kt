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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import de.cyface.app.utils.R
import de.cyface.app.utils.ServiceProvider
import de.cyface.app.utils.databinding.FragmentTripsBinding
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.persistence.model.Measurement
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.Double.min
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Locale
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

        // Achievements
        val showAchievements = requireContext().packageName.equals("de.cyface.app.r4r")
        // Check voucher availability
        Handler().postDelayed({
            val availableVouchers = 100 // FIXME: add actual request + error "Keine Verbindung zum Server"
            if (showAchievements && availableVouchers > 0) {
                // Can be null when switching tab before response returns
                _binding?.achievementsVouchersLeft?.text =
                    getString(R.string.voucher_left, availableVouchers)
                _binding?.achievements?.visibility = VISIBLE
            }
        }, 1000)

        // Update adapters with the updates from the ViewModel
        tripsViewModel.measurements.observe(viewLifecycleOwner) { measurements ->
            measurements?.let { adapter.submitList(it) }

            // Achievements
            if (showAchievements) {
                val totalDistanceKm = totalDistanceKm(measurements)
                val distanceGoalKm = 2.0 // FIXME
                val progress = min(totalDistanceKm / distanceGoalKm * 100.0, 100.0)

                if (progress < 100) {
                    showProgress(progress, distanceGoalKm, totalDistanceKm)
                } else {
                    // Show request voucher button
                    binding.achievementsProgress.visibility = GONE
                    binding.achievementsReceived.visibility = GONE
                    binding.achievementsUnlocked.visibility = VISIBLE
                    binding.achievementsUnlockedButton.setOnClickListener {
                        showVoucher()
                    }
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

    private fun showProgress(
        progress: Double,
        @Suppress("SameParameterValue") distanceGoalKm: Double,
        totalDistanceKm: Double
    ) {
        binding.achievementsUnlocked.visibility = GONE
        binding.achievementsReceived.visibility = GONE
        binding.achievementsProgress.visibility = VISIBLE
        val missingKm = distanceGoalKm - totalDistanceKm
        binding.achievementsProgressContent.text =
            getString(R.string.achievements_progress, missingKm)
        binding.achievementsProgressBar.progress = progress.roundToInt()
    }

    private fun totalDistanceKm(measurements: List<Measurement>): Double {
        var totalDistanceKm = 0.0
        measurements.forEach { measurement ->
            val distanceKm = measurement.distance.div(1000.0)
            totalDistanceKm += distanceKm
        }
        return totalDistanceKm
    }

    private fun showVoucher() {
        // FIXME: request voucher from API
        val voucherCode = "0123456789"
        val until = "2024-05-31T23:59:59Z"
        // FIXME: Handle 204 - no content (when the last voucher just got assigned)

        binding.achievementsUnlocked.visibility = GONE
        binding.achievementsProgress.visibility = GONE
        binding.achievementsReceived.visibility = VISIBLE
        binding.achievementsReceivedContent.text = getString(R.string.voucher_code_is, voucherCode)
        @Suppress("SpellCheckingInspection")
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMANY)
        val validUntil = format.parse(until)
        val untilText =
            SimpleDateFormat.getDateInstance(
                SimpleDateFormat.LONG,
                Locale.getDefault()
            ).format(validUntil!!.time)
        binding.achievementValidUntil.text = getString(R.string.valid_until, untilText)
        binding.achievementsReceivedButton.setOnClickListener {
            val clipboard = getSystemService(requireContext(), ClipboardManager::class.java)
            val clip = ClipData.newPlainText(getString(R.string.voucher_code), voucherCode)
            clipboard!!.setPrimaryClip(clip)
        }
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