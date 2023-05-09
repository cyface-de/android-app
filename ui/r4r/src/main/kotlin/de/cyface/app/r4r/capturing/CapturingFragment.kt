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
package de.cyface.app.r4r.capturing

import android.app.Activity
import android.app.ProgressDialog
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.view.MenuHost
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.navigation.fragment.NavHostFragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import de.cyface.app.r4r.R
import de.cyface.app.utils.ServiceProvider
import de.cyface.app.r4r.databinding.FragmentCapturingBinding
import de.cyface.app.r4r.capturing.map.MapFragment
import de.cyface.app.r4r.capturing.marker.MarkerFragment
import de.cyface.app.r4r.capturing.speed.SpeedFragment
import de.cyface.app.r4r.utils.Constants.TAG
import de.cyface.app.utils.CalibrationDialogListener
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.DataCapturingListener
import de.cyface.datacapturing.DataCapturingService
import de.cyface.datacapturing.IsRunningCallback
import de.cyface.datacapturing.MessageCodes
import de.cyface.datacapturing.ShutDownFinishedHandler
import de.cyface.datacapturing.StartUpFinishedHandler
import de.cyface.datacapturing.exception.DataCapturingException
import de.cyface.datacapturing.exception.MissingPermissionException
import de.cyface.datacapturing.model.CapturedData
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.datacapturing.ui.Reason
import de.cyface.energy_settings.TrackingSettings.isBackgroundProcessingRestricted
import de.cyface.energy_settings.TrackingSettings.isEnergySaferActive
import de.cyface.energy_settings.TrackingSettings.isGnssEnabled
import de.cyface.energy_settings.TrackingSettings.isProblematicManufacturer
import de.cyface.energy_settings.TrackingSettings.showEnergySaferWarningDialog
import de.cyface.energy_settings.TrackingSettings.showGnssWarningDialog
import de.cyface.energy_settings.TrackingSettings.showRestrictedBackgroundProcessingWarningDialog
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.model.Modality
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.persistence.model.Track
import de.cyface.persistence.strategy.DefaultLocationCleaning
import de.cyface.utils.DiskConsumption
import de.cyface.utils.Validate
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * This is the UI controller/element, responsible for displaying the data from the [CapturingViewModel].
 *
 * It holds the `Observer` objects which control what happens when the `LiveData` changes.
 * The [ViewModel]s are responsible for holding the `LiveData` data.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.2.0
 */
class CapturingFragment : Fragment(), DataCapturingListener {

    /**
     * This property is only valid between onCreateView and onDestroyView.
     */
    private var _binding: FragmentCapturingBinding? = null

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
     * Button which allows to start or resume a measurement.
     */
    private lateinit var startResumeButton: FloatingActionButton

    /**
     * Button which allows to stop a measurement.
     */
    private lateinit var stopButton: FloatingActionButton

    /**
     * Button which allows to pause a measurement.
     */
    private lateinit var pauseButton: FloatingActionButton

    /*+
     * Listeners which are interested in the status of the calibration dialog.
     */
    private var calibrationDialogListener: Collection<CalibrationDialogListener>? = null

    /**
     * Shared instance of the [CapturingViewModel] which is used by multiple `Fragments.
     */
    private val viewModel: CapturingViewModel by activityViewModels {
        CapturingViewModelFactory(
            persistence.measurementRepository!!,
            persistence.eventRepository!!
        )
    }

    /**
     * Handler for [startResumeButton] clicks.
     */
    private val onStartResume: View.OnClickListener = OnStartResumeListener(this)

    /**
     * Handler for [pauseButton] clicks.
     */
    private val onPause: View.OnClickListener = OnPauseListener(this)

    /**
     * Handler for [stopButton] clicks.
     */
    private val onStop: View.OnClickListener = OnStopListener(this)

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
        _binding = FragmentCapturingBinding.inflate(inflater, container, false)
        val root: View = binding.root
        startResumeButton = binding.startResumeButton
        stopButton = binding.stopButton
        pauseButton = binding.pauseButton

