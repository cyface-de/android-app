package de.cyface.app.r4r.ui.capturing

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import de.cyface.app.r4r.MainActivity
import de.cyface.app.r4r.R
import de.cyface.app.r4r.ServiceProvider
import de.cyface.app.r4r.databinding.FragmentCapturingBinding
import de.cyface.app.r4r.ui.capturing.dialog.CalibrationDialogListener
import de.cyface.app.r4r.ui.capturing.map.MapFragment
import de.cyface.app.r4r.ui.capturing.speed.SpeedFragment
import de.cyface.app.r4r.utils.Constants.TAG
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
import de.cyface.persistence.model.Measurement
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
import kotlin.math.roundToInt

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

    private var _binding: FragmentCapturingBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var capturingStatus: MeasurementStatus? = null

    private lateinit var locationManager: LocationManager

    private lateinit var capturingService: CyfaceDataCapturingService

    private lateinit var persistenceLayer: DefaultPersistenceLayer<CapturingPersistenceBehaviour>

    // Get shared `ViewModel` instance from Activity
    private val capturingViewModel: CapturingViewModel by activityViewModels { CapturingViewModelFactory(persistenceLayer.measurementRepository!!)}

    //private val preferences: SharedPreferences? = null

    // When requested, this adapter returns a DemoObjectFragment,
    // representing an object in the collection.
    private lateinit var pagerAdapter: PagerAdapter

    /**
     * The pager widget, which handles animation and allows swiping horizontally
     * to access previous and next wizard steps.
     */
    private lateinit var viewPager: ViewPager2

    private lateinit var startResumeButton: FloatingActionButton
    private lateinit var stopButton: FloatingActionButton
    private lateinit var pauseButton: FloatingActionButton

    @Suppress("PrivatePropertyName")
    private val CALIBRATION_DIALOG_TIMEOUT = 1500L
    private var calibrationDialogListener: Collection<CalibrationDialogListener>? = null
    private var calibrationProgressDialog: ProgressDialog? = null

    private val onStartResume: View.OnClickListener = View.OnClickListener {
        // From examples:
        // Update LiveData upon user interaction, network responses, or data loading completion.
        // `setValue must be called from the main thread, use `postValue()` from worker threads.
        // capturingViewModel.insert(measurement)

        // Disables the button. This is used to avoid a duplicate start command which crashes the SDK
        // until CY-4098 is implemented. It's automatically re-enabled as soon as the callback arrives.
        startResumeButton.isEnabled = false

        // The MaterialDialog implementation of the EnergySettings dialogs are not shown when called
        // from inside the IsRunningCallback. Thus, we call it here for now instead of in startCapturing()
        if ((capturingStatus == MeasurementStatus.FINISHED || capturingStatus == MeasurementStatus.PAUSED) && isRestrictionActive()) {
            return@OnClickListener
        }

        capturingService.isRunning(
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
                    if (persistenceLayer.hasMeasurement(MeasurementStatus.PAUSED)) {
                        resumeCapturing()
                        return
                    }
                    startCapturing()
                }
            })
    }

    private val onPause: View.OnClickListener = View.OnClickListener {
        // Disables the button. This is used to avoid a duplicate pause/stop command which crashes the SDK
        // until CY-4098 is implemented. It's automatically re-enabled as soon as the callback arrives.
        pauseButton.isEnabled = false
        stopButton.isEnabled = false

        capturingService.isRunning(
            DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT,
            TimeUnit.MILLISECONDS,
            object : IsRunningCallback {
                override fun isRunning() {
                    Validate.isTrue(
                        capturingStatus === MeasurementStatus.OPEN,
                        "DataCapturingButton is out of sync."
                    )
                    pauseCapturing()
                }

                override fun timedOut() {
                    throw java.lang.IllegalStateException("No Measurement is running")
                }
            })
    }

    private val onStop: View.OnClickListener = View.OnClickListener {
        // Disables the button. This is used to avoid a duplicate stop/pause command which crashes the SDK
        // until CY-4098 is implemented. It's automatically re-enabled as soon as the callback arrives.
        stopButton.isEnabled = false
        pauseButton.isEnabled = false

        capturingService.isRunning(
            DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT,
            TimeUnit.MILLISECONDS,
            object : IsRunningCallback {
                override fun isRunning() {
                    Validate.isTrue(
                        capturingStatus === MeasurementStatus.OPEN,
                        "DataCapturingButton is out of sync."
                    )
                    stopCapturing()
                }

                override fun timedOut() {
                    // If Measurement is paused, stop the measurement
                    if (persistenceLayer.hasMeasurement(MeasurementStatus.PAUSED)) {
                        Validate.isTrue(
                            capturingStatus === MeasurementStatus.PAUSED,
                            "DataCapturingButton is out of sync."
                        )
                        stopCapturing()
                        return
                    }
                    throw java.lang.IllegalStateException("No measurement is running")
                }
            })
    }

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
        _binding = FragmentCapturingBinding.inflate(inflater, container, false)
        val root: View = binding.root
        startResumeButton = binding.startResumeButton
        stopButton = binding.stopButton
        pauseButton = binding.pauseButton

        // Update UI element with the updates from the ViewModel
        capturingViewModel.measurement.observe(viewLifecycleOwner) {
            binding.distanceView.text =
                "LIVE: ${if (it == null) "N/A" else (it!!.distance * 100).roundToInt() / 100.0} km"
        }
        capturingViewModel.location.observe(viewLifecycleOwner) {
            val speedMps = it?.speed
            val speedKmPh = speedMps?.times(3.6)?.roundToInt()
            val speedText = if (speedKmPh == null) null else "$speedKmPh km/h (Ø N/A km/h)"
            binding.speedView.text = speedText ?: ""
        }

        startResumeButton.setOnClickListener(onStartResume)
        pauseButton.setOnClickListener(onPause)
        stopButton.setOnClickListener(onStop)
        calibrationDialogListener = HashSet()
        locationManager =
            requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Instantiate a ViewPager2 and a PagerAdapter.
        viewPager = view.findViewById(R.id.pager)

        // Connect adapter to ViewPager (which provides pages to the view pager)
        val fragmentManager: FragmentManager = childFragmentManager
        pagerAdapter = PagerAdapter(fragmentManager, lifecycle)
        viewPager.adapter = pagerAdapter

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

        // Tab Swiping disabled to ease touch-control in the Google Map
        viewPager.isUserInputEnabled = false

        // `reconnect()` is called in `onResume()` which is called after `onViewCreated`
    }

    override fun onPause() {
        super.onPause()
        //FIXME: move the code below to `disconnect()` like `reconnect()`
        //FIXME: unsetLocationListener()
        capturingService.removeDataCapturingListener(this)
        //FIXME: cameraService.removeCameraListener(this)
    }

    /**
     * FIXME: From `DataCapturingButton.onResume`
     */
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

    override fun onNewGeoLocationAcquired(position: ParcelableGeoLocation?) {
        // FIXME: See DataCapturingButton
        capturingViewModel.setLocation(position)
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
        // Disabled on Android 13+ for workaround, see `stop/pauseCapturing()` [RFR-246]
        //if (Build.VERSION.SDK_INT < 33) { FIXME: see if bug is still here before uncommenting this
        setCapturingStatus(MeasurementStatus.FINISHED)
        capturingViewModel.setLocation(null)
        //}
    }

    /**
     * Checks the current capturing state and refreshes the UI elements accordingly.
     */
    private fun reconnect() {
        updateCachedTrack()
        capturingService.addDataCapturingListener(this)
        // FIXME: cameraService.addCameraListener(this)

        // To avoid blocking the UI when switching Tabs, this is implemented in an async way.
        // I.e. we disable all buttons as the capturingState is set in the callback.
        startResumeButton.isEnabled = false
        pauseButton.isEnabled = false
        stopButton.isEnabled = false
        GlobalScope.launch {
            // OPEN: running capturing
            if (capturingService.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT)) {
                Log.d(TAG, "onResume: reconnecting DCS succeeded")
                // FIXME: setLocationListener()
                // We re-sync the button here as the data capturing can be canceled while the app is closed
                setCapturingStatus(MeasurementStatus.OPEN)
                //setButtonEnabled(button)
                // Was disabled before, too: mainFragment.showUnfinishedTracksOnMap(persistenceLayer.loadCurrentlyCapturedMeasurement().getIdentifier());

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
            if (persistenceLayer.hasMeasurement(MeasurementStatus.PAUSED)) {
                setCapturingStatus(MeasurementStatus.PAUSED)
                // Was disabled before, too:  mainFragment.showUnfinishedTracksOnMap(persistenceLayer.loadCurrentlyCapturedMeasurement().getIdentifier());
            } else {
                setCapturingStatus(MeasurementStatus.FINISHED)
            }
            //setButtonEnabled(button)

            // Check if there is a zombie CameraService running
            // In case anything went wrong and the camera is still bound by this app we're releasing it so that it
            // can be used by other apps again
            /*FIXME: if (cameraService.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT)) {
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
     * Sets the [Button] icon to indicate the next reachable state after a click on the button.
     *
     * @param newStatus the new status of the measurement
     */
    private fun setCapturingStatus(newStatus: MeasurementStatus) {
        capturingStatus = newStatus
        runOnUiThread {
            updateButtonView(newStatus)
            // FIXME: updateOngoingCapturingInfo(newStatus)
            /*if (map != null) { FIXME
                if (newStatus === MeasurementStatus.OPEN) {
                    setLocationListener()
                } else {
                    unsetLocationListener()
                }
            }*/
        }
    }

    /**
     * Updates the `TextView`s depending on the current [MeasurementStatus].
     *
     * When a new Capturing is started, the `TextView` will only show the [Measurement.id]
     * of the open [Measurement]. The [Measurement.distance] is automatically updated as soon as the
     * first [ParcelableGeoLocation]s are captured. This way the user can see if the capturing actually works.
     *
     * @param status the state of the `DataCapturingButton`
     */
    private fun updateOngoingCapturingInfo(status: MeasurementStatus) {
        if (status === MeasurementStatus.OPEN) {
            // FIXME: We used `DefaultBehavior` before, but now `CapturingBehavior` is used
            val measurementIdText = (getString(R.string.measurement) + " "
                    + persistenceLayer.loadCurrentlyCapturedMeasurement().id)
            binding.tripTitle.text = measurementIdText
            // FIXME: cameraInfoTextView.setVisibility(View.VISIBLE)
        } else {
            // This way you can notice if a GeoLocation/Picture was already captured or not
            binding.distanceView.text = ""
            // Disabling or else the text is updated when JpegSafer handles image after capturing stopped
            //cameraInfoTextView.setText("")
            //cameraInfoTextView.setVisibility(View.INVISIBLE)
            binding.tripTitle.text = ""
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
        // noinspection ConstantConditions - this happened on Emulator N5X on app close
        /*if (startResumeButton == null || pauseButton == null || stopButton == null) {
            Log.w(TAG,"CapturingButton is null, not updating button status")
            return
        }*/
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

    /*@Throws(SecurityException::class)
    private fun setLocationListener() {
        // FIXME: locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 0f, map)
    }

    @Throws(SecurityException::class)
    private fun unsetLocationListener() {
        // FIXME: locationManager.removeUpdates(map)
    }*/

    /**
     * Pause capturing
     */
    private fun pauseCapturing() {
        try {
            capturingService.pause(
                object : ShutDownFinishedHandler(MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED) {
                    override fun shutDownFinished(measurementIdentifier: Long) {
                        // Disabled on Android 13+ for workaround, see `timeoutHandler` below [RFR-246]
                        if (Build.VERSION.SDK_INT < 33) {

                            // The measurement id should always be set [STAD-333]
                            Validate.isTrue(measurementIdentifier != -1L, "Missing measurement id")
                            setCapturingStatus(MeasurementStatus.PAUSED)
                            //setButtonEnabled(button)
                        }
                    }
                })

            // Workaround for flaky `rebind` on Android 13+ [RFR-246]
            // - Don't wait for `shutDownFinished` to be called (flaky due to the bug).
            // - Use a static 500ms delay to give the measurement some time to stop.
            if (Build.VERSION.SDK_INT >= 33) {
                val timeoutHandler = Handler(requireContext().mainLooper)
                timeoutHandler.postAtTime({
                    // The measurement id should always be set [STAD-333]
                    // Validate.isTrue(measurementIdentifier != -1, "Missing measurement id");
                    setCapturingStatus(MeasurementStatus.PAUSED)
                    //setButtonEnabled(button)
                }, SystemClock.uptimeMillis() + 500L)
            }
        } catch (e: NoSuchMeasurementException) {
            throw IllegalStateException(e)
        }

        // Pause camera capturing if it is running (even is DCS is not running)
        // TODO: this way we don't notice when the camera was stopped unexpectedly
        /*FIXME: cameraService.isRunning(
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
            capturingService.stop(
                object : ShutDownFinishedHandler(MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED) {
                    override fun shutDownFinished(measurementIdentifier: Long) {
                        // Disabled on Android 13+ for workaround, see `timeoutHandler` below [RFR-246]
                        if (Build.VERSION.SDK_INT < 33) {

                            // The measurement id should always be set [STAD-333]
                            Validate.isTrue(measurementIdentifier != -1L, "Missing measurement id")
                            capturingViewModel.resetCurrentMeasurementsTracks()
                            setCapturingStatus(MeasurementStatus.FINISHED)
                            //setButtonEnabled(button)
                        }
                    }
                })
            // FIXME: runOnUiThread { map.clearMap() }

            // Workaround for flaky `rebind` on Android 13+ [RFR-246]
            // - Don't wait for `shutDownFinished` to be called (flaky due to the bug).
            // - Use a static 500ms delay to give the measurement some time to stop.
            if (Build.VERSION.SDK_INT >= 33) {
                val timeoutHandler = Handler(requireContext().mainLooper)
                timeoutHandler.postAtTime({

                    // The measurement id should always be set [STAD-333]
                    // Validate.isTrue(measurementIdentifier != -1, "Missing measurement id");
                    capturingViewModel.resetCurrentMeasurementsTracks()
                    setCapturingStatus(MeasurementStatus.FINISHED)
                    //setButtonEnabled(button)
                }, SystemClock.uptimeMillis() + 500L)
            }
        } catch (e: NoSuchMeasurementException) {
            throw IllegalStateException(e)
        }

        // Stop camera capturing if it is running (even is DCS is not running)
        // TODO: this way we don't notice when the camera was stopped unexpectedly
        /* FIXME: cameraService.isRunning(
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
        if (persistenceLayer.hasMeasurement(MeasurementStatus.OPEN) && isProblematicManufacturer) {
            showToastOnMainThread(
                getString(R.string.toast_last_tracking_crashed),
                true
            )
        }

        // We use a handler to run the UI Code on the main thread as it is supposed to be
        runOnUiThread {
            calibrationProgressDialog = createAndShowCalibrationDialog()
            scheduleProgressDialogDismissal(
                calibrationProgressDialog!!,
                calibrationDialogListener!!
            )
        }

        // TODO [CY-3855]: we have to provide a listener for the button (<- ???)
        try {
            capturingViewModel.initializeCurrentMeasurementsTracks()
            capturingViewModel.addToCurrentMeasurementsTracks(Track())
            capturingService.start(Modality.BICYCLE,
                object : StartUpFinishedHandler(MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED) {
                    override fun startUpFinished(measurementIdentifier: Long) {
                        // The measurement id should always be set [STAD-333]
                        Validate.isTrue(measurementIdentifier != -1L, "Missing measurement id")
                        Log.v(TAG, "startUpFinished")
                        setCapturingStatus(MeasurementStatus.OPEN)
                        //setButtonEnabled(button)

                        // Start CameraService
                        /* FIXME: if (isCameraServiceRequested()) {
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
        capturingViewModel.addToCurrentMeasurementsTracks(Track())
        try {
            capturingService.resume(
                object : StartUpFinishedHandler(MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED) {
                    override fun startUpFinished(measurementIdentifier: Long) {
                        // The measurement id should always be set [STAD-333]
                        Validate.isTrue(measurementIdentifier != -1L, "Missing measurement id")
                        Log.v(TAG, "resumeCapturing: startUpFinished")
                        setCapturingStatus(MeasurementStatus.OPEN)
                        //setButtonEnabled(button)

                        // Start CameraService
                        /* FIXME: if (isCameraServiceRequested()) {
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
                getString(R.string.error_message_capturing_canceled_no_space),
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
            getString(R.string.title_dialog_starting_data_capture),
            getString(R.string.msg_calibrating),
            true,
            false
        ) {
            try {
                capturingService
                    .stop(object : ShutDownFinishedHandler(
                        MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED
                    ) {
                        override fun shutDownFinished(l: Long) {
                            // nothing to do
                        }
                    })
                /* FIXME: if (isCameraServiceRequested()) {
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
        Handler().postDelayed(
            { dismissCalibrationDialog(progressDialog, calibrationDialogListener) },
            CALIBRATION_DIALOG_TIMEOUT
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
     *
     * This is required when the app is resumed after being in background and probably missing locations
     * and when the app is restarted.
     */
    private fun updateCachedTrack() {
        try {
            if (!persistenceLayer.hasMeasurement(MeasurementStatus.OPEN)
                && !persistenceLayer.hasMeasurement(MeasurementStatus.PAUSED)
            ) {
                Log.d(TAG, "updateCachedTrack: No unfinished measurement found, un-setting cache.")
                capturingViewModel.resetCurrentMeasurementsTracks()
                return
            }
            Log.d(
                TAG,
                "updateCachedTrack: Unfinished measurement found, loading track from database."
            )
            val (id) = capturingService.loadCurrentlyCapturedMeasurement()
            val loadedList = persistenceLayer.loadTracks(
                id,
                DefaultLocationCleaning()
            )
            // We need to make sure we return a list which supports "add" even when an empty list is returned
            // or else the onHostResume method cannot add a new sub track to a loaded empty list
            capturingViewModel.currentMeasurementsTracks = java.util.ArrayList(loadedList)
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
            } else SpeedFragment()
        }

        override fun getItemCount(): Int {
            // List size
            return 2
        }
    }
}