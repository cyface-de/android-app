/*
 * Copyright 2017-2023 Cyface GmbH
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
package de.cyface.app.button

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.SharedPreferences
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.View.OnLongClickListener
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.github.lzyzsd.circleprogress.DonutProgress
import de.cyface.app.R
import de.cyface.app.CapturingFragment
import de.cyface.app.utils.CalibrationDialogListener
import de.cyface.app.utils.Constants
import de.cyface.app.utils.Map
import de.cyface.app.utils.SharedConstants.ACCEPTED_REPORTING_KEY
import de.cyface.app.utils.SharedConstants.PREFERENCES_MODALITY_KEY
import de.cyface.camera_service.CameraListener
import de.cyface.camera_service.CameraService
import de.cyface.camera_service.MessageCodes
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.DataCapturingListener
import de.cyface.datacapturing.DataCapturingService
import de.cyface.datacapturing.IsRunningCallback
import de.cyface.datacapturing.ShutDownFinishedHandler
import de.cyface.datacapturing.StartUpFinishedHandler
import de.cyface.datacapturing.exception.DataCapturingException
import de.cyface.datacapturing.exception.MissingPermissionException
import de.cyface.datacapturing.model.CapturedData
import de.cyface.datacapturing.ui.Reason
import de.cyface.energy_settings.TrackingSettings.isBackgroundProcessingRestricted
import de.cyface.energy_settings.TrackingSettings.isEnergySaferActive
import de.cyface.energy_settings.TrackingSettings.isGnssEnabled
import de.cyface.energy_settings.TrackingSettings.isProblematicManufacturer
import de.cyface.energy_settings.TrackingSettings.showEnergySaferWarningDialog
import de.cyface.energy_settings.TrackingSettings.showGnssWarningDialog
import de.cyface.energy_settings.TrackingSettings.showRestrictedBackgroundProcessingWarningDialog
import de.cyface.persistence.DefaultPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.exception.NoSuchMeasurementException
import de.cyface.persistence.model.Event
import de.cyface.persistence.model.EventType
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.model.Modality
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.persistence.model.Track
import de.cyface.persistence.strategy.DefaultLocationCleaning
import de.cyface.utils.DiskConsumption
import de.cyface.utils.Validate
import io.sentry.Sentry
import java.util.concurrent.TimeUnit

/**
 * The button listener for the button to start and stop the data capturing service.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.8.3
 * @since 1.0.0
 */