        // Update UI elements with the updates from the ViewModel
        viewModel.measurementId.observe(viewLifecycleOwner) {
            val tripTitle = if (it == null) null else getString(de.cyface.app.utils.R.string.trip_id, it)
            binding.tripTitle.text = tripTitle ?: ""
        }
        viewModel.measurement.observe(viewLifecycleOwner) {
            val visibility = if (it == null) INVISIBLE else VISIBLE
            binding.speedTitle.visibility = visibility
            binding.distanceTitle.visibility = visibility
            binding.durationTitle.visibility = visibility
            binding.ascendTitle.visibility = visibility
            binding.co2Title.visibility = visibility
            binding.speedView.visibility = visibility
            binding.distanceView.visibility = visibility
            binding.durationView.visibility = visibility
            binding.ascendView.visibility = visibility
            binding.co2View.visibility = visibility

            // If code duplication increases, we could reduce it by introducing a domain layer
            // see https://developer.android.com/topic/architecture/domain-layer

            val distanceKm = it?.distance?.div(1000.0)
            binding.distanceView.text =
                if (distanceKm == null) "" else getString(de.cyface.app.utils.R.string.distanceKm, distanceKm)

            // 95 g / km see https://de.statista.com/infografik/25742/durchschnittliche-co2-emission-von-pkw-in-deutschland-im-jahr-2020/
            val co2Kg = distanceKm?.times(95)?.div(1000)
            binding.co2View.text = if (co2Kg == null) "" else getString(de.cyface.app.utils.R.string.co2kg, co2Kg)

            val millis = if (it == null) null else persistence.loadDuration(it.id)
            val seconds = millis?.div(1000)
            val minutes = seconds?.div(60)
            val hours = minutes?.div(60)
            val hoursText =
                if (hours == null || hours == 0L) "" else getString(de.cyface.app.utils.R.string.hours, hours) + " "
            val minutesText = if (minutes == null || minutes == 0L) "" else getString(
                de.cyface.app.utils.R.string.minutes,
                minutes % 60
            ) + " "
            val secondsText = if (seconds == null) "" else getString(de.cyface.app.utils.R.string.seconds, seconds % 60)
            val durationText = hoursText + minutesText + secondsText
            binding.durationView.text = durationText
        }
        viewModel.location.observe(viewLifecycleOwner) {
            val ascendText: String?
            try {
                val measurement = persistence.loadCurrentlyCapturedMeasurement()
                val averageSpeedKmh =
                    persistence.loadAverageSpeed(
                        measurement.id,
                        DefaultLocationCleaning()
                    ) * 3.6

                val ongoingCapturing = measurement.status == MeasurementStatus.OPEN
                val ascend = if (ongoingCapturing) persistence.loadAscend(measurement.id) else null
                ascendText = getString(de.cyface.app.utils.R.string.ascendMeters, ascend ?: 0.0)

                val speedKmPh = it?.speed?.times(3.6)
                binding.speedView.text = if (speedKmPh == null) "" else getString(
                    de.cyface.app.utils.R.string.speedKphWithAverage,
                    speedKmPh,
                    averageSpeedKmh
                )
                binding.ascendView.text = if (speedKmPh == null) "" else ascendText
            } catch (e: NoSuchMeasurementException) {
                // Happen when locations arrive late
                Log.d(TAG, "Position changed while no capturing is active, ignoring.")
            }
        }

        startResumeButton.setOnClickListener(onStartResume)
        pauseButton.setOnClickListener(onPause)
        stopButton.setOnClickListener(onStop)
        calibrationDialogListener = HashSet()

        // Add items to menu (top right)
        // Not using `findNavController()` as `FragmentContainerView` in `activity_main.xml` does not
        // work with with `findNavController()` (https://stackoverflow.com/a/60434988/5815054).
        val navHostFragment =
            requireActivity().supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        requireActivity().addMenuProvider(
            MenuProvider(
                capturing,
                requireActivity(),
                navHostFragment.navController
            ),
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )

        // Dynamically add tabs (dynamically show MarkerFragment)
        val tabLayout = binding.tabLayout
        tabLayout.addTab(tabLayout.newTab().setIcon(R.drawable.ic_baseline_map_24))
        if (!MarkerFragment.eventPassed()) {
            tabLayout.addTab(tabLayout.newTab().setIcon(R.drawable.baseline_pin_drop_24))
        }
        tabLayout.addTab(tabLayout.newTab().setIcon(R.drawable.ic_baseline_speed_24))

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Allows to change the tabs by swiping horizontally and handles animation
        val viewPager = view.findViewById<ViewPager2>(R.id.pager)
        // Tab Swiping disabled to ease touch-control in the Google Map
        viewPager.isUserInputEnabled = false

        // Connect adapter to ViewPager (which provides pages to the view pager)
        val fragmentManager: FragmentManager = childFragmentManager
        viewPager.adapter = PagerAdapter(fragmentManager, lifecycle)

        // Add TabItems to TabLayout
        val tabLayout: TabLayout = view.findViewById(R.id.tabLayout)

        // Connect TabLayout to Adapter
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewPager.currentItem = tab.position
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // `reconnect()` is called in `onResume()` which is called after `onViewCreated`
    }

    override fun onPause() {
        super.onPause()
        disconnect()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: reconnecting ...")
        reconnect()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onFixAcquired() {
        // Nothing to do
    }

    override fun onFixLost() {
        // Nothing to do
    }

    override fun onNewGeoLocationAcquired(position: ParcelableGeoLocation) {
        viewModel.setLocation(position)
        viewModel.addToTrack(position)
    }

    override fun onNewSensorDataAcquired(data: CapturedData?) {
        // Nothing to do here
    }

    override fun onLowDiskSpace(allocation: DiskConsumption?) {
        // Nothing to do here - handled by [CapturingEventHandler]
    }

    override fun onSynchronizationSuccessful() {
        // Nothing to do here
    }

    override fun onErrorState(e: Exception?) {
        throw java.lang.IllegalStateException(e)
    }

    override fun onRequiresPermission(
        permission: String?,
        reason: Reason?
    ): Boolean {
        return false
    }

    override fun onCapturingStopped() {
        setCapturingStatus(MeasurementStatus.FINISHED)
        viewModel.setLocation(null)
        viewModel.setMeasurementId(null)
    }

    /**
     * Checks the current capturing state and refreshes the UI elements accordingly.
     */
    private fun reconnect() {
        capturing.addDataCapturingListener(this)
        // cameraService.addCameraListener(this)

        // To avoid blocking the UI when switching Tabs, this is implemented in an async way.
        // I.e. we disable all buttons as the capturingState is set in the callback.
        startResumeButton.isEnabled = false
        pauseButton.isEnabled = false
        stopButton.isEnabled = false
        GlobalScope.launch {
            // OPEN: running capturing
            if (capturing.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT)) {
                Log.d(TAG, "onResume: reconnecting DCS succeeded")
                val (id) = capturing.loadCurrentlyCapturedMeasurement()
                viewModel.setMeasurementId(id)
                // We re-sync the button here as the data capturing can be canceled while the app is closed
                setCapturingStatus(MeasurementStatus.OPEN)
                updateCachedTrack(id)

                // Also try to reconnect to CameraService if it's alive
                /*if (cameraService.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT)) {
                    // It does not matter whether isCameraServiceRequested() as this can change all the time
                    Log.d(
                        TAG,
                        "onResume: reconnecting CameraService succeeded"
                    )
                }*/
                return@launch
            }

            // PAUSED or FINISHED capturing
            Log.d(TAG, "onResume: reconnecting timed out")
            if (persistence.hasMeasurement(MeasurementStatus.PAUSED)) {
                setCapturingStatus(MeasurementStatus.PAUSED)
                viewModel.setMeasurementId(null)
            } else {
                setCapturingStatus(MeasurementStatus.FINISHED)
                viewModel.setMeasurementId(null)
            }

            // Check if there is a zombie CameraService running
            // In case anything went wrong and the camera is still bound by this app we're releasing it so that it
            // can be used by other apps again
            /* if (cameraService.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT)) {
                Log.w(
                    de.cyface.camera_service.Constants.TAG, "Zombie CameraService is running and it's "
                            + (if (isCameraServiceRequested()) "" else "*not*") + " requested"
                )
                cameraService.stop(
                    object :
                        ShutDownFinishedHandler(de.cyface.camera_service.MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED) {
                        override fun shutDownFinished(measurementIdentifier: Long) {
                            Log.d(
                                TAG,
                                "onResume: zombie CameraService stopped"
                            )
                        }
                    })
                throw java.lang.IllegalStateException(
                    "Camera stopped manually as the camera was not released. This should not happen!"
                )
            }*/
        }
    }

    /**
     * Releases resources by unregistering listeners which are re-registered [onResume].
     */
    private fun disconnect() {
        capturing.removeDataCapturingListener(this)
        // cameraService.removeCameraListener(this)

        try {
            capturing.disconnect()
        } catch (e: DataCapturingException) {
            // This just tells us there is no running capturing in the background, see [MOV-588]
            Log.d(TAG, "No need to unbind as the background service was not running.")
        }
        /*try {
            cameraService.disconnect()
        } catch (e: DataCapturingException) {
            // This just tells us there is no running capturing in the background, see [MOV-588]
            Log.d(
                de.cyface.app.utils.Constants.TAG,
                "No need to unbind as the camera background service was not running."
            )
        }*/

        viewModel.setMeasurementId(null) // Experimental, might influence other Fragments
        //setCapturingStatus(null) // Seems not necessary right not, but is called in `reconnect`
        //viewModel.setTracks(null) // Seems not necessary right not, but is called in `reconnect`
    }

    /**
     * This method helps to access the button via UI thread from a handler thread.
     */
    private fun runOnUiThread(runnable: Runnable) {
        Handler(Looper.getMainLooper()).post(runnable)
    }

    /**
     * Updates the "is enabled" state of the button on the ui thread.
     *
     * @param button The [Button] to access the [Activity] to run code on the UI thread
     */
    private fun setButtonEnabled(button: ImageButton) {
        runOnUiThread { button.isEnabled = true }
    }

    /**
     * Enables/disables and/or sets the [Button] icon to indicate the next reachable state.
     *
     * @param newStatus the new status of the measurement
     */
    private fun setCapturingStatus(newStatus: MeasurementStatus) {
        viewModel.setCapturing(newStatus)
        runOnUiThread {
            updateButtonView(newStatus)
        }
    }

    /**
     * Updates the views of the capturing buttons depending on the capturing [status].
     *
     * The button view indicates the next state to be reached after the button is clicked again.
     *
     * @param status the new capturing status
     */
    private fun updateButtonView(status: MeasurementStatus) {
        when (status) {
            MeasurementStatus.OPEN -> {
                startResumeButton.isEnabled = false
                pauseButton.isEnabled = true
                stopButton.isEnabled = true
            }
            MeasurementStatus.PAUSED -> {
                startResumeButton.setImageResource(R.drawable.ic_baseline_fast_forward_24)
                startResumeButton.isEnabled = true
                pauseButton.isEnabled = false
                stopButton.isEnabled = true
            }
            MeasurementStatus.FINISHED -> {
                startResumeButton.setImageResource(R.drawable.ic_baseline_play_arrow_24)
                startResumeButton.isEnabled = true
                pauseButton.isEnabled = false
                stopButton.isEnabled = false
            }
            else -> throw IllegalArgumentException("Invalid button state: $status")
        }
    }

    /**
     * Pause capturing
     */
    private fun pauseCapturing() {
        try {
            capturing.pause(
                object : ShutDownFinishedHandler(MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED) {
                    override fun shutDownFinished(measurementIdentifier: Long) {
                        // The measurement id should always be set [STAD-333]
                        Validate.isTrue(measurementIdentifier != -1L, "Missing measurement id")
                        setCapturingStatus(MeasurementStatus.PAUSED)
                        viewModel.setMeasurementId(null)
                    }
                })
        } catch (e: NoSuchMeasurementException) {
            throw IllegalStateException(e)
        }

        // Pause camera capturing if it is running (even is DCS is not running)
        // TODO: this way we don't notice when the camera was stopped unexpectedly
        /*cameraService.isRunning(
            DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS,
            object : IsRunningCallback {
                override fun isRunning() {
                    cameraService.pause(object : ShutDownFinishedHandler(
                        de.cyface.camera_service.MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED
                    ) {
                        override fun shutDownFinished(measurementIdentifier: Long) {
                            Log.d(
                                de.cyface.app.utils.Constants.TAG,
                                "pauseCapturing: CameraService stopped"
                            )
                        }
                    })
                }

                override fun timedOut() {
                    Log.d(
                        de.cyface.camera_service.Constants.TAG,
                        "pauseCapturing: no CameraService running, nothing to do"
                    )
                }
            })*/
    }

    /**
     * Stop capturing
     */
    private fun stopCapturing() {
        try {
            capturing.stop(
                object : ShutDownFinishedHandler(MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED) {
                    override fun shutDownFinished(measurementIdentifier: Long) {
                        // The measurement id should always be set [STAD-333]
                        Validate.isTrue(measurementIdentifier != -1L, "Missing measurement id")
                        viewModel.setTracks(null)
                        setCapturingStatus(MeasurementStatus.FINISHED)
                        viewModel.setMeasurementId(null)
                    }
                })
        } catch (e: NoSuchMeasurementException) {
            throw IllegalStateException(e)
        }

        // Stop camera capturing if it is running (even is DCS is not running)
        // TODO: this way we don't notice when the camera was stopped unexpectedly
        /* cameraService.isRunning(
            DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT,
            TimeUnit.MILLISECONDS,
            object : IsRunningCallback {
                override fun isRunning() {
                    cameraService.stop(object : ShutDownFinishedHandler(
                        de.cyface.camera_service.MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED
                    ) {
                        override fun shutDownFinished(measurementIdentifier: Long) {
                            Log.d(
                                de.cyface.app.utils.Constants.TAG,
                                "stopCapturing: CameraService stopped"
                            )
                        }
                    })
                }

                override fun timedOut() {
                    Log.d(
                        de.cyface.camera_service.Constants.TAG,
                        "stopCapturing: no CameraService running, nothing to do"
                    )
                }
            })*/
    }

    /**
     * Starts capturing
     */
    private fun startCapturing() {

        // Measurement is stopped, so we start a new measurement
        if (persistence.hasMeasurement(MeasurementStatus.OPEN) && isProblematicManufacturer) {
            showToastOnMainThread(
                getString(de.cyface.app.utils.R.string.toast_last_tracking_crashed),
                true
            )
        }

        // We use a handler to run the UI Code on the main thread as it is supposed to be
        runOnUiThread {
            val calibrationProgressDialog = createAndShowCalibrationDialog()
            scheduleProgressDialogDismissal(
                calibrationProgressDialog!!,
                calibrationDialogListener!!
            )
        }

        // TODO [CY-3855]: we have to provide a listener for the button (<- ???)
        try {
            viewModel.setTracks(arrayListOf(Track()))
            capturing.start(Modality.BICYCLE,
                object : StartUpFinishedHandler(MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED) {
                    override fun startUpFinished(measurementIdentifier: Long) {
                        // The measurement id should always be set [STAD-333]
                        Validate.isTrue(measurementIdentifier != -1L, "Missing measurement id")
                        Log.v(TAG, "startUpFinished")
                        viewModel.setMeasurementId(measurementIdentifier)
                        // TODO: Status should also be in ViewModel (maybe via currMId and observed M)
                        // the button should then just change on itself based on the live data measurement
                        setCapturingStatus(MeasurementStatus.OPEN)

                        // Start CameraService
                        /* if (isCameraServiceRequested()) {
                            Log.d(de.cyface.camera_service.Constants.TAG, "CameraServiceRequested")
                            try {
                                startCameraService(measurementIdentifier)
                            } catch (e: DataCapturingException) {
                                throw IllegalStateException(e)
                            } catch (e: MissingPermissionException) {
                                throw IllegalStateException(e)
                            }
                        }*/
                    }
                })
        } catch (e: DataCapturingException) {
            throw IllegalStateException(e)
        } catch (e: MissingPermissionException) {
            throw IllegalStateException(e)
        }
    }

    /**
     * Resumes capturing
     */
    private fun resumeCapturing() {
        Log.d(TAG, "resumeCachedTrack: Adding new sub track to existing cached track")
        viewModel.addTrack(Track())
        try {
            capturing.resume(
                object : StartUpFinishedHandler(MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED) {
                    override fun startUpFinished(measurementIdentifier: Long) {
                        // The measurement id should always be set [STAD-333]
                        Validate.isTrue(measurementIdentifier != -1L, "Missing measurement id")
                        Log.v(TAG, "resumeCapturing: startUpFinished")
                        viewModel.setMeasurementId(measurementIdentifier)
                        setCapturingStatus(MeasurementStatus.OPEN)

                        // Start CameraService
                        /* if (isCameraServiceRequested()) {
                            Log.d(de.cyface.camera_service.Constants.TAG, "CameraServiceRequested")
                            try {
                                startCameraService(measurementIdentifier)
                            } catch (e: DataCapturingException) {
                                throw java.lang.IllegalStateException(e)
                            } catch (e: MissingPermissionException) {
                                throw java.lang.IllegalStateException(e)
                            }
                        }*/
                    }
                })
        } catch (e: NoSuchMeasurementException) {
            throw java.lang.IllegalStateException(e)
        } catch (e: DataCapturingException) {
            throw java.lang.IllegalStateException(e)
        } catch (e: MissingPermissionException) {
            throw java.lang.IllegalStateException(e)
        }
    }

    private fun isRestrictionActive(): Boolean {
        if (context == null) {
            Log.w(TAG, "Context is null, restrictions cannot be checked")
            return false
        }
        if (activity == null) {
            Log.w(TAG, "Activity is null. If needed, dialogs wont appear.")
        }
        if (!DiskConsumption.spaceAvailable()) {
            showToastOnMainThread(
                getString(de.cyface.app.utils.R.string.error_message_capturing_canceled_no_space),
                false
            )
            setButtonEnabled(startResumeButton)
            return true
        }
        if (!isGnssEnabled(requireContext())) {
            showGnssWarningDialog(activity)
            setButtonEnabled(startResumeButton)
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isEnergySaferActive(requireContext())) {
            showEnergySaferWarningDialog(activity)
            setButtonEnabled(startResumeButton)
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isBackgroundProcessingRestricted(
                requireContext()
            )
        ) {
            showRestrictedBackgroundProcessingWarningDialog(activity)
            setButtonEnabled(startResumeButton)
            return true
        }
        return false
    }

    /**
     * Creates and shows [ProgressDialog] to inform the user about calibration at the beginning of a
     * measurement.
     *
     * @return A reference to the ProgressDialog which can be used to dismiss it.
     */
    private fun createAndShowCalibrationDialog(): ProgressDialog? {
        return ProgressDialog.show(
            context,
            getString(de.cyface.app.utils.R.string.title_dialog_starting_data_capture),
            getString(de.cyface.app.utils.R.string.msg_calibrating),
            true,
            false
        ) {
            try {
                capturing
                    .stop(object : ShutDownFinishedHandler(
                        MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED
                    ) {
                        override fun shutDownFinished(l: Long) {
                            // nothing to do
                        }
                    })
                /* if (isCameraServiceRequested()) {
                    cameraService.stop(object : ShutDownFinishedHandler(
                        de.cyface.camera_service.MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED
                    ) {
                        override fun shutDownFinished(l: Long) {
                            // nothing to do
                        }
                    })
                }*/
            } catch (e: NoSuchMeasurementException) {
                throw java.lang.IllegalStateException(e)
            }
        }
    }

    /**
     * Dismisses a (calibration) ProgressDialog and informs the [CalibrationDialogListener]s
     * about the dismissal.
     *
     * @param progressDialog The [ProgressDialog] to dismiss
     * @param calibrationDialogListener The [<] to inform
     */
    private fun scheduleProgressDialogDismissal(
        progressDialog: ProgressDialog,
        calibrationDialogListener: Collection<CalibrationDialogListener>
    ) {
        val dialogTimeoutMillis = 1500L
        Handler().postDelayed(
            { dismissCalibrationDialog(progressDialog, calibrationDialogListener) },
            dialogTimeoutMillis
        )
    }

    private fun dismissCalibrationDialog(
        progressDialog: ProgressDialog?,
        calibrationDialogListener: Collection<CalibrationDialogListener>
    ) {
        if (progressDialog != null) {
            progressDialog.dismiss()
            for (calibrationDialogListener1 in calibrationDialogListener) {
                calibrationDialogListener1.onCalibrationDialogFinished()
            }
        }
    }

    /**
     * Loads the track of the currently captured measurement from the database.
     *
     * This is required when the app is resumed after being in background and probably missing locations
     * and when the app is restarted.
     *
     * @param measurementId The id of the currently active measurement.
     */
    private fun updateCachedTrack(measurementId: Long) {
        try {
            if (!persistence.hasMeasurement(MeasurementStatus.OPEN)
                && !persistence.hasMeasurement(MeasurementStatus.PAUSED)
            ) {
                Log.d(TAG, "updateCachedTrack: No unfinished measurement found, un-setting cache.")
                viewModel.setTracks(null)
                return
            }
            Log.d(
                TAG,
                "updateCachedTrack: Unfinished measurement found, loading track from database."
            )
            val loadedList = persistence.loadTracks(
                measurementId,
                DefaultLocationCleaning()
            )
            // We need to make sure we return a list which supports "add" even when an empty list is returned
            // or else the onHostResume method cannot add a new sub track to a loaded empty list
            viewModel.setTracks(ArrayList(loadedList))
        } catch (e: NoSuchMeasurementException) {
            throw java.lang.RuntimeException(e)
        }
    }

    /**
     * Shows a toast message explicitly on the main thread.
     *
     * @param toastMessage The message to show
     * @param longDuration `True` if the toast should be shown for a longer time
     */
    private fun showToastOnMainThread(toastMessage: String, longDuration: Boolean) {
        Handler(Looper.getMainLooper()).post {
            Toast
                .makeText(
                    context,
                    toastMessage,
                    if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                ).show()
        }
    }

    // Supplies the fragments to the ViewPager
    private class PagerAdapter(fragmentManager: FragmentManager?, lifecycle: Lifecycle?) :
        FragmentStateAdapter(
            fragmentManager!!, lifecycle!!
        ) {
        override fun createFragment(position: Int): Fragment {
            // Ordered list, make sure titles list match this order
            return if (position == 0) {
                MapFragment()
            } else if (position == 1 && !MarkerFragment.eventPassed()) {
                MarkerFragment()
            } else {
                SpeedFragment()
            }
        }

        override fun getItemCount(): Int {
            // List size
            return if (MarkerFragment.eventPassed()) 2 else 3
        }
    }

    class OnStartResumeListener(private val capturingFragment: CapturingFragment) :
        View.OnClickListener {
        override fun onClick(v: View?) {
            // Disables the button. This is used to avoid a duplicate start command which crashes the SDK
            // until CY-4098 is implemented. It's automatically re-enabled as soon as the callback arrives.
            capturingFragment.startResumeButton.isEnabled = false

            // The MaterialDialog implementation of the EnergySettings dialogs are not shown when called
            // from inside the IsRunningCallback. Thus, we call it here for now instead of in startCapturing()
            val capturingStatus = capturingFragment.viewModel.capturing.value
            if ((capturingStatus == MeasurementStatus.FINISHED || capturingStatus == MeasurementStatus.PAUSED) && capturingFragment.isRestrictionActive()) {
                return
            }

            capturingFragment.capturing.isRunning(
                DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT,
                TimeUnit.MILLISECONDS,
                object : IsRunningCallback {
                    override fun isRunning() {
                        throw java.lang.IllegalStateException("Measurement is already running")
                    }

                    override fun timedOut() {
                        Validate.isTrue(
                            capturingStatus !== MeasurementStatus.OPEN,
                            "DataCapturingButton is out of sync."
                        )

                        // If Measurement is paused, resume the measurement
                        if (capturingFragment.persistence.hasMeasurement(MeasurementStatus.PAUSED)) {
                            capturingFragment.resumeCapturing()
                            return
                        }
                        capturingFragment.startCapturing()
                    }
                })
        }
    }

    class OnPauseListener(private val capturingFragment: CapturingFragment) : View.OnClickListener {
        override fun onClick(v: View?) {

            // Disables the button. This is used to avoid a duplicate pause/stop command which crashes the SDK
            // until CY-4098 is implemented. It's automatically re-enabled as soon as the callback arrives.
            capturingFragment.pauseButton.isEnabled = false
            capturingFragment.stopButton.isEnabled = false

            capturingFragment.capturing.isRunning(
                DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT,
                TimeUnit.MILLISECONDS,
                object : IsRunningCallback {
                    override fun isRunning() {
                        Validate.isTrue(
                            capturingFragment.viewModel.capturing.value === MeasurementStatus.OPEN,
                            "DataCapturingButton is out of sync."
                        )
                        capturingFragment.pauseCapturing()
                    }

                    override fun timedOut() {
                        throw java.lang.IllegalStateException("No Measurement is running")
                    }
                })
        }
    }

    class OnStopListener(private val capturingFragment: CapturingFragment) :
        View.OnClickListener {
        override fun onClick(v: View?) {
            // Disables the button. This is used to avoid a duplicate stop/pause command which crashes the SDK
            // until CY-4098 is implemented. It's automatically re-enabled as soon as the callback arrives.
            capturingFragment.stopButton.isEnabled = false
            capturingFragment.pauseButton.isEnabled = false

            capturingFragment.capturing.isRunning(
                DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT,
                TimeUnit.MILLISECONDS,
                object : IsRunningCallback {
                    override fun isRunning() {
                        Validate.isTrue(
                            capturingFragment.viewModel.capturing.value === MeasurementStatus.OPEN,
                            "DataCapturingButton is out of sync."
                        )
                        capturingFragment.stopCapturing()
                    }

                    override fun timedOut() {
                        // If Measurement is paused, stop the measurement
                        if (capturingFragment.persistence.hasMeasurement(MeasurementStatus.PAUSED)) {
                            Validate.isTrue(
                                capturingFragment.viewModel.capturing.value === MeasurementStatus.PAUSED,
                                "DataCapturingButton is out of sync."
                            )
                            capturingFragment.stopCapturing()
                            return
                        }
                        throw java.lang.IllegalStateException("No measurement is running")
                    }
                })
        }
    }
}
