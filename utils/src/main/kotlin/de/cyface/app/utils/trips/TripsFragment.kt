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
import android.os.Looper
import android.preference.PreferenceManager
import android.util.Log
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
import de.cyface.app.utils.SharedConstants.TAG
import de.cyface.app.utils.databinding.FragmentTripsBinding
import de.cyface.app.utils.trips.incentives.Incentives
import de.cyface.app.utils.trips.incentives.Incentives.Companion.INCENTIVES_ENDPOINT_URL_SETTINGS_KEY
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.persistence.model.Measurement
import de.cyface.utils.Validate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Double.min
import java.lang.ref.WeakReference
import java.net.ConnectException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The [Fragment] which shows all finished measurements to the user.
 *
 * @author Armin Schnabel
 * @version 1.0.1
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
     * defined by E-Mail from Matthias Koss, 23.05.23
     */
    @Suppress("SpellCheckingInspection")
    private val distanceGoalKm = 15.0

    /**
     * The API to get the voucher data from or `null` when no such things show be shown to the user.
     */
    private var incentives: Incentives? = null

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
                        requireContext().getString(R.string.export_data_no_permission),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) tracker?.onRestoreInstanceState(savedInstanceState)

        if (activity is ServiceProvider) {
            val serviceProvider = activity as ServiceProvider
            capturing = serviceProvider.capturing

            // Load incentivesUrl - only send requests in RFR app
            val rfr = requireContext().packageName.equals("de.cyface.app.r4r")
            if (rfr) {
                val incentivesApi = incentivesApi(requireContext())
                this.incentives = Incentives(requireContext(), incentivesApi, serviceProvider.auth)
            }
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

        // Check voucher availability
        if (incentives != null) {
            GlobalScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        incentives!!.availableVouchers(
                            requireContext(),
                            { response ->
                                val availableVouchers = response.getInt("vouchers")
                                if (availableVouchers > 0) {
                                    Handler(Looper.getMainLooper()).post {
                                        // Can be null when switching tab before response returns
                                        _binding?.achievementsVouchersLeft?.text =
                                            getString(R.string.voucher_left, availableVouchers)
                                        _binding?.achievements?.visibility = VISIBLE
                                    }
                                }
                            },
                            {
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(
                                        context,
                                        "Gutscheinprüfung fehlgeschlagen: ${it.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                // FIXME: Report to Sentry, to add error handling later
                            },
                            {
                                // FIXME: handle refresh token error, e.g. when server is not reachable
                                var reason = "Authentifizierung fehlgeschlagen: ${it.message}"
                                if (it.cause is UnknownHostException || it.cause is ConnectException) {
                                    reason = "Server nicht erreichbar." // e.g. device is offline
                                } else {
                                    // FIXME: Report to Sentry, to add error handling later
                                }
                                Toast.makeText(
                                    context,
                                    reason,
                                    Toast.LENGTH_LONG
                                ).show()
                            })
                    } catch (e: Exception) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                context,
                                "Gutscheinprüfung nicht möglich",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        Log.e(TAG, "availableVouchers crashed", e)
                        // FIXME: Report to Sentry, to add error handling later
                    }
                }
            }
        }

        // Update adapters with the updates from the ViewModel
        tripsViewModel.measurements.observe(viewLifecycleOwner) { measurements ->
            measurements?.let { adapter.submitList(it) }

            // Show achievements progress
            if (incentives != null) {
                val totalDistanceKm = totalDistanceKm(measurements)
                val progress = min(totalDistanceKm / distanceGoalKm * 100.0, 100.0)

                if (progress < 100) {
                    showProgress(progress, distanceGoalKm, totalDistanceKm)
                } else {
                    // Show request voucher button
                    binding.achievementsProgress.visibility = GONE
                    binding.achievementsReceived.visibility = GONE
                    binding.achievementsUnlocked.visibility = VISIBLE
                    binding.achievementsUnlockedButton.setOnClickListener {
                        GlobalScope.launch {
                            withContext(Dispatchers.IO) {
                                showVoucher()
                            }
                        }
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
            getString(R.string.achievements_progress, missingKm, distanceGoalKm)
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

    /**
     * Reads the Incentives API URL from the preferences.
     *
     * @param context The `Context` required to read the preferences
     * @return The URL as string
     */
    private fun incentivesApi(context: Context): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val apiEndpoint = preferences.getString(INCENTIVES_ENDPOINT_URL_SETTINGS_KEY, null)
        Validate.notNull(
            apiEndpoint,
            "TripsFragment: Incentives url not available. Please set the applications server url preference."
        )
        return apiEndpoint!!
    }

    private fun showVoucher() {
        try {
            incentives!!.voucher(
                requireContext(),
                { response ->
                    val code = response.getString("code")
                    val until = response.getString("until")
                    Handler(Looper.getMainLooper()).post {
                        binding.achievementsUnlocked.visibility = GONE
                        binding.achievementsProgress.visibility = GONE
                        binding.achievementsReceived.visibility = VISIBLE
                        binding.achievementsReceivedContent.text =
                            getString(R.string.voucher_code_is, code)
                        @Suppress("SpellCheckingInspection")
                        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMANY)
                        val validUntil = format.parse(until)
                        val untilText =
                            SimpleDateFormat.getDateInstance(
                                SimpleDateFormat.LONG,
                                Locale.getDefault()
                            ).format(validUntil!!.time)
                        binding.achievementValidUntil.text =
                            getString(R.string.valid_until, untilText)
                        binding.achievementsReceivedButton.setOnClickListener {
                            val clipboard =
                                getSystemService(requireContext(), ClipboardManager::class.java)
                            val clip = ClipData.newPlainText(getString(R.string.voucher_code), code)
                            clipboard!!.setPrimaryClip(clip)
                        }
                    }
                },
                {
                    // FIXME: Handle 204 - no content (when the last voucher just got assigned)
                    Toast.makeText(
                        context,
                        "Gutscheinanfrage fehlgeschlagen: ${it.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    // FIXME: Report to Sentry, to add error handling later
                },
                {
                    // FIXME: handle refresh token error, e.g. when server is not reachable
                    var reason = "Authentifizierung fehlgeschlagen: ${it.message}"
                    if (it.cause is UnknownHostException || it.cause is ConnectException) {
                        reason = "Server nicht erreichbar." // e.g. device is offline
                    } else {
                        // FIXME: Report to Sentry, to add error handling later
                    }
                    Toast.makeText(
                        context,
                        reason,
                        Toast.LENGTH_LONG
                    ).show()
                })
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "Gutscheinanfrage nicht möglich",
                    Toast.LENGTH_LONG
                ).show()
            }
            Log.e(TAG, "availableVouchers crashed", e)
            // FIXME: Report to Sentry, to add error handling later
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