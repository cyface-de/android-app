/*
 * Copyright 2023-2025 Cyface GmbH
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
package de.cyface.app.digural.capturing

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import de.cyface.app.digural.CameraServiceProvider
import de.cyface.app.digural.MainActivity
import de.cyface.app.digural.R
import de.cyface.app.digural.capturing.map.MapFragment
import de.cyface.app.digural.databinding.FragmentCapturingBinding
import de.cyface.app.digural.utils.Constants
import de.cyface.app.utils.CalibrationDialogListener
import de.cyface.app.utils.ServiceProvider
import de.cyface.camera_service.UIListener
import de.cyface.camera_service.background.camera.CameraListener
import de.cyface.camera_service.foreground.AnnotationsWriter
import de.cyface.camera_service.foreground.CameraService
import de.cyface.camera_service.foreground.NoAnnotationsFound
import de.cyface.camera_service.settings.CameraSettings
import de.cyface.camera_service.settings.CameraSettings.Companion.categoryConverterForModel
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
import de.cyface.utils.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit


/**
 * This is the UI controller/element, responsible for displaying the data from the [CapturingViewModel].
 *
 * It holds the `Observer` objects which control what happens when the `LiveData` changes.
 * The [ViewModel]s are responsible for holding the `LiveData` data.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 1.0.0
 */
class CapturingFragment : Fragment(), DataCapturingListener, CameraListener {

    /**
     * This property is only valid between onCreateView and onDestroyView.
     */
    private var _binding: FragmentCapturingBinding? = null

    /**
     * The generated class which holds all bindings from the layout file.
     */
    private val binding get() = _binding!!

    /**
     * The `DataCapturingService` which represents the API of the Cyface Android SDK.
     */
    private lateinit var capturing: CyfaceDataCapturingService

    /**
     * The [CameraService] required to control and check the visual capturing process.
     */
    private lateinit var cameraService: CameraService

    /**
     * The settings used by both, UIs and libraries.
     */
    private lateinit var appSettings: AppSettings

