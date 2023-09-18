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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import de.cyface.app.utils.R
import de.cyface.app.utils.ServiceProvider
import de.cyface.app.utils.capturing.settings.UiSettings
import de.cyface.app.utils.databinding.FragmentTripsBinding
import de.cyface.app.utils.trips.incentives.AuthExceptionListener
import de.cyface.app.utils.trips.incentives.Incentives
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.persistence.model.Measurement
import de.cyface.utils.settings.AppSettings
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okio.IOException
import org.json.JSONException
import org.json.JSONObject
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
 * @version 1.1.0
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
     * The settings used by both, UIs and libraries.
     */
    private lateinit var appSettings: AppSettings

    /**
     * The settings used to store values specific to android-app/utils.
     */
    private lateinit var uiSettings: UiSettings

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
                    lifecycleScope.launch { Exporter(requireContext()).export() }
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
            uiSettings = serviceProvider.uiSettings
            appSettings = serviceProvider.appSettings

            // Load incentivesUrl - only send requests in RFR app
            val rfr = requireContext().packageName.equals("de.cyface.app.r4r")
            if (rfr) {
                val incentivesApi =
                    runBlocking { uiSettings.incentivesUrlFlow.first() }
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
        tripsViewModel.measurements.observe(viewLifecycleOwner) { measurements ->
            measurements?.let { adapter.submitList(it) }

            // Show achievements progress
            if (incentives != null) {
                // Check voucher availability
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        showVouchersLeft()
                    }
                }

                val totalDistanceKm = totalDistanceKm(measurements)
                val progress = min(totalDistanceKm / distanceGoalKm * 100.0, 100.0)

                if (progress < 100) {
                    showProgress(progress, distanceGoalKm, totalDistanceKm)
                } else {
                    // Show request voucher button
                    binding.achievementsError.visibility = GONE
                    binding.achievementsProgress.visibility = GONE
                    binding.achievementsReceived.visibility = GONE
                    binding.achievementsUnlocked.visibility = VISIBLE
                    binding.achievementsUnlockedButton.setOnClickListener {
                        lifecycleScope.launch {
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
                appSettings,
                adapter,
                exportPermissionLauncher,
                WeakReference<Context>(requireContext().applicationContext),
                lifecycleScope
            ), viewLifecycleOwner, Lifecycle.State.RESUMED
        )

        return binding.root
    }

    private fun handleException(e: Exception) {
        Handler(Looper.getMainLooper()).post {
            _binding?.achievementsErrorMessage?.text =
                getString(R.string.error_message_request_failed)
            _binding?.achievementsProgress?.visibility = GONE
            _binding?.achievementsUnlocked?.visibility = GONE
            _binding?.achievementsReceived?.visibility = GONE
            _binding?.achievementsError?.visibility = VISIBLE
            _binding?.achievements?.visibility = VISIBLE
        }
        // This should not happen, thus, reporting to Sentry
        val reportErrors = runBlocking { appSettings.reportErrorsFlow.first() }
        if (reportErrors) {
            Sentry.captureException(e)
        }
    }

    private fun handleAuthorizationException(it: AuthorizationException) {
        if (it.cause is UnknownHostException || it.cause is ConnectException) {
            // e.g. device is offline
            _binding?.achievementsErrorMessage?.text =
                getString(R.string.error_message_server_unavailable)
        } else {
            _binding?.achievementsErrorMessage?.text =
                getString(R.string.error_message_authentication_failed)
            // This should not happen, thus, reporting to Sentry
            val reportErrors = runBlocking { appSettings.reportErrorsFlow.first() }
            if (reportErrors) {
                Sentry.captureException(it)
            }
        }
        _binding?.achievementsProgress?.visibility = GONE
        _binding?.achievementsUnlocked?.visibility = GONE
        _binding?.achievementsReceived?.visibility = GONE
        _binding?.achievementsError?.visibility = VISIBLE
        _binding?.achievements?.visibility = VISIBLE
    }

    private fun handleError(it: IOException) {
        Handler(Looper.getMainLooper()).post {
            // This could also be another problem than "offline"
            _binding?.achievementsErrorMessage?.text =
                getString(R.string.error_message_no_connection)
            _binding?.achievementsProgress?.visibility = GONE
            _binding?.achievementsUnlocked?.visibility = GONE
            _binding?.achievementsReceived?.visibility = GONE
            _binding?.achievementsError?.visibility = VISIBLE
            _binding?.achievements?.visibility = VISIBLE
        }
        // This should not happen, thus, reporting to Sentry
        val reportErrors = runBlocking { appSettings.reportErrorsFlow.first() }
        if (reportErrors) {
            Sentry.captureException(it)
        }
    }

    private fun showProgress(
        progress: Double,
        @Suppress("SameParameterValue") distanceGoalKm: Double,
        totalDistanceKm: Double
    ) {
        binding.achievementsError.visibility = GONE
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

    private fun showVouchersLeft() {
        try {
            incentives!!.availableVouchers(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        handleError(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.code == 200) {
                            try {
                                val json = JSONObject(response.body!!.string())
                                val availableVouchers = json.getInt("vouchers")
                                if (availableVouchers > 0) {
                                    Handler(Looper.getMainLooper()).post {
                                        // Can be null when switching tab before response returns
                                        _binding?.achievementsVouchersLeft?.text =
                                            getString(R.string.voucher_left, availableVouchers)
                                        _binding?.achievementsError?.visibility = GONE
                                        _binding?.achievements?.visibility = VISIBLE
                                    }
                                } else {
                                    showNoVouchersLeft()
                                    //_binding?.achievementsError?.visibility = GONE
                                    //_binding?.achievements?.visibility = GONE
                                }
                            } catch (e: JSONException) {
                                handleJsonException(e)
                            }
                        } else {
                            handleUnknownResponse(response.code)
                        }
                    }

                },
                object : AuthExceptionListener {
                    override fun onException(e: AuthorizationException) {
                        handleAuthorizationException(e)
                    }
                })
        } catch (e: Exception) {
            handleException(e)
        }
    }

    private fun handleUnknownResponse(responseCode: Int) {
        val reportErrors = runBlocking { appSettings.reportErrorsFlow.first() }
        if (reportErrors) {
            Sentry.captureMessage("Unknown response code: $responseCode")
        }
        throw IllegalArgumentException("Unknown response code: $responseCode")
    }

    @Suppress("SpellCheckingInspection")
    private fun handleJsonException(e: JSONException) {
        // If parsing crashes the server probably returned a 302 which forwards to
        // the Keycloak page (`<!DOCTYPE html>...`) which can't be parsed.
        // So it'S ok that this crashes, as this should not happen (302 = no Auth header)
        val reportErrors = runBlocking { appSettings.reportErrorsFlow.first() }
        if (reportErrors) {
            Sentry.captureMessage("Forwarded? Forgot Auth header?")
        }
        throw java.lang.IllegalStateException("Forwarded? Forgot Auth header?", e)
    }

    private fun showVoucher() {
        try {
            incentives!!.voucher(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        handleError(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        // Capture all responses, also non-successful response codes
                        if (response.code == 428) {
                            // Use from wrong municipality tried to request a voucher [RFR-605]
                            throw IllegalStateException("Voucher not allowed for this user")
                        } else if (response.code == 204) {
                            // if this is in face 204: The last voucher just got assigned
                            // else the server forgot to send a JSON content
                            showNoVouchersLeft()
                            // This should hardly ever happen, thus, reporting to Sentry
                            val reportErrors =
                                runBlocking { appSettings.reportErrorsFlow.first() }
                            if (reportErrors) {
                                Sentry.captureMessage("Last voucher just got assigned?")
                            }
                        } else if (response.code == 200) {
                            try {
                                val json = JSONObject(response.body!!.string())
                                val code = json.getString("code")
                                val until = json.getString("until")
                                Handler(Looper.getMainLooper()).post {
                                    binding.achievementsError.visibility = GONE
                                    binding.achievementsUnlocked.visibility = GONE
                                    binding.achievementsProgress.visibility = GONE
                                    binding.achievementsReceived.visibility = VISIBLE
                                    binding.achievementsReceivedContent.text =
                                        getString(R.string.voucher_code_is, code)
                                    @Suppress("SpellCheckingInspection")
                                    val format =
                                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMANY)
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
                                            getSystemService(
                                                requireContext(),
                                                ClipboardManager::class.java
                                            )
                                        val clip =
                                            ClipData.newPlainText(
                                                getString(R.string.voucher_code),
                                                code
                                            )
                                        clipboard!!.setPrimaryClip(clip)
                                    }
                                }
                            } catch (e: JSONException) {
                                handleJsonException(e)
                            }
                        } else {
                            handleUnknownResponse(response.code)
                        }
                    }
                },
                object : AuthExceptionListener {
                    override fun onException(e: AuthorizationException) {
                        handleAuthorizationException(e)
                    }
                })
        } catch (e: Exception) {
            handleException(e)
        }
    }

    private fun showNoVouchersLeft() {
        Handler(Looper.getMainLooper()).post {
            // This could also be another problem than "offline"
            _binding?.achievementsErrorMessage?.text =
                getString(R.string.error_message_no_voucher_left)
            _binding?.achievementsProgress?.visibility = GONE
            _binding?.achievementsUnlocked?.visibility = GONE
            _binding?.achievementsReceived?.visibility = GONE
            _binding?.achievementsError?.visibility = VISIBLE
            _binding?.achievements?.visibility = VISIBLE
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