class DataCapturingButton(capturingFragment: CapturingFragment) : AbstractButton,
    DataCapturingListener, OnLongClickListener, CameraListener {
    // TODO [CY-3855]: communication with MainFragment/Activity should use this listener instead of hard-coded
    // implementation
    private val listener: MutableCollection<ButtonListener?>
    private var buttonStatus: MeasurementStatus? = null
    private var map: Map? = null
    private var locationManager: LocationManager? = null
    private var context: Context? = null

    /**
     * The [CyfaceDataCapturingService] required to control and check the capturing process.
     */
    private lateinit var dataCapturingService: CyfaceDataCapturingService

    /**
     * The [CameraService] required to control and check the visual capturing process.
     */
    private var cameraService: CameraService? = null
    private lateinit var preferences: SharedPreferences
    private var calibrationDialogListener: Collection<CalibrationDialogListener>? = null

    /**
     * The actual java button object, this class implements behaviour for.
     */
    private var button: ImageButton? = null

    /**
     * The `TextView` use to show the [Measurement.distance] for an ongoing measurement
     */
    private var distanceTextView: TextView? = null

    /**
     * The `TextView` use to show the info from the [CameraListener] for an ongoing measurement
     */
    private var cameraInfoTextView: TextView? = null

    /**
     * The `TextView` use to show the [Measurement.id] for an ongoing measurement
     */
    private var measurementIdTextView: TextView? = null

    /**
     * [DefaultPersistenceLayer] to show the [Measurement.distance] of the currently captured
     * [Measurement]
     */
    private var persistenceLayer: DefaultPersistenceLayer<DefaultPersistenceBehaviour>? = null
    private val capturingFragment: CapturingFragment

    /**
     * Caching the [Track]s of the current [Measurement], so we do not need to ask the database each time
     * the updated track is requested. This is `null` if there is no unfinished measurement.
     */
    internal var currentMeasurementsTracks: MutableList<Track>? = null

    /**
     * Helps to reduce the Sentry quota used. We only want to receive this event once for each DataCapturingButton
     * instance not for each location event.
     */
    private val onNewGeoLocationAcquiredExceptionTriggered = booleanArrayOf(false, false, false)

    /**
     * `True` if the user opted-in to error reporting.
     */
    private var isReportingEnabled = false
    private var calibrationProgressDialog: ProgressDialog? = null

    init {
        listener = HashSet()
        this.capturingFragment = capturingFragment
    }

    override fun onCreateView(button: ImageButton?, progress: DonutProgress?) {
        Validate.notNull(button)

        // In order to be able to execute runOnUiThread in onResume
        context = button!!.context
        this.button = button
        measurementIdTextView = button.rootView.findViewById(R.id.data_capturing_measurement_id)
        distanceTextView = button.rootView.findViewById(R.id.data_capturing_distance)
        cameraInfoTextView = button.rootView.findViewById(R.id.camera_capturing_info)

        // To get the vehicle
        preferences = PreferenceManager.getDefaultSharedPreferences(context!!)
        isReportingEnabled = preferences.getBoolean(ACCEPTED_REPORTING_KEY, false)

        // To load the measurement distance
        persistenceLayer = DefaultPersistenceLayer(context!!, DefaultPersistenceBehaviour())
        button.setOnClickListener(this)
        button.setOnLongClickListener(this)
        calibrationDialogListener = HashSet()
        locationManager =
            button.context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    fun bindMap(map: Map?) {
        this.map = map
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
     * @param button The button to access the [Activity] to run code on the UI thread
     */
    private fun setButtonEnabled(button: ImageButton) {
        runOnUiThread { button.isEnabled = true }
    }

    /**
     * Sets the button icon to indicate the next reachable state after a click on the button.
     *
     * @param button The button to access the [Activity] to run code on the UI thread
     * @param newStatus the new status of the measurement
     */
    private fun setButtonStatus(button: ImageButton?, newStatus: MeasurementStatus) {
        if (newStatus === buttonStatus) {
            Log.d(Constants.TAG, "setButtonStatus ignored, button is already in correct state.")
            return
        }
        buttonStatus = newStatus
        runOnUiThread {

            // noinspection ConstantConditions - this happened on Emulator N5X on app close
            if (button == null) {
                Log.w(Constants.TAG, "CapturingButton is null, not updating button status")
                return@runOnUiThread
            }
            updateButtonView(newStatus)
            updateOngoingCapturingInfo(newStatus)
            if (map != null) {
                if (newStatus === MeasurementStatus.OPEN) {
                    setLocationListener()
                } else {
                    unsetLocationListener()
                }
            }
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
            try {
                val measurementIdText =
                    (context!!.getString(de.cyface.app.utils.R.string.measurement) + " "
                            + persistenceLayer!!.loadCurrentlyCapturedMeasurement().id)
                measurementIdTextView!!.text = measurementIdText
                cameraInfoTextView!!.visibility = View.VISIBLE
            } catch (e: NoSuchMeasurementException) {
                throw IllegalStateException(e)
            }
        } else {
            // This way you can notice if a GeoLocation/Picture was already captured or not
            distanceTextView!!.text = ""
            // Disabling or else the text is updated when JpegSafer handles image after capturing stopped
            cameraInfoTextView!!.text = ""
            cameraInfoTextView!!.visibility = View.INVISIBLE
            measurementIdTextView!!.text = ""
        }
    }

    /**
     * Updates the view of the [DataCapturingButton] depending on it's {@param netState}.
     *
     * The button view indicates the next state to be reached after the button is clicked again.
     *
     * @param status the state of the `DataCapturingButton`
     */
    private fun updateButtonView(status: MeasurementStatus) {
        when (status) {
            MeasurementStatus.OPEN -> button!!.setImageResource(R.drawable.ic_stop)
            MeasurementStatus.PAUSED -> button!!.setImageResource(R.drawable.ic_resume)
            MeasurementStatus.FINISHED -> button!!.setImageResource(R.drawable.ic_play)
            else -> throw IllegalArgumentException("Invalid button state: $status")
        }
    }

    @Throws(SecurityException::class)
    private fun setLocationListener() {
        locationManager!!.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 0f, map!!)
    }

    @Throws(SecurityException::class)
    private fun unsetLocationListener() {
        locationManager!!.removeUpdates(map!!)
    }

    fun onPause() {
        unsetLocationListener()
        dataCapturingService.removeDataCapturingListener(this)
        cameraService!!.removeCameraListener(this)
    }

    /**
     * We cannot set the [DataCapturingService] in the constructor as there is a circular dependency:
     * - the `DataCapturingService` constructor requires the [DataCapturingButton]
     * ([DataCapturingListener]
     * - the `DataCapturingButton` requires the `DataCapturingService`
     *
     * Thus, we first create the `DataCapturingButton`, then reference it when creating the
     * `DataCapturingService` and then register the `DataCapturingService` in here to the button.
     *
     * This also sets the [DataCapturingButton] status to the correct state.
     *
     * @param dataCapturingService The `DataCapturingService` required to control and check the capturing.
     * @param cameraService The `CameraService` required to control and check the visual capturing.
     */
    fun onResume(
        dataCapturingService: CyfaceDataCapturingService,
        cameraService: CameraService
    ) {
        Log.d(Constants.TAG, "onResume: reconnecting ...")
        this.dataCapturingService = dataCapturingService
        this.cameraService = cameraService
        updateCachedTrack()
        dataCapturingService.addDataCapturingListener(this)
        cameraService.addCameraListener(this)

        // OPEN: running capturing
        if (dataCapturingService.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT)) {
            Log.d(Constants.TAG, "onResume: reconnecting DCS succeeded")
            setLocationListener()
            // We re-sync the button here as the data capturing can be canceled while the app is closed
            setButtonStatus(button!!, MeasurementStatus.OPEN)
            setButtonEnabled(button!!)
            // mainFragment.showUnfinishedTracksOnMap(persistenceLayer.loadCurrentlyCapturedMeasurement().getIdentifier());

            // Also try to reconnect to CameraService if it's alive
            if (cameraService.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT)) {
                // It does not matter whether isCameraServiceRequested() as this can change all the time
                Log.d(Constants.TAG, "onResume: reconnecting CameraService succeeded")
            }
            return
        }

        // PAUSED or FINISHED capturing
        Log.d(Constants.TAG, "onResume: reconnecting timed out")
        if (persistenceLayer!!.hasMeasurement(MeasurementStatus.PAUSED)) {
            setButtonStatus(button!!, MeasurementStatus.PAUSED)
            // mainFragment.showUnfinishedTracksOnMap(persistenceLayer.loadCurrentlyCapturedMeasurement().getIdentifier());
        } else {
            setButtonStatus(button!!, MeasurementStatus.FINISHED)
        }
        setButtonEnabled(button!!)

        // Check if there is a zombie CameraService running
        // In case anything went wrong and the camera is still bound by this app we're releasing it so that it
        // can be used by other apps again
        if (cameraService.reconnect(DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT)) {
            Log.w(
                de.cyface.camera_service.Constants.TAG, "Zombie CameraService is running and it's "
                        + (if (isCameraServiceRequested) "" else "*not*") + " requested"
            )
            cameraService.stop(
                object : ShutDownFinishedHandler(MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED) {
                    override fun shutDownFinished(measurementIdentifier: Long) {
                        Log.d(Constants.TAG, "onResume: zombie CameraService stopped")
                    }
                })
            throw IllegalStateException(
                "Camera stopped manually as the camera was not released. This should not happen!"
            )
        }
    }

    override fun onClick(view: View) {
        // Disables the button. This is used to avoid a duplicate start command which crashes the SDK
        // until CY-4098 is implemented. It's automatically re-enabled as soon as the callback arrives.
        button!!.isEnabled = false

        // The MaterialDialog implementation of the EnergySettings dialogs are not shown when called
        // from inside the IsRunningCallback. Thus, we call it here for now instead of in startCapturing()
        if (buttonStatus == MeasurementStatus.FINISHED || buttonStatus == MeasurementStatus.PAUSED && isRestrictionActive) {
            return
        }
        dataCapturingService.isRunning(
            DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT,
            TimeUnit.MILLISECONDS,
            object : IsRunningCallback {
                override fun isRunning() {
                    Validate.isTrue(
                        buttonStatus === MeasurementStatus.OPEN,
                        "DataCapturingButton is out of sync."
                    )
                    stopCapturing()
                }

                override fun timedOut() {
                    Validate.isTrue(
                        buttonStatus !== MeasurementStatus.OPEN,
                        "DataCapturingButton is out of sync."
                    )

                    // If Measurement is paused, resume the measurement on a normal click
                    if (persistenceLayer!!.hasMeasurement(MeasurementStatus.PAUSED)) {
                        resumeCapturing()
                        return
                    }
                    startCapturing()
                }
            })
    }

    override fun onLongClick(v: View): Boolean {
        // Disables the button. This is used to avoid a duplicate start command which crashes the SDK
        // until CY-4098 is implemented. It's automatically re-enabled as soon as the callback arrives.
        button!!.isEnabled = false

        // The MaterialDialog implementation of the EnergySettings dialogs are not shown when called
        // from inside the IsRunningCallback. Thus, we call it here for now instead of in startCapturing()
        if (buttonStatus == MeasurementStatus.FINISHED || buttonStatus == MeasurementStatus.PAUSED && isRestrictionActive) {
            return true
        }
        dataCapturingService.isRunning(
            DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT,
            TimeUnit.MILLISECONDS,
            object : IsRunningCallback {
                override fun isRunning() {
                    Validate.isTrue(
                        buttonStatus === MeasurementStatus.OPEN,
                        "DataCapturingButton is out of sync."
                    )
                    pauseCapturing()
                }

                override fun timedOut() {
                    Validate.isTrue(
                        buttonStatus !== MeasurementStatus.OPEN,
                        "DataCapturingButton is out of sync."
                    )

                    // If Measurement is paused, stop the measurement on long press
                    if (persistenceLayer!!.hasMeasurement(MeasurementStatus.PAUSED)) {
                        stopCapturing()
                        return
                    }
                    startCapturing()
                }
            })
        return true
    }

    /**
     * Pause capturing
     */
    private fun pauseCapturing() {
        try {
            dataCapturingService.pause(
                object :
                    ShutDownFinishedHandler(de.cyface.datacapturing.MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED) {
                    override fun shutDownFinished(measurementIdentifier: Long) {
                        // The measurement id should always be set [STAD-333]
                        Validate.isTrue(measurementIdentifier != -1L, "Missing measurement id")
                        setButtonStatus(button!!, MeasurementStatus.PAUSED)
                        setButtonEnabled(button!!)
                        Toast.makeText(
                            context,
                            R.string.toast_measurement_paused,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
        } catch (e: NoSuchMeasurementException) {
            throw IllegalStateException(e)
        }

        // Pause camera capturing if it is running (even is DCS is not running)
        // TODO: this way we don't notice when the camera was stopped unexpectedly
        cameraService!!.isRunning(
            DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS,
            object : IsRunningCallback {
                override fun isRunning() {
                    cameraService!!.pause(object : ShutDownFinishedHandler(
                        MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED
                    ) {
                        override fun shutDownFinished(measurementIdentifier: Long) {
                            Log.d(Constants.TAG, "pauseCapturing: CameraService stopped")
                        }
                    })
                }

                override fun timedOut() {
                    Log.d(
                        de.cyface.camera_service.Constants.TAG,
                        "pauseCapturing: no CameraService running, nothing to do"
                    )
                }
            })
    }

    /**
     * Stop capturing
     */
    private fun stopCapturing() {
        try {
            dataCapturingService.stop(
                object :
                    ShutDownFinishedHandler(de.cyface.datacapturing.MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED) {
                    override fun shutDownFinished(measurementIdentifier: Long) {
                        // The measurement id should always be set [STAD-333]
                        Validate.isTrue(measurementIdentifier != -1L, "Missing measurement id")
                        currentMeasurementsTracks = null
                        setButtonStatus(button!!, MeasurementStatus.FINISHED)
                        setButtonEnabled(button!!)
                    }
                })
            runOnUiThread { map!!.clearMap() }
        } catch (e: NoSuchMeasurementException) {
            throw IllegalStateException(e)
        }

        // Stop camera capturing if it is running (even is DCS is not running)
        // TODO: this way we don't notice when the camera was stopped unexpectedly
        cameraService!!.isRunning(
            DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT,
            TimeUnit.MILLISECONDS,
            object : IsRunningCallback {
                override fun isRunning() {
                    cameraService!!.stop(object : ShutDownFinishedHandler(
                        MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED
                    ) {
                        override fun shutDownFinished(measurementIdentifier: Long) {
                            Log.d(Constants.TAG, "stopCapturing: CameraService stopped")
                        }
                    })
                }

                override fun timedOut() {
                    Log.d(
                        de.cyface.camera_service.Constants.TAG,
                        "stopCapturing: no CameraService running, nothing to do"
                    )
                }
            })
    }

    /**
     * Starts capturing
     */
    private fun startCapturing() {

        // Measurement is stopped, so we start a new measurement
        if (persistenceLayer!!.hasMeasurement(MeasurementStatus.OPEN) && isProblematicManufacturer) {
            showToastOnMainThread(
                context!!.getString(de.cyface.app.utils.R.string.toast_last_tracking_crashed),
                true
            )
        }

        // We use a handler to run the UI Code on the main thread as it is supposed to be
        runOnUiThread {
            calibrationProgressDialog = createAndShowCalibrationDialog()
            scheduleProgressDialogDismissal(calibrationProgressDialog, calibrationDialogListener)
        }

        // TODO [CY-3855]: we have to provide a listener for the button (<- ???)
        try {
            val modality = Modality.valueOf(
                preferences.getString(PREFERENCES_MODALITY_KEY, null)!!
            )
            Validate.notNull(modality)
            currentMeasurementsTracks = ArrayList()
            currentMeasurementsTracks!!.add(Track())
            dataCapturingService.start(modality,
                object :
                    StartUpFinishedHandler(de.cyface.datacapturing.MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED) {
                    override fun startUpFinished(measurementIdentifier: Long) {
                        // The measurement id should always be set [STAD-333]
                        Validate.isTrue(measurementIdentifier != -1L, "Missing measurement id")
                        Log.v(TAG, "startUpFinished")
                        setButtonStatus(button!!, MeasurementStatus.OPEN)
                        setButtonEnabled(button!!)

                        // Start CameraService
                        if (isCameraServiceRequested) {
                            Log.d(de.cyface.camera_service.Constants.TAG, "CameraServiceRequested")
                            try {
                                startCameraService(measurementIdentifier)
                            } catch (e: DataCapturingException) {
                                throw IllegalStateException(e)
                            } catch (e: MissingPermissionException) {
                                throw IllegalStateException(e)
                            }
                        }
                    }
                })
        } catch (e: DataCapturingException) {
            throw IllegalStateException(e)
        } catch (e: MissingPermissionException) {
            throw IllegalStateException(e)
        }
    }

    private val isRestrictionActive: Boolean
        get() {
            if (context == null) {
                Log.w(Constants.TAG, "Context is null, restrictions cannot be checked")
                return false
            }
            val activity: Activity? = capturingFragment.activity
            if (activity == null) {
                Log.w(Constants.TAG, "Activity is null. If needed, dialogs wont appear.")
            }
            if (!DiskConsumption.spaceAvailable()) {
                showToastOnMainThread(
                    context!!.getString(de.cyface.app.utils.R.string.error_message_capturing_canceled_no_space),
                    false
                )
                setButtonEnabled(button!!)
                return true
            }
            if (!isGnssEnabled(context!!)) {
                showGnssWarningDialog(activity)
                setButtonEnabled(button!!)
                return true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isEnergySaferActive(
                    context!!
                )
            ) {
                showEnergySaferWarningDialog(activity)
                setButtonEnabled(button!!)
                return true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isBackgroundProcessingRestricted(
                    context!!
                )
            ) {
                showRestrictedBackgroundProcessingWarningDialog(activity)
                setButtonEnabled(button!!)
                return true
            }
            return false
        }

    /**
     * Resumes capturing
     */
    private fun resumeCapturing() {
        Log.d(Constants.TAG, "resumeCachedTrack: Adding new sub track to existing cached track")
        currentMeasurementsTracks!!.add(Track())
        try {
            dataCapturingService.resume(
                object :
                    StartUpFinishedHandler(de.cyface.datacapturing.MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED) {
                    override fun startUpFinished(measurementIdentifier: Long) {
                        // The measurement id should always be set [STAD-333]
                        Validate.isTrue(measurementIdentifier != -1L, "Missing measurement id")
                        Log.v(TAG, "resumeCapturing: startUpFinished")
                        setButtonStatus(button!!, MeasurementStatus.OPEN)
                        setButtonEnabled(button!!)
                        Toast.makeText(
                            context,
                            R.string.toast_measurement_resumed,
                            Toast.LENGTH_SHORT
                        ).show()

                        // Start CameraService
                        if (isCameraServiceRequested) {
                            Log.d(de.cyface.camera_service.Constants.TAG, "CameraServiceRequested")
                            try {
                                startCameraService(measurementIdentifier)
                            } catch (e: DataCapturingException) {
                                throw IllegalStateException(e)
                            } catch (e: MissingPermissionException) {
                                throw IllegalStateException(e)
                            }
                        }
                    }
                })
        } catch (e: NoSuchMeasurementException) {
            throw IllegalStateException(e)
        } catch (e: DataCapturingException) {
            throw IllegalStateException(e)
        } catch (e: MissingPermissionException) {
            throw IllegalStateException(e)
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

    /**
     * Starts the camera service and, thus, the camera capturing.
     *
     * @param measurementId the id of the measurement for which camera data is to be captured
     * @throws DataCapturingException If the asynchronous background service did not start successfully or no valid
     * Android context was available.
     * @throws MissingPermissionException If no Android `ACCESS_FINE_LOCATION` has been granted. You may
     * register a listener to ask the user for this permission and prevent the
     * `Exception`. If the `Exception` was thrown the service does not start.
     */
    @Throws(DataCapturingException::class, MissingPermissionException::class)
    private fun startCameraService(measurementId: Long) {
        val rawModeSelected = preferences.getBoolean(
            de.cyface.camera_service.Constants.PREFERENCES_CAMERA_RAW_MODE_ENABLED_KEY,
            false
        )
        val videoModeSelected = preferences.getBoolean(
            de.cyface.camera_service.Constants.PREFERENCES_CAMERA_VIDEO_MODE_ENABLED_KEY,
            false
        )
        // We need to load and pass the preferences for the camera focus here as the preferences
        // do not work reliably on multi-process access. https://stackoverflow.com/a/27987956/5815054
        val staticFocusSelected = preferences.getBoolean(
            de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_FOCUS_ENABLED_KEY,
            false
        )
        val staticFocusDistance = preferences.getFloat(
            de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_FOCUS_DISTANCE_KEY,
            de.cyface.camera_service.Constants.DEFAULT_STATIC_FOCUS_DISTANCE
        )
        val distanceBasedTriggeringSelected = preferences.getBoolean(
            de.cyface.camera_service.Constants.PREFERENCES_CAMERA_DISTANCE_BASED_TRIGGERING_ENABLED_KEY,
            true
        )
        val triggeringDistance = preferences.getFloat(
            de.cyface.camera_service.Constants.PREFERENCES_CAMERA_TRIGGERING_DISTANCE_KEY,
            de.cyface.camera_service.Constants.DEFAULT_TRIGGERING_DISTANCE
        )
        val staticExposureTimeSelected = preferences.getBoolean(
            de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_ENABLED_KEY,
            false
        )
        val staticExposureTime = preferences.getLong(
            de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_KEY,
            de.cyface.camera_service.Constants.DEFAULT_STATIC_EXPOSURE_TIME
        )
        val exposureValueIso100 = preferences.getInt(
            de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_EXPOSURE_VALUE_KEY,
            de.cyface.camera_service.Constants.DEFAULT_STATIC_EXPOSURE_VALUE_ISO_100
        )
        cameraService!!.start(measurementId,
            videoModeSelected,
            rawModeSelected,
            staticFocusSelected,
            staticFocusDistance,
            staticExposureTimeSelected,
            staticExposureTime,
            exposureValueIso100,
            distanceBasedTriggeringSelected,
            triggeringDistance,
            object : StartUpFinishedHandler(MessageCodes.GLOBAL_BROADCAST_SERVICE_STARTED) {
                override fun startUpFinished(measurementIdentifier: Long) {
                    Log.v(
                        de.cyface.camera_service.Constants.TAG,
                        "startCameraService: CameraService startUpFinished"
                    )
                }
            })
    }

    /**
     * Creates and shows [ProgressDialog] to inform the user about calibration at the beginning of a
     * measurement.
     *
     * @return A reference to the ProgressDialog which can be used to dismiss it.
     */
    private fun createAndShowCalibrationDialog(): ProgressDialog {
        return ProgressDialog.show(
            context,
            context!!.getString(de.cyface.app.utils.R.string.title_dialog_starting_data_capture),
            context!!.getString(de.cyface.app.utils.R.string.msg_calibrating),
            true,
            false
        ) {
            try {
                dataCapturingService
                    .stop(object : ShutDownFinishedHandler(
                        de.cyface.datacapturing.MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED
                    ) {
                        override fun shutDownFinished(l: Long) {
                            // nothing to do
                        }
                    })
                if (isCameraServiceRequested) {
                    cameraService!!.stop(object : ShutDownFinishedHandler(
                        MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED
                    ) {
                        override fun shutDownFinished(l: Long) {
                            // nothing to do
                        }
                    })
                }
            } catch (e: NoSuchMeasurementException) {
                throw IllegalStateException(e)
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
        progressDialog: ProgressDialog?,
        calibrationDialogListener: Collection<CalibrationDialogListener>?
    ) {
        Handler().postDelayed(
            { dismissCalibrationDialog(progressDialog, calibrationDialogListener) },
            CALIBRATION_DIALOG_TIMEOUT
        )
    }

    private fun dismissCalibrationDialog(
        progressDialog: ProgressDialog?,
        calibrationDialogListener: Collection<CalibrationDialogListener>?
    ) {
        if (progressDialog != null) {
            progressDialog.dismiss()
            for (calibrationDialogListener1 in calibrationDialogListener!!) {
                calibrationDialogListener1.onCalibrationDialogFinished()
            }
        }
    }

    /**
     * Loads the track of the currently captured measurement from the database.
     *
     * This is required when the app is resumed after being in background and probably missing locations
     * and when the app is restarted.
     */
    private fun updateCachedTrack() {
        try {
            if (!persistenceLayer!!.hasMeasurement(MeasurementStatus.OPEN)
                && !persistenceLayer!!.hasMeasurement(MeasurementStatus.PAUSED)
            ) {
                Log.d(
                    Constants.TAG,
                    "updateCachedTrack: No unfinished measurement found, un-setting cache."
                )
                currentMeasurementsTracks = null
                return
            }
            Log.d(
                Constants.TAG,
                "updateCachedTrack: Unfinished measurement found, loading track from database."
            )
            val (id) = dataCapturingService.loadCurrentlyCapturedMeasurement()
            val loadedList = persistenceLayer!!.loadTracks(
                id,
                DefaultLocationCleaning()
            )
            // We need to make sure we return a list which supports "add" even when an empty list is returned
            // or else the onHostResume method cannot add a new sub track to a loaded empty list
            currentMeasurementsTracks = ArrayList(loadedList)
        } catch (e: NoSuchMeasurementException) {
            throw RuntimeException(e)
        }
    }

    fun getCurrentMeasurementsTracks(): List<Track>? {
        return currentMeasurementsTracks
    }

    @Throws(NoSuchMeasurementException::class)
    fun loadCurrentMeasurementsEvents(): List<Event>? {
        val (id) = dataCapturingService.loadCurrentlyCapturedMeasurement()
        return persistenceLayer!!.loadEvents(id, EventType.MODALITY_TYPE_CHANGE)
    }

    override fun onDestroyView() {
        button!!.setOnClickListener(null)
        disconnect(dataCapturingService, cameraService)
        dismissCalibrationDialog(calibrationProgressDialog, calibrationDialogListener)
    }

    /**
     * Unbinds the services. They continue to run in the background but won't send any updates to this button.
     *
     * Instead of only disconnecting when `isRunning()` returns a timeout we always disconnect.
     * This way the race condition between view destruction and timeout is prevented [MOV-588].
     *
     * @param dataCapturingService the capturing service to unregister from
     * @param cameraService the camera service to unregister from
     */
    private fun disconnect(
        dataCapturingService: DataCapturingService?,
        cameraService: CameraService?
    ) {
        if (dataCapturingService == null) {
            Log.w(Constants.TAG, "Skipping DCS.disconnect() as DCS is null")
            // This should not happen, thus, reporting to Sentry
            if (isReportingEnabled) {
                Sentry.captureMessage("DCButton.onDestroyView: dataCapturingService is null")
            }
        } else {
            try {
                dataCapturingService.disconnect()
            } catch (e: DataCapturingException) {
                // This just tells us there is no running capturing in the background, see [MOV-588]
                Log.d(Constants.TAG, "No need to unbind as the background service was not running.")
            }
        }
        if (cameraService == null) {
            Log.d(Constants.TAG, "Skipping CameraService.disconnect() as CameraService is null")
            // No need to capture this as this is always null when camera is disabled
        } else {
            try {
                cameraService.disconnect()
            } catch (e: DataCapturingException) {
                // This just tells us there is no running capturing in the background, see [MOV-588]
                Log.d(
                    Constants.TAG,
                    "No need to unbind as the camera background service was not running."
                )
            }
        }
    }

    override fun addButtonListener(buttonListener: ButtonListener?) {
        Validate.notNull(buttonListener)
        listener.add(buttonListener)
    }

    private val isCameraServiceRequested: Boolean
        get() = preferences.getBoolean(
            de.cyface.camera_service.Constants.PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY,
            false
        )

    override fun onFixAcquired() {
        // Nothing to do
    }

    override fun onFixLost() {
        // Nothing to do
    }

    override fun onNewGeoLocationAcquired(geoLocation: ParcelableGeoLocation) {
        Log.d(Constants.TAG, "onNewGeoLocationAcquired")
        val measurement: Measurement
        try {
            measurement = persistenceLayer!!.loadCurrentlyCapturedMeasurement()
        } catch (e: NoSuchMeasurementException) {
            // GeoLocations may also arrive shortly after a measurement was stopped. Thus, this may not crash.
            // This happened on the Emulator with emulated live locations.
            Log.w(
                Constants.TAG,
                "onNewGeoLocationAcquired: No currently captured measurement found, doing nothing."
            )
            if (!onNewGeoLocationAcquiredExceptionTriggered[0] && isReportingEnabled) {
                onNewGeoLocationAcquiredExceptionTriggered[0] = true
                Sentry.captureException(e)
            }
            return
        }
        val distanceMeter = Math.round(measurement.distance).toInt()
        val distanceKm = if (distanceMeter == 0) 0.0 else distanceMeter / 1000.0
        val distanceText = "$distanceKm km"
        Log.d(Constants.TAG, "Distance update: $distanceText")
        distanceTextView!!.text = distanceText
        Log.d(Constants.TAG, "distanceTextView: " + distanceTextView!!.text)
        Log.d(Constants.TAG, "distanceTextView: " + distanceTextView!!.isEnabled)
        addLocationToCachedTrack(geoLocation)
        val currentMeasurementsEvents: List<Event>?
        try {
            currentMeasurementsEvents = loadCurrentMeasurementsEvents()
            capturingFragment.map!!.renderMeasurement(
                currentMeasurementsTracks!!,
                currentMeasurementsEvents!!,
                false
            )
        } catch (e: NoSuchMeasurementException) {
            Log.w(
                Constants.TAG,
                "onNewGeoLocationAcquired() failed to loadCurrentMeasurementsEvents(). "
                        + "Thus, map.renderMeasurement() is ignored. This should only happen id "
                        + "the capturing already stopped."
            )
            if (!onNewGeoLocationAcquiredExceptionTriggered[2] && isReportingEnabled) {
                onNewGeoLocationAcquiredExceptionTriggered[2] = true
                Sentry.captureException(e)
            }
        }
    }

    private fun addLocationToCachedTrack(location: ParcelableGeoLocation) {
        Validate.notNull(currentMeasurementsTracks, "onNewGeoLocation - cached track is null")
        if (!location.isValid) {
            Log.d(Constants.TAG, "updateCachedTrack: ignoring invalid point")
            return
        }
        if (currentMeasurementsTracks!!.size == 0) {
            Log.d(
                Constants.TAG,
                "updateCachedTrack: Loaded track is empty, creating new list with empty sub track"
            )
            currentMeasurementsTracks = ArrayList()
            currentMeasurementsTracks!!.add(Track())
        }
        currentMeasurementsTracks!![currentMeasurementsTracks!!.size - 1].addLocation(location)
    }

    override fun onNewSensorDataAcquired(capturedData: CapturedData) {
        // Nothing to do here
    }

    override fun onNewPictureAcquired(picturesCaptured: Int) {
        Log.d(de.cyface.camera_service.Constants.TAG, "onNewPictureAcquired")
        val text = context!!.getString(R.string.camera_images) + " " + picturesCaptured
        cameraInfoTextView!!.text = text
        Log.d(Constants.TAG, "cameraInfoTextView: " + cameraInfoTextView!!.text)
    }

    override fun onNewVideoStarted() {
        Log.d(de.cyface.camera_service.Constants.TAG, "onNewVideoStarted")
    }

    override fun onVideoStopped() {
        Log.d(de.cyface.camera_service.Constants.TAG, "onVideoStopped")
    }

    override fun onLowDiskSpace(diskConsumption: DiskConsumption) {
        // Nothing to do here - handled by <code>DataCapturingEventHandler</code>
    }

    override fun onSynchronizationSuccessful() {
        // Nothing to do here
    }

    override fun onErrorState(e: Exception) {
        throw IllegalStateException(e)
    }

    override fun onRequiresPermission(permission: String, reason: Reason): Boolean {
        return false
    }

    override fun onCapturingStopped() {
        setButtonStatus(button!!, MeasurementStatus.FINISHED)
    } /*
     * TODO [CY-3577] re-enable
     * Adds an already paired Bluetooth CSC sensor device information to the provided Intent. If no such device is
     * found, a warning is issued and no information is added.
     * <p>
     * The information is added as two extras with the keys {@link BluetoothLeSetup#BLUETOOTH_LE_DEVICE} and
     * {@link BluetoothLeSetup#WHEEL_CIRCUMFERENCE}. The first is the Android {@link BluetoothDevice} object while
     * the second is a double parameter specifying the used vehicles wheel circumference in centimeters.
     *
     * @ param intent The {@link Intent} to add the parameters to.
     *
     * private void addBluetoothDeviceToIntent(final Intent intent) {
     * final String bluetoothDeviceMac = preferences.getString(BluetoothLeSetup.BLUETOOTHLE_DEVICE_MAC_KEY, "");
     * double wheelCircumference = preferences.getFloat(BluetoothLeSetup.BLUETOOTHLE_WHEEL_CIRCUMFERENCE, 0);
     * Validate.notEmpty(bluetoothDeviceMac);
     * Validate.isTrue(wheelCircumference > 0);
     *
     * Log.d(TAG, "Using registered Bluetooth CSC sensor MAC: " + bluetoothDeviceMac + ", Wheel Circumference: "
     * + wheelCircumference);
     * BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(bluetoothDeviceMac);
     * if (device == null) {
     * return;
     * }
     * intent.putExtra(BluetoothLeSetup.BLUETOOTH_LE_DEVICE, device);
     * intent.putExtra(BluetoothLeSetup.WHEEL_CIRCUMFERENCE, Double.valueOf(wheelCircumference).floatValue());
     * }
     */

    companion object {
        private const val CALIBRATION_DIALOG_TIMEOUT = 1500L
    }
}