    /**
     * The settings used by the camera service.
     */
    private lateinit var cameraSettings: CameraSettings

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
        // With async in onCreate the app crashes as late-init `capturing` is not initialized yet.
        val reportErrors = runBlocking { appSettings.reportErrorsFlow.first() }
        CapturingViewModelFactory(
            persistence.measurementRepository!!,
            persistence.eventRepository!!,
            reportErrors
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
            appSettings = (activity as ServiceProvider).appSettings
            persistence = capturing.persistenceLayer
        } else {
            error("Context doesn't support the Fragment, implement `ServiceProvider`")
        }
        if (activity is CameraServiceProvider) {
            cameraService = (activity as CameraServiceProvider).cameraService
            cameraSettings = (activity as CameraServiceProvider).cameraSettings
        } else {
            error("Context doesn't support the Fragment, implement `CameraServiceProvider`")
        }
    }

    /**
     * All non-graphical initializations should go into onCreate (which might be called before Activity's onCreate
     * finishes). All view-related initializations go into onCreateView and final initializations which depend on the
     * Activity's onCreate and the fragment's onCreateView to be finished belong into the onActivityCreated method
     */
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
        lifecycleScope.launch { withContext(Dispatchers.IO) { showModalitySelectionDialogIfNeeded() } }

        // Update UI elements with the updates from the ViewModel
        viewModel.measurementId.observe(viewLifecycleOwner) {
            val tripTitle =
                if (it == null) null else getString(de.cyface.app.utils.R.string.trip_id, it)
            binding.tripTitle.text = tripTitle ?: ""
        }
        viewModel.measurement.observe(viewLifecycleOwner) {
            val visibility = if (it == null) INVISIBLE else VISIBLE
            binding.speedTitle.visibility = visibility
            binding.distanceTitle.visibility = visibility
            binding.durationTitle.visibility = visibility
            binding.ascendTitle.visibility = visibility
            //binding.co2Title.visibility = visibility
            binding.speedView.visibility = visibility
            binding.distanceView.visibility = visibility
            binding.durationView.visibility = visibility
            binding.ascendView.visibility = visibility
            //binding.co2View.visibility = visibility

            // If code duplication increases, we could reduce it by introducing a domain layer
            // see https://developer.android.com/topic/architecture/domain-layer

            val distanceKm = it?.distance?.div(1000.0)
            binding.distanceView.text =
                if (distanceKm == null) "" else getString(
                    de.cyface.app.utils.R.string.distanceKm,
                    distanceKm
                )

            // 95 g / km see https://de.statista.com/infografik/25742/durchschnittliche-co2-emission-von-pkw-in-deutschland-im-jahr-2020/
            /*val co2Kg = distanceKm?.times(95)?.div(1000)
            binding.co2View.text =
                if (co2Kg == null) "" else getString(de.cyface.app.utils.R.string.co2kg, co2Kg)*/

            lifecycleScope.launch { updateDurationView(it?.id) }
        }
        viewModel.location.observe(viewLifecycleOwner) {
            lifecycleScope.launch { updateLocationViews(it) }
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
                requireActivity() as MainActivity,
                navHostFragment.navController
            ),
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )

        // Dynamically add tabs
        val tabLayout = binding.tabLayout
        tabLayout.addTab(tabLayout.newTab())

        return root
    }

    private suspend fun updateDurationView(measurementId: Long?) {
        if (measurementId == null) return
        val millis = withContext(Dispatchers.IO) { persistence.loadDuration(measurementId) }
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        val durationText = buildString {
            if (hours > 0) append(getString(de.cyface.app.utils.R.string.hours, hours)).append(" ")
            if (minutes > 0) append(getString(de.cyface.app.utils.R.string.minutes, minutes % 60)).append(" ")
            append(getString(de.cyface.app.utils.R.string.seconds, seconds % 60))
        }

        withContext(Dispatchers.Main) {
            binding.durationView.text = durationText
        }
    }

    private suspend fun updateLocationViews(location: ParcelableGeoLocation?) {
        try {
            val measurement = withContext(Dispatchers.IO) { persistence.loadCurrentlyCapturedMeasurement() }
            val averageSpeedKmh = withContext(Dispatchers.IO) {
                persistence.loadAverageSpeed(measurement.id, DefaultLocationCleaning()) * 3.6
            }

            val ongoingCapturing = measurement.status == MeasurementStatus.OPEN
            val ascend = if (ongoingCapturing) {
                withContext(Dispatchers.IO) { persistence.loadAscend(measurement.id) }
            } else null
            val ascendText = getString(de.cyface.app.utils.R.string.ascendMeters, ascend ?: 0.0)
            val speedKmPh = location?.speed?.times(3.6)

            withContext(Dispatchers.Main) {
                binding.speedView.text = if (speedKmPh == null) "" else getString(
                    de.cyface.app.utils.R.string.speedKphWithAverage,
                    speedKmPh,
                    averageSpeedKmh
                )
                binding.ascendView.text = if (speedKmPh == null) "" else ascendText
            }
        } catch (e: NoSuchMeasurementException) {
            // Happen when locations arrive late
            Log.d(TAG, "Position changed while no capturing is active, ignoring.")
        }
    }

    private suspend fun showModalitySelectionDialogIfNeeded() {
        registerModalityTabSelectionListener()
        val modality = Modality.valueOf(appSettings.modalityFlow.first())
        if (modality != Modality.UNKNOWN) {
            selectModalityTab()
            return
        }
        val fragmentManager = fragmentManager
        requireNotNull(fragmentManager)
        val dialog = ModalityDialog(appSettings)
        dialog.setTargetFragment(this, DIALOG_INITIAL_MODALITY_SELECTION_REQUEST_CODE)
        dialog.isCancelable = false
        dialog.show(fragmentManager, "MODALITY_DIALOG")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Allows to change the tabs by swiping horizontally and handles animation
        val viewPager = view.findViewById<ViewPager2>(R.id.pager)
        // Tab Swiping disabled to ease touch-control in the Google Map
        viewPager.isUserInputEnabled = false

        // Add TabItems to TabLayout
        val tabLayout: TabLayout = view.findViewById(R.id.tabLayout)

        // Connect adapter to ViewPager (which provides pages to the view pager)
        val fragmentManager: FragmentManager = childFragmentManager
        val adapter = PagerAdapter(fragmentManager, lifecycle)
        viewPager.adapter = adapter

        // Hide the TabLayout if there's only one tab and adjust constraints accordingly
        if (adapter.itemCount == 1) {
            tabLayout.visibility = View.GONE

            // Adjust constraints programmatically to remove space taken by the hidden TabLayout
            val constraintLayout = view as ConstraintLayout
            val constraintSet = ConstraintSet()
            constraintSet.clone(constraintLayout)
            constraintSet.clear(viewPager.id, ConstraintSet.TOP) // Remove the top constraint
            constraintSet.connect(
                viewPager.id,
                ConstraintSet.TOP,
                ConstraintSet.PARENT_ID,
                ConstraintSet.TOP
            ) // Connect ViewPager2 directly to the parent top
            constraintSet.applyTo(constraintLayout)
        } else {
            // Connect TabLayout to Adapter
            tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    viewPager.currentItem = tab.position
                }

                override fun onTabUnselected(tab: TabLayout.Tab) { /* Nothing to do */
                }

                override fun onTabReselected(tab: TabLayout.Tab) { /* Nothing to do */
                }
            })
        }

        // `reconnect()` is called in `onResume()` which is called after `onViewCreated`
    }


    private fun registerModalityTabSelectionListener() {
        val tabLayout = binding.modalityTabs
        val newModality = arrayOfNulls<Modality>(1)
        tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                lifecycleScope.launch {
                    val oldModalityId = appSettings.modalityFlow.first()
                    val oldModality = Modality.valueOf(oldModalityId)
                    when (tab.position) {
                        0 -> newModality[0] = Modality.CAR
                        1 -> newModality[0] = Modality.BICYCLE
                        2 -> newModality[0] = Modality.WALKING
                        3 -> newModality[0] = Modality.BUS
                        4 -> newModality[0] = Modality.TRAIN
                        else -> throw IllegalArgumentException("Unknown tab selected: " + tab.position)
                    }
                    appSettings.setModality(newModality[0]!!.databaseIdentifier)
                    if (oldModality == newModality[0]) {
                        Log.d(
                            TAG,
                            "changeModalityType(): old (" + oldModality + " and new Modality (" + newModality[0]
                                    + ") types are equal not recording event."
                        )
                        return@launch
                    }
                    capturing.changeModalityType(newModality[0]!!)

                    // Deactivated for pro app until we show them their own tiles:
                    // if (map != null) { map.loadCyfaceTiles(newModality[0].getDatabaseIdentifier()); }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                // Nothing to do here
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
                // Nothing to do here
            }
        })
    }

    /**
     * We use the activity result method as a callback from the `Modality` dialog to the main fragment
     * to setup the tabs as soon as a [Modality] type is selected the first time
     *
     * @param requestCode is used to differentiate and identify requests
     * @param resultCode is used to describe the request's result
     * @param data an intent which may contain result data
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DIALOG_INITIAL_MODALITY_SELECTION_REQUEST_CODE) {
            lifecycleScope.launch { withContext(Dispatchers.IO) { selectModalityTab() } }
        }
    }

    /**
     * Depending on which [Modality] is selected in the preferences the respective tab is selected.
     * Also, the tiles relative to the selected `Modality` are loaded onto the map, if enabled.
     *
     * Make sure the order (0, 1, 2 from left(start) to right(end)) in the TabLayout xml file is consistent
     * with the order here to map the correct enum to each tab.
     */
    private suspend fun selectModalityTab() {
        val tabLayout = binding.modalityTabs

        // Select the Modality tab
        val tab: TabLayout.Tab? = when (val modality = appSettings.modalityFlow.first()) {
            Modality.CAR.name -> {
                tabLayout.getTabAt(0)
            }

            Modality.BICYCLE.name -> {
                tabLayout.getTabAt(1)
            }

            Modality.WALKING.name -> {
                tabLayout.getTabAt(2)
            }

            Modality.BUS.name -> {
                tabLayout.getTabAt(3)
            }

            Modality.TRAIN.name -> {
                tabLayout.getTabAt(4)
            }

            else -> {
                throw IllegalArgumentException("Unknown Modality id: $modality")
            }
        }
        requireNotNull(tab)
        tab.select()
    }

    override fun onPause() {
        super.onPause()
        disconnect()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: reconnecting ...")
        lifecycleScope.launch(Dispatchers.IO) { reconnect() }
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

    override fun onLowDiskSpace(allocation: DiskConsumption) {
        // Nothing to do here - handled by [CapturingEventHandler]
    }

    override fun onCameraLowDiskSpace(allocation: DiskConsumption) {
        // Nothing to do here - handled by [CapturingEventHandler]
    }

    override fun onErrorState(e: Exception) {
        throw java.lang.IllegalStateException(e)
    }

    override fun onCameraErrorState(e: Exception) {
        throw java.lang.IllegalStateException(e)
    }

    override fun onRequiresPermission(
        permission: String,
        reason: Reason
    ): Boolean {
        return false
    }

    override fun onCameraRequiresPermission(permission: String, reason: Reason): Boolean {
        return false
    }

    override fun onCapturingStopped() {
        // Between roughly SDK 6.4.2 and 7.12.11 we used local broadcasts, which broke the
        // ShutdownFinishedHandler [LEIP-299]. As we didn't find the issue at first, we used
        // onCapturingStopped() as a workaround to update the buttons to "FINISHED".
        // After fixing this bug the SDK now only calls onCapturingStopped() when the capturing
        // stopped itself, e.g. because of low space, but not when the user asks it to stop.
        // This way we can set the UI to "FINISHED" here without interfering with the
        // ShutdownFinishedHandler, as this would "hide" a broken handler without intention.

        Log.d(TAG, "onCapturingStopped")
        checkAndStopCameraCapturing(capturingStopped = true, updateUi = true, pause = false)
    }

    override fun onCameraCapturingStopped() {
        Log.d(TAG, "onCameraCapturingStopped")
        checkAndStopCapturing(crashAsyncUI = false, pause = false)
    }


    override fun onSynchronizationSuccessful() {
        // Nothing to do here
    }

    /**
     * Checks the current capturing state and refreshes the UI elements accordingly.
     */
    private suspend fun reconnect() {
        capturing.addDataCapturingListener(this)
        cameraService.addCameraListener(this)

        // To avoid blocking the UI when switching Tabs, this is implemented in an async way.
        // I.e. we disable all buttons as the capturingState is set in the callback.
        withContext(Dispatchers.Main) {
            startResumeButton.isEnabled = false
            pauseButton.isEnabled = false
            stopButton.isEnabled = false
        }
        // OPEN: running capturing
        if (capturing.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT)) {
            Log.d(TAG, "onResume: reconnecting DCS succeeded")
            val (id) = capturing.loadCurrentlyCapturedMeasurement()
            viewModel.setMeasurementId(id)
            // We re-sync the button here as the data capturing can be canceled while the app is closed
            setCapturingStatus(MeasurementStatus.OPEN)
            updateCachedTrack(id)

            // Also try to reconnect to CameraService if it's alive
            if (cameraService.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT)) {
                // It does not matter whether isCameraServiceRequested() as this can change all the time
                Log.d(TAG, "onResume: reconnecting CameraService succeeded")
            }
            return
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
        if (cameraService.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT)) {
            Log.w(
                Constants.TAG,
                "Zombie CameraService is running and it's "
                        + (if (cameraSettings.cameraEnabledFlow.first()) "" else "*not*")
                        + " requested"
            )
            cameraService.stop(
                object :
                    ShutDownFinishedHandler(
                        de.cyface.camera_service.MessageCodes.GLOBAL_BROADCAST_SERVICE_STOPPED
                    ) {
                    override fun shutDownFinished(measurementIdentifier: Long) {
                        Log.d(
                            TAG,
                            "onResume: zombie CameraService stopped"
                        )

                        // This needs to be done in on finished handler, or else
                        // the log files are not yet created (done in `close()`)
                        lifecycleScope.launch {
                            mergeAnnotationsJson(measurementIdentifier)
                        }
                    }
                })
            error(
                "Camera stopped manually as the camera was not released. This should not happen!"
            )
        }
    }

    /**
     * Releases resources by unregistering listeners which are re-registered [onResume].
     */
    private fun disconnect() {
        capturing.removeDataCapturingListener(this)
        cameraService.removeCameraListener(this)

        try {
            capturing.disconnect()
        } catch (e: DataCapturingException) {
            // This just tells us there is no running capturing in the background, see [MOV-588]
            Log.d(TAG, "No need to unbind as the background service was not running.", e)
        }
        try {
            cameraService.disconnect()
        } catch (e: DataCapturingException) {
            // This just tells us there is no running capturing in the background, see [MOV-588]
            Log.d(TAG, "No need to unbind as the camera background service was not running.", e)
        }

        viewModel.setMeasurementId(null) // Experimental, might influence other Fragments
        //setCapturingStatus(null) // Seems not necessary right not, but is called in `reconnect`
        //viewModel.setTracks(null) // Seems not necessary right not, but is called in `reconnect`
    }

    /**
     * Updates the "is enabled" state of the button on the ui thread.
     *
     * @param button The [Button] to access the [Activity] to run code on the UI thread
     */
    private fun setButtonEnabled(button: ImageButton) {
        button.isEnabled = true
    }

    /**
     * Enables/disables and/or sets the [Button] icon to indicate the next reachable state.
     *
     * @param newStatus the new status of the measurement
     */
    private suspend fun setCapturingStatus(newStatus: MeasurementStatus) {
        viewModel.setCapturing(newStatus)
        withContext(Dispatchers.Main) { updateButtonView(newStatus) }
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
     * Stop capturing
     */
    private suspend fun stopCapturing(pause: Boolean) {
        try {
            val finishedHandler =
                object : ShutDownFinishedHandler(MessageCodes.GLOBAL_BROADCAST_SERVICE_STOPPED) {
                    override fun shutDownFinished(measurementIdentifier: Long) {
                        // The measurement id should always be set [STAD-333]
                        require(measurementIdentifier != -1L) { "Missing measurement id" }

                        // Ensures both services stopped to avoid btn out of sync [LEIP-299]
                        val target = if (pause) "pause" else "stop"
                        Log.d(TAG, "stopCapturing: $target finished")
                        checkAndStopCameraCapturing(capturingStopped = true, updateUi = true, pause)
                    }
                }
            if (pause) {
                capturing.pause(finishedHandler)
            } else {
                capturing.stop(finishedHandler)
            }
        } catch (e: NoSuchMeasurementException) {
            throw IllegalStateException(e)
        }
    }

    /**
     * Starts capturing
     */
    private suspend fun startCapturing() {
        // Measurement is stopped, so we start a new measurement
        if (persistence.hasMeasurement(MeasurementStatus.OPEN) && isProblematicManufacturer) {
            withContext(Dispatchers.Main) {
                showToast(
                    getString(de.cyface.app.utils.R.string.toast_last_tracking_crashed),
                    true
                )
            }
        }

        // We use a handler to run the UI Code on the main thread as it is supposed to be
        withContext(Dispatchers.Main) {
            val calibrationProgressDialog = createAndShowCalibrationDialog()
            scheduleProgressDialogDismissal(
                calibrationProgressDialog!!,
                calibrationDialogListener!!
            )
        }

        // TODO [CY-3855]: we have to provide a listener for the button (<- ???)
        try {
            viewModel.setTracks(mutableListOf(Track()))
            capturing.start(Modality.BICYCLE,
                object : StartUpFinishedHandler(MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED) {
                    override fun startUpFinished(measurementIdentifier: Long) {
                        // The measurement id should always be set [STAD-333]
                        require(measurementIdentifier != -1L) { "Missing measurement id" }
                        Log.v(TAG, "startUpFinished")
                        viewModel.setMeasurementId(measurementIdentifier)
                        // TODO: Status should also be in ViewModel (maybe via currMId and observed M)
                        // the button should then just change on itself based on the live data measurement
                        lifecycleScope.launch(Dispatchers.IO) { setCapturingStatus(MeasurementStatus.OPEN) }

                        // Start CameraService
                        lifecycleScope.launch {
                            if (withContext(Dispatchers.IO) { cameraSettings.cameraEnabledFlow.first() }) {
                                Log.d(TAG, "CameraServiceRequested")
                                try {
                                    startCameraService(measurementIdentifier)
                                } catch (e: DataCapturingException) {
                                    throw IllegalStateException(e)
                                } catch (e: MissingPermissionException) {
                                    throw IllegalStateException(e)
                                }
                            }
                        }
                    }
                })
        } catch (e: DataCapturingException) {
            throw IllegalStateException(e)
        } catch (e: MissingPermissionException) {
            Toast.makeText(
                requireContext(),
                getString(de.cyface.app.utils.R.string.missing_location_permissions_toast),
                Toast.LENGTH_LONG
            ).show()
            throw IllegalStateException(e)
        }
    }

    /**
     * Resumes capturing
     */
    private suspend fun resumeCapturing() {
        Log.d(TAG, "resumeCachedTrack: Adding new sub track to existing cached track")
        viewModel.addTrack(Track())
        try {
            capturing.resume(
                object : StartUpFinishedHandler(MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED) {
                    override fun startUpFinished(measurementIdentifier: Long) {
                        // The measurement id should always be set [STAD-333]
                        require(measurementIdentifier != -1L) { "Missing measurement id" }
                        Log.v(TAG, "resumeCapturing: startUpFinished")
                        viewModel.setMeasurementId(measurementIdentifier)
                        lifecycleScope.launch(Dispatchers.IO) { setCapturingStatus(MeasurementStatus.OPEN) }

                        // Start CameraService
                        lifecycleScope.launch {
                            if (withContext(Dispatchers.IO) { cameraSettings.cameraEnabledFlow.first() }) {
                                Log.d(Constants.TAG, "CameraServiceRequested")
                                try {
                                    startCameraService(measurementIdentifier)
                                } catch (e: DataCapturingException) {
                                    throw java.lang.IllegalStateException(e)
                                } catch (e: MissingPermissionException) {
                                    throw java.lang.IllegalStateException(e)
                                }
                            }
                        }
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

    /**
     * Starts the camera service and, thus, the camera capturing.
     *
     * @param measurementId the id of the measurement for which camera data is to be captured
     * @throws DataCapturingException If the asynchronous background service did not start successfully or no valid
     * Android context was available.
     * @throws MissingPermissionException If no Android `ACCESS_FINE_LOCATION` has been granted. You may
     * register a [UIListener] to ask the user for this permission and prevent the
     * `Exception`. If the `Exception` was thrown the service does not start.
     */
    @Throws(DataCapturingException::class, MissingPermissionException::class)
    private suspend fun startCameraService(measurementId: Long) {
        val videoModeSelected = cameraSettings.videoModeFlow.first()
        val rawModeSelected = cameraSettings.rawModeFlow.first()
        // We need to load and pass the preferences for the camera focus here as the preferences
        // do not work reliably on multi-process access. https://stackoverflow.com/a/27987956/5815054
        val staticFocusSelected = cameraSettings.staticFocusFlow.first()
        val staticFocusDistance = cameraSettings.staticFocusDistanceFlow.first()
        val distanceBasedTriggeringSelected = cameraSettings.distanceBasedTriggeringFlow.first()
        val triggeringDistance = cameraSettings.triggeringDistanceFlow.first()
        val staticExposureTimeSelected = cameraSettings.staticExposureFlow.first()
        val staticExposureTime = cameraSettings.staticExposureTimeFlow.first()
        val exposureValueIso100 = cameraSettings.staticExposureValueFlow.first()
        val anonymizationModel = cameraSettings.anonModelFlow.first()

        // Usual cycling velocity in cities should be around 15-20 km/h, but outside the
        // city it can be up to 50 km/h. So we set the distance to 15m to ensure we stay
        // below the max image capturing frequency of 1 Hz for now.
        cameraService.start(
            measurementId,
            videoModeSelected,
            rawModeSelected,
            staticFocusSelected,
            staticFocusDistance,
            staticExposureTimeSelected,
            staticExposureTime,
            exposureValueIso100,
            distanceBasedTriggeringSelected,
            triggeringDistance,
            true,
            anonymizationModel,
            object :
                StartUpFinishedHandler(de.cyface.camera_service.MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED) {
                override fun startUpFinished(measurementIdentifier: Long) {
                    Log.v(Constants.TAG, "startCameraService: CameraService startUpFinished")
                }
            })
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
            showToast(
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
            lifecycle.coroutineScope.launch(Dispatchers.IO) {
                try {
                    capturing
                        .stop(object : ShutDownFinishedHandler(
                            MessageCodes.GLOBAL_BROADCAST_SERVICE_STOPPED
                        ) {
                            override fun shutDownFinished(measurementIdentifier: Long) {
                                // nothing to do
                            }
                        })
                    if (cameraSettings.cameraEnabledFlow.first()) {
                        cameraService.stop(
                            object : ShutDownFinishedHandler(
                                de.cyface.camera_service.MessageCodes.GLOBAL_BROADCAST_SERVICE_STOPPED
                            ) {
                                override fun shutDownFinished(measurementIdentifier: Long) {
                                    // This needs to be done in on finished handler, or else
                                    // the log files are not yet created (done in `close()`)
                                    lifecycleScope.launch {
                                        mergeAnnotationsJson(measurementIdentifier)
                                    }
                                }
                            })
                    }
                } catch (e: NoSuchMeasurementException) {
                    throw java.lang.IllegalStateException(e)
                }
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
    private suspend fun updateCachedTrack(measurementId: Long) {
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
            viewModel.setTracks(loadedList.toMutableList())
        } catch (e: NoSuchMeasurementException) {
            throw java.lang.RuntimeException(e)
        }
    }

    /**
     * Shows a toast message.
     *
     * @param toastMessage The message to show
     * @param longDuration `True` if the toast should be shown for a longer time
     */
    private fun showToast(toastMessage: String, longDuration: Boolean) {
        Toast
            .makeText(
                context,
                toastMessage,
                if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            ).show()
    }

    // Supplies the fragments to the ViewPager
    private class PagerAdapter(fragmentManager: FragmentManager?, lifecycle: Lifecycle?) :
        FragmentStateAdapter(fragmentManager!!, lifecycle!!) {
        override fun createFragment(position: Int): Fragment {
            // Ordered list, make sure titles list match this order
            require(position == 0)
            return MapFragment()
        }

        override fun getItemCount(): Int {
            return 1
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
            if ((capturingStatus == MeasurementStatus.FINISHED ||
                        capturingStatus == MeasurementStatus.PAUSED) &&
                capturingFragment.isRestrictionActive()) {
                return
            }

            capturingFragment.capturing.isRunning(
                DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT,
                TimeUnit.MILLISECONDS,
                object : IsRunningCallback {
                    override fun isRunning() {
                        error("Measurement is already running")
                    }

                    override fun timedOut() {
                        require(capturingStatus !== MeasurementStatus.OPEN) {
                            "DataCapturingButton is out of sync."
                        }

                        // If Measurement is paused, resume the measurement
                        capturingFragment.lifecycleScope.launch(Dispatchers.IO) {
                            if (capturingFragment.persistence.hasMeasurement(MeasurementStatus.PAUSED)) {
                                capturingFragment.resumeCapturing()
                                return@launch
                            }
                            capturingFragment.startCapturing()
                        }
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
            Log.d(TAG, "OnPauseListener")
            capturingFragment.checkAndStopCapturing(crashAsyncUI = true, pause = true)
        }
    }

    class OnStopListener(private val capturingFragment: CapturingFragment) :
        View.OnClickListener {
        override fun onClick(v: View?) {
            // Disables the button. This is used to avoid a duplicate stop/pause command which crashes the SDK
            // until CY-4098 is implemented. It's automatically re-enabled as soon as the callback arrives.
            capturingFragment.stopButton.isEnabled = false
            capturingFragment.pauseButton.isEnabled = false
            Log.d(TAG, "OnStopListener")
            capturingFragment.checkAndStopCapturing(crashAsyncUI = true, pause = false)
        }
    }

    private fun checkAndStopCapturing(crashAsyncUI: Boolean, pause: Boolean) {
        capturing.isRunning(
            DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT,
            TimeUnit.MILLISECONDS,
            object : IsRunningCallback {
                override fun isRunning() {
                    if (crashAsyncUI) {
                        require(viewModel.capturing.value === MeasurementStatus.OPEN) {
                            "DataCapturingButton is out of sync."
                        }
                    }
                    lifecycleScope.launch(Dispatchers.IO) { stopCapturing(pause) }
                }

                override fun timedOut() {
                    // If Measurement is paused, stop the measurement
                    lifecycleScope.launch {
                        if (withContext(Dispatchers.IO) { persistence.hasMeasurement(MeasurementStatus.PAUSED) }) {
                            if (crashAsyncUI) {
                                require(!pause) { "Measurement is already paused." }
                                require(viewModel.capturing.value === MeasurementStatus.PAUSED) {
                                    "DataCapturingButton is out of sync."
                                }
                            }
                            withContext(Dispatchers.IO) { stopCapturing(pause) }
                            return@launch
                        } else {
                            error("No measurement is running")
                        }
                    }
                }
            })
    }

    private fun checkAndStopCameraCapturing(
        @Suppress("SameParameterValue") capturingStopped: Boolean,
        @Suppress("SameParameterValue") updateUi: Boolean,
        pause: Boolean,
    ) {
        cameraService.isRunning(
            DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT,
            TimeUnit.MILLISECONDS,
            object : IsRunningCallback {
                override fun isRunning() {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            cameraService.stop(
                                object : ShutDownFinishedHandler(
                                    de.cyface.camera_service.MessageCodes.GLOBAL_BROADCAST_SERVICE_STOPPED
                                ) {
                                    override fun shutDownFinished(measurementIdentifier: Long) {
                                        Log.d(TAG, "stopCapturing: CameraService stopped")
                                        if (capturingStopped) {
                                            Log.d(TAG, "DCS + CS stopped")
                                        } else {
                                            Log.d(TAG, "CS only stopped")
                                        }
                                        if (updateUi) {
                                            lifecycleScope.launch {
                                                if (pause) {
                                                    withContext(Dispatchers.IO) { setCapturingStatus(MeasurementStatus.PAUSED) }
                                                } else {
                                                    withContext(Dispatchers.Main) { viewModel.setTracks(null) }
                                                    withContext(Dispatchers.IO) { setCapturingStatus(MeasurementStatus.FINISHED) }
                                                }
                                            }
                                            viewModel.setLocation(null)
                                            viewModel.setMeasurementId(null)
                                        }

                                        if (!pause) {
                                            // This needs to be done in on finished handler, or else
                                            // the log files are not yet created (done in `close()`)
                                            lifecycleScope.launch {
                                                mergeAnnotationsJson(measurementIdentifier)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                override fun timedOut() {
                    Log.d(TAG, "stopCapturing: No CameraService running")
                    if (capturingStopped) {
                        Log.d(TAG, "DCS only stopped")
                    }
                    if (updateUi) {
                        lifecycleScope.launch {
                            if (pause) {
                                withContext(Dispatchers.IO) { setCapturingStatus(MeasurementStatus.PAUSED) }
                            } else {
                                withContext(Dispatchers.Main) { viewModel.setTracks(null) }
                                withContext(Dispatchers.IO) { setCapturingStatus(MeasurementStatus.FINISHED) }
                            }
                        }
                        viewModel.setLocation(null)
                        viewModel.setMeasurementId(null)
                    }
                }
            }
        )
    }

    private suspend fun mergeAnnotationsJson(measurementId: Long) {
        withContext(Dispatchers.IO) {
            val anonModel = cameraSettings.anonModelFlow.first()
            try {
                AnnotationsWriter(
                    measurementId = measurementId,
                    attachmentDao = persistence.attachmentDao!!,
                    measurementRepository = persistence.measurementRepository!!,
                    categoryConverter = categoryConverterForModel(anonModel)
                ).mergeJsonAndWriteToFile()
            } catch (e: NoAnnotationsFound) {
                Log.i(TAG, "Skip merging annotations.json, no annotations found")
            }
        }
    }

    override fun onNewPictureAcquired(picturesCaptured: Int) {
        Log.d(Constants.TAG, "onNewPictureAcquired")
        /*val text =
            context!!.getString(de.cyface.camera_service.R.string.camera_images) + " " + picturesCaptured
        cameraInfoTextView.setText(text)
        Log.d(TAG, "cameraInfoTextView: " + cameraInfoTextView.getText())*/
    }

    override fun onNewVideoStarted() {
        Log.d(Constants.TAG, "onNewVideoStarted")
    }

    override fun onVideoStopped() {
        Log.d(Constants.TAG, "onVideoStopped")
    }

    companion object {
        /**
         * The identifier for the [ModalityDialog] request which asks the user (initially) to select a
         * [Modality] preference.
         */
        const val DIALOG_INITIAL_MODALITY_SELECTION_REQUEST_CODE = 201909191

        /**
         * The tag used to identify logging from this class.
         */
        private const val TAG = "de.cyface.app.frag.main"
    }
}
