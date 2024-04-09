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
import android.content.Intent
import android.net.Uri
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
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.model.ParcelableGeoLocation
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
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

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
     * An implementation of the persistence layer which caches some data during capturing.
     */
    private lateinit var persistence: DefaultPersistenceLayer<CapturingPersistenceBehaviour>

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
     * The raffle events - sorted by date ASC!
     */
    private val events = listOf(
        // test event
        Event(
            "2024-04-01T00:00:00Z",
            "2024-04-20T23:59:59Z",
            "2024-04-24T23:59:59Z" // FIXME
        ),
        // event 1
        Event(
            "2024-05-01T00:00:00Z",
            "2024-05-31T23:59:59Z",
            "2024-06-14T23:59:59Z" // FIXME
        ),
        // event 2
        Event(
            "2024-08-01T00:00:00Z",
            "2024-08-30T23:59:59Z",
            "2024-09-14T23:59:59Z" // FIXME
        ),
    )

    /**
     * The minimum length of a measurement in meters to be valid for the raffle condition.
     */
    private val minimumMeasurementMeters = 1000.0

    /**
     * The minimum number of measurements with [minimumMeasurementMeters] to unlock a raffle code.
     */
    private val requiredDaysWithMinimumLength = 7

    /**
     * The minimum number of days with measurements which pass through the [geoFence] to unlock a raffle code.
     */
    private val requiredDaysWithMeasurementsWithinGeoFence = 3

    /**
     * The minimum number of GNSS measurements within the [geoFence] to count as "measurement within [geoFence]".
     */
    private val requiredLocationsWithinGeoFence = 10

    /**
     * The [GeoFence] which [requiredDaysWithMeasurementsWithinGeoFence] need to pass to unlock a raffle code.
     */
    private val geoFence = GeoFence(51.395503, 12.220760, 150.0)

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
            persistence = capturing.persistenceLayer
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
                showAchievements(measurements)
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

    /**
     * Main method to check the achievements and update the UI with the current state.
     *
     * @param measurements The measurements to check for achievements.
     */
    private fun showAchievements(measurements: List<Measurement>) {

        // Check for active events
        val activeEvent = findActiveEvent(events)
        if (activeEvent == null) {
            val nextEvent = findNextEvent(events)
            showNoActiveEvent(nextEvent)
            return
        }

        // Check voucher availability (or participating municipality => 0 vouchers [RFR-997])
        // TODO: Fix this race-condition with the next checks
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                showVouchersLeft()
            }
        }

        // Check achievement unlock conditions
        // FIXME: Add a test for start and end date to ensure they are included
        val eventMeasurements = measurements.filter {
            Date(it.timestamp).after(activeEvent.startTime) &&
                    Date(it.timestamp).before(activeEvent.endTime)
        }
        val conditions = checkMeasurements(
            eventMeasurements,
            minimumMeasurementMeters,
            requiredDaysWithMinimumLength,
            requiredDaysWithMeasurementsWithinGeoFence,
            requiredLocationsWithinGeoFence,
            geoFence
        )

        // Show progress to user
        if (!conditions.achievementUnlocked) {
            showProgress(conditions)
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

    /**
     * Finds the currently active event among a list of events.
     *
     * @param events The list of events.
     * @return The active event or `null` if no such event is left.
     */
    private fun findActiveEvent(events: List<Event>): Event? {
        val now = Date()
        return events.firstOrNull { now.after(it.startTime) && now.before(it.activeUntil) }
    }

    /**
     * Finds the next event among a list of events.
     *
     * @param events The list of events.
     * @return The next event or `null` if no such event is left.
     */
    private fun findNextEvent(events: List<Event>): Event? {
        val now = Date()
        return events.firstOrNull { it.startTime.after(now) }
    }

    /**
     * Loads the tracks for a list of measurements to check for the raffle unlock conditions.
     *
     * @param measurements The measurements to check.
     * @param minLength The minimum length of a measurement in meters to be valid for the raffle condition.
     * @param requiredDaysWithMinimumLength The minimum number of measurements with [minLength] to unlock a raffle code.
     * @param requiredDaysWithMeasurementsWithinGeoFence The minimum number of days with measurements which need to
     * pass through the [geoFence].
     * @param requiredLocationsWithinGeoFence The minimum number of GNSS measurements within the [geoFence] to count as
     * "measurement within [geoFence]".
     * @param geoFence The [GeoFence] which [requiredDaysWithMeasurementsWithinGeoFence] measurements need to pass.
     * @return A `Future` which resolves to `true` if all conditions are passed.
     */
    private fun checkMeasurements(
        measurements: List<Measurement>,
        @Suppress("SameParameterValue") minLength: Double,
        @Suppress("SameParameterValue") requiredDaysWithMinimumLength: Int,
        @Suppress("SameParameterValue") requiredDaysWithMeasurementsWithinGeoFence: Int,
        @Suppress("SameParameterValue") requiredLocationsWithinGeoFence: Int,
        geoFence: GeoFence
    ): UnlockCondition {
        var measurementCount = 0
        val daysWithMeasurementsAboveMinimumLength = mutableSetOf<Date>()
        val daysWithMeasurementsWithinGeoFence = mutableSetOf<Date>()

        measurements.forEach { measurement ->
            measurementCount++

            // Load Tracks to check conditions like geoFence
            val tracks = persistence.loadTracks(measurement.id)
            if (tracks.isNotEmpty() && tracks.first().geoLocations.isNotEmpty()) {
                val timestamp = tracks.first().geoLocations.first()!!.timestamp
                val measurementDay = Date(timestamp)

                if (measurement.distance >= minLength) {
                    daysWithMeasurementsAboveMinimumLength.add(measurementDay)
                }

                tracks.forEach trackLoop@{ track ->
                    val locationsWithinGeoFenceCount = track.geoLocations.count { location ->
                        geoFence.isWithin(location!!)
                    }
                    if (locationsWithinGeoFenceCount >= requiredLocationsWithinGeoFence) {
                        daysWithMeasurementsWithinGeoFence.add(measurementDay)
                        return@trackLoop
                    }
                }
            }
        }

        val daysAboveThresholdUnlocked =
            daysWithMeasurementsAboveMinimumLength.size >= requiredDaysWithMinimumLength
        val daysWithinGeoFenceUnlocked =
            daysWithMeasurementsWithinGeoFence.size >= requiredDaysWithMeasurementsWithinGeoFence
        val unSyncedMeasurements = measurements.filter {
            it.status == MeasurementStatus.FINISHED || it.status == MeasurementStatus.SYNCABLE_ATTACHMENTS
        }
        val measurementsSynced = unSyncedMeasurements.isEmpty()
        val allConditionsMet =
            daysAboveThresholdUnlocked && daysWithinGeoFenceUnlocked && measurementsSynced

        return UnlockCondition(
            ridesWithinEvent = measurementCount,
            daysWithMeasurementsAboveMinimumLength.toSet(),
            requiredDaysWithMeasurementsWithinGeoFence,
            daysWithinGeoFenceUnlocked,
            unSyncedMeasurements.size,
            measurementsSynced,
            achievementUnlocked = allConditionsMet
        )
    }

    private fun showProgress(p: UnlockCondition) {
        var text = "Unknown Progress"
        var progressDouble: Double? = null
        // Condition 1: days with measurements within the geo fence
        if (!p.daysWithinGeoFenceUnlocked) {
            val left = p.requiredDaysWithinGeoFence - p.daysWithinGeoFence.size
            text = getString(
                R.string.days_within_geo_fence_progress,
                left,
                p.requiredDaysWithinGeoFence
            )
            progressDouble = min(p.geoFenceProgress(), 100.0)
        } else
        // Condition 2: all measurements synced
            if (!p.measurementsSynced) {
                text = getString(R.string.sync_progress, p.unSyncedMeasurements, p.ridesWithinEvent)
                progressDouble = min(p.syncProgress(), 100.0)
            }

        binding.achievementsError.visibility = GONE
        binding.achievementsUnlocked.visibility = GONE
        binding.achievementsReceived.visibility = GONE
        binding.achievementsProgress.visibility = VISIBLE
        binding.achievementsProgressContent.text = text
        binding.achievementsProgressBar.progress = progressDouble!!.roundToInt()
    }

    private fun showNoActiveEvent(nextEvent: Event?) {
        val text = if (nextEvent == null) {
            getString(R.string.error_message_no_active_event)
        } else {
            val next = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
            getString(R.string.error_message_next_event, next.format(nextEvent.startTime))
        }
        _binding?.achievementsErrorMessage?.text = text
        _binding?.achievementsError?.visibility = VISIBLE
        _binding?.achievements?.visibility = VISIBLE

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
                                        // We don't want to show number of raffle tickets left
                                        /*_binding?.numberOfVouchersLeft?.text =
                                            getString(R.string.voucher_left, availableVouchers)*/
                                        _binding?.achievementsError?.visibility = GONE
                                        _binding?.achievements?.visibility = VISIBLE
                                    }
                                } else { //FIXME
                                    showNoVouchersLeft()
                                    _binding?.achievementsError?.visibility = GONE
                                    _binding?.achievements?.visibility = GONE
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
                            // User from wrong municipality tried to request a voucher [RFR-605]
                            throw IllegalStateException("Voucher not allowed for this user")
                            // FIXME: add response body handling to throw an exception with
                            // more details about what condition failed
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
                                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("mailto:")
                                            putExtra(
                                                Intent.EXTRA_EMAIL,
                                                arrayOf("gewinnspiel@ready-for-robots.de")
                                            )
                                            putExtra(
                                                Intent.EXTRA_SUBJECT,
                                                getString(R.string.email_subject, code)
                                            )
                                        }
                                        startActivity(intent)

                                        // Copy to clipboard
                                        /*val clipboard =
                                            getSystemService(
                                                requireContext(),
                                                ClipboardManager::class.java
                                            )
                                        val clip =
                                            ClipData.newPlainText(
                                                getString(R.string.voucher_code),
                                                code
                                            )
                                        clipboard!!.setPrimaryClip(clip)*/
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

    /**
     * A event, like e.g. a raffle.
     *
     * @param startTime The earliest time at which a raffle ticket can be unlocked.
     * @param endTime The latest time at which a raffle ticket can be unlocked.
     * @param activeUntil The latest time until which the raffle ticket can be redeemed.
     */
    data class Event(
        val startTime: Date,
        val endTime: Date,
        val activeUntil: Date
    ) {
        constructor(
            startTimeString: String,
            endTimeString: String,
            activeUntilString: String
        ) : this(
            SimpleDateFormat(
                @Suppress("SpellCheckingInspection") "yyyy-MM-dd'T'HH:mm:ss'Z'",
                Locale.US
            ).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(startTimeString)!!,
            SimpleDateFormat(
                @Suppress("SpellCheckingInspection") "yyyy-MM-dd'T'HH:mm:ss'Z'",
                Locale.US
            ).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(endTimeString)!!,
            SimpleDateFormat(
                @Suppress("SpellCheckingInspection") "yyyy-MM-dd'T'HH:mm:ss'Z'",
                Locale.US
            ).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(activeUntilString)!!,
        )
    }

    /**
     * A geo fence which is defined by a center location and a radius.
     *
     * @param centerLat The latitude of the center.
     * @param centerLon The longitude of the center.
     * @param radiusMeters The radius in meters.
     */
    data class GeoFence(
        val centerLat: Double,
        val centerLon: Double,
        val radiusMeters: Double
    ) {
        // FIXME: ensure the distance is correct (test)
        /**
         * Checks if a given location is within the [GeoFence].
         *
         * @param location The location to check.
         * @return `true` if the location is within.
         */
        fun isWithin(location: ParcelableGeoLocation): Boolean {
            val earthRadiusMeters = 6371000.0 // average earth radius in meters
            val dLat = Math.toRadians(centerLat - location.lat)
            val dLon = Math.toRadians(centerLon - location.lon)
            val lat1 = Math.toRadians(location.lat)
            val lat2 = Math.toRadians(centerLat)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    sin(dLon / 2) * sin(dLon / 2) * cos(lat1) * cos(lat2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            val distance = earthRadiusMeters * c
            return distance <= radiusMeters
        }
    }

    data class UnlockCondition(
        val ridesWithinEvent: Int = 0,
        val daysWithinGeoFence: Set<Date> = emptySet(),
        val requiredDaysWithinGeoFence: Int,
        val daysWithinGeoFenceUnlocked: Boolean = false,
        val unSyncedMeasurements: Int,
        val measurementsSynced: Boolean = false,
        val achievementUnlocked: Boolean = false
    ) {
        fun geoFenceProgress(): Double {
            return (daysWithinGeoFence.size / requiredDaysWithinGeoFence).toDouble()
        }

        fun syncProgress(): Double {
            return (unSyncedMeasurements / ridesWithinEvent).toDouble()
        }
    }
}