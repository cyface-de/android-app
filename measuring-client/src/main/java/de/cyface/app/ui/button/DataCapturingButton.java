/*
 * Copyright 2017 Cyface GmbH
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
package de.cyface.app.ui.button;

import static de.cyface.app.utils.Constants.ACCEPTED_REPORTING_KEY;
import static de.cyface.app.utils.Constants.AUTHORITY;
import static de.cyface.app.utils.Constants.PREFERENCES_MODALITY_KEY;
import static de.cyface.app.utils.Constants.TAG;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_DISTANCE_BASED_TRIGGERING_ENABLED_KEY;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_RAW_MODE_ENABLED_KEY;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_ENABLED_KEY;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_EXPOSURE_VALUE_KEY;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_KEY;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_FOCUS_DISTANCE_KEY;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_FOCUS_ENABLED_KEY;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_TRIGGERING_DISTANCE_KEY;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_VIDEO_MODE_ENABLED_KEY;
import static de.cyface.datacapturing.DataCapturingService.IS_RUNNING_CALLBACK_TIMEOUT;
import static de.cyface.energy_settings.TrackingSettings.isBackgroundProcessingRestricted;
import static de.cyface.energy_settings.TrackingSettings.isEnergySaferActive;
import static de.cyface.energy_settings.TrackingSettings.isGnssEnabled;
import static de.cyface.energy_settings.TrackingSettings.isProblematicManufacturer;
import static de.cyface.energy_settings.TrackingSettings.showEnergySaferWarningDialog;
import static de.cyface.energy_settings.TrackingSettings.showGnssWarningDialog;
import static de.cyface.energy_settings.TrackingSettings.showRestrictedBackgroundProcessingWarningDialog;
import static de.cyface.persistence.model.MeasurementStatus.FINISHED;
import static de.cyface.persistence.model.MeasurementStatus.OPEN;
import static de.cyface.persistence.model.MeasurementStatus.PAUSED;
import static de.cyface.utils.DiskConsumption.spaceAvailable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.github.lzyzsd.circleprogress.DonutProgress;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentProvider;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import de.cyface.app.R;
import de.cyface.app.ui.MainFragment;
import de.cyface.app.ui.Map;
import de.cyface.app.ui.dialog.CalibrationDialogListener;
import de.cyface.camera_service.CameraListener;
import de.cyface.camera_service.CameraService;
import de.cyface.camera_service.Constants;
import de.cyface.camera_service.UIListener;
import de.cyface.datacapturing.CyfaceDataCapturingService;
import de.cyface.datacapturing.DataCapturingListener;
import de.cyface.datacapturing.DataCapturingService;
import de.cyface.datacapturing.IsRunningCallback;
import de.cyface.datacapturing.MessageCodes;
import de.cyface.datacapturing.ShutDownFinishedHandler;
import de.cyface.datacapturing.StartUpFinishedHandler;
import de.cyface.datacapturing.exception.DataCapturingException;
import de.cyface.datacapturing.exception.MissingPermissionException;
import de.cyface.datacapturing.model.CapturedData;
import de.cyface.datacapturing.ui.Reason;
import de.cyface.persistence.DefaultLocationCleaningStrategy;
import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Modality;
import de.cyface.persistence.model.Track;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.DiskConsumption;
import de.cyface.utils.Validate;
import io.sentry.Sentry;

/**
 * The button listener for the button to start and stop the data capturing service.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.8.0
 * @since 1.0.0
 */
public class DataCapturingButton
        implements AbstractButton, DataCapturingListener, View.OnLongClickListener, CameraListener {

    // TODO [CY-3855]: communication with MainFragment/Activity should use this listener instead of hard-coded
    // implementation
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final Collection<ButtonListener> listener;
    private MeasurementStatus buttonStatus;
    private Map map;
    private LocationManager locationManager;
    private Context context;
    /**
     * The {@link CyfaceDataCapturingService} required to control and check the capturing process.
     */
    private CyfaceDataCapturingService dataCapturingService = null;
    /**
     * The {@link CameraService} required to control and check the visual capturing process.
     */
    private CameraService cameraService = null;
    private SharedPreferences preferences;
    private final static long CALIBRATION_DIALOG_TIMEOUT = 1500L;
    private Collection<CalibrationDialogListener> calibrationDialogListener;
    /**
     * The actual java button object, this class implements behaviour for.
     */
    private ImageButton button;
    /**
     * The {@code TextView} use to show the {@link Measurement#getDistance()} for an ongoing measurement
     */
    private TextView distanceTextView;
    /**
     * The {@code TextView} use to show the info from the {@link CameraListener} for an ongoing measurement
     */
    private TextView cameraInfoTextView;
    /**
     * The {@code TextView} use to show the {@link Measurement#getIdentifier()} for an ongoing measurement
     */
    private TextView measurementIdTextView;
    /**
     * {@link PersistenceLayer} to show the {@link Measurement#getDistance()} of the currently captured
     * {@link Measurement}
     */
    private PersistenceLayer<DefaultPersistenceBehaviour> persistenceLayer;
    private final MainFragment mainFragment;
    /**
     * Caching the {@link Track}s of the current {@link Measurement}, so we do not need to ask the database each time
     * the updated track is requested. This is {@code null} if there is no unfinished measurement.
     */
    private List<Track> currentMeasurementsTracks;
    /**
     * Helps to reduce the Sentry quota used. We only want to receive this event once for each DataCapturingButton
     * instance not for each location event.
     */
    private final boolean[] onNewGeoLocationAcquiredExceptionTriggered = new boolean[] {false, false, false};
    /**
     * {@code True} if the user opted-in to error reporting.
     */
    private boolean isReportingEnabled;
    private ProgressDialog calibrationProgressDialog;

    public DataCapturingButton(@NonNull final MainFragment mainFragment) {
        this.listener = new HashSet<>();
        this.mainFragment = mainFragment;
    }

    @Override
    public void onCreateView(final ImageButton button, final DonutProgress ISNULL) {
        Validate.notNull(button);

        // In order to be able to execute runOnUiThread in onResume
        this.context = button.getContext();

        this.button = button;
        this.measurementIdTextView = button.getRootView().findViewById(R.id.data_capturing_measurement_id);
        this.distanceTextView = button.getRootView().findViewById(R.id.data_capturing_distance);
        this.cameraInfoTextView = button.getRootView().findViewById(R.id.camera_capturing_info);

        // To get the vehicle
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        isReportingEnabled = preferences.getBoolean(ACCEPTED_REPORTING_KEY, false);

        // To load the measurement distance
        this.persistenceLayer = new PersistenceLayer<>(context, context.getContentResolver(), AUTHORITY,
                new DefaultPersistenceBehaviour());

        button.setOnClickListener(this);
        button.setOnLongClickListener(this);
        calibrationDialogListener = new HashSet<>();
        locationManager = (LocationManager)button.getContext().getSystemService(Context.LOCATION_SERVICE);
    }

    public void bindMap(Map map) {
        this.map = map;
    }

    /**
     * This method helps to access the button via UI thread from a handler thread.
     */
    private void runOnUiThread(@NonNull final Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }

    /**
     * Updates the "is enabled" state of the button on the ui thread.
     *
     * @param button The {@link Button} to access the {@link Activity} to run code on the UI thread
     */
    private void setButtonEnabled(@NonNull final ImageButton button) {
        runOnUiThread(() -> button.setEnabled(true));
    }

    /**
     * Sets the {@link Button} icon to indicate the next reachable state after a click on the button.
     *
     * @param button The {@link Button} to access the {@link Activity} to run code on the UI thread
     * @param newStatus the new status of the measurement
     */
    private void setButtonStatus(@NonNull final ImageButton button, @NonNull final MeasurementStatus newStatus) {

        if (newStatus == buttonStatus) {
            Log.d(TAG, "setButtonStatus ignored, button is already in correct state.");
            return;
        }

        buttonStatus = newStatus;
        runOnUiThread(() -> {
            // noinspection ConstantConditions - this happened on Emulator N5X on app close
            if (button == null) {
                Log.w(TAG, "CapturingButton is null, not updating button status");
                return;
            }

            updateButtonView(newStatus);
            updateOngoingCapturingInfo(newStatus);

            if (map != null) {
                if (newStatus == OPEN) {
                    setLocationListener();
                } else {
                    unsetLocationListener();
                }
            }
        });
    }

    /**
     * Updates the {@code TextView}s depending on the current {@link MeasurementStatus}.
     * <p>
     * When a new Capturing is started, the {@code TextView} will only show the {@link Measurement#getIdentifier()}
     * of the open {@link Measurement}. The {@link Measurement#getDistance()} is automatically updated as soon as the
     * first {@link GeoLocation}s are captured. This way the user can see if the capturing actually works.
     *
     * @param status the state of the {@code DataCapturingButton}
     */
    private void updateOngoingCapturingInfo(@NonNull final MeasurementStatus status) {
        if (status == OPEN) {
            try {
                final String measurementIdText = context.getString(R.string.measurement) + " "
                        + persistenceLayer.loadCurrentlyCapturedMeasurement().getIdentifier();
                measurementIdTextView.setText(measurementIdText);
                cameraInfoTextView.setVisibility(View.VISIBLE);
            } catch (CursorIsNullException | NoSuchMeasurementException e) {
                throw new IllegalStateException(e);
            }
        } else {
            // This way you can notice if a GeoLocation/Picture was already captured or not
            distanceTextView.setText("");
            // Disabling or else the text is updated when JpegSafer handles image after capturing stopped
            cameraInfoTextView.setText("");
            cameraInfoTextView.setVisibility(View.INVISIBLE);
            measurementIdTextView.setText("");
        }
    }

    /**
     * Updates the view of the {@link DataCapturingButton} depending on it's {@param netState}.
     * <p>
     * The button view indicates the next state to be reached after the button is clicked again.
     *
     * @param status the state of the {@code DataCapturingButton}
     */
    private void updateButtonView(@NonNull final MeasurementStatus status) {

        switch (status) {
            case OPEN:
                button.setImageResource(R.drawable.ic_stop);
                break;
            case PAUSED:
                button.setImageResource(R.drawable.ic_resume);
                break;
            case FINISHED:
                button.setImageResource(R.drawable.ic_play);
                break;
            default:
                throw new IllegalArgumentException("Invalid button state: " + status);
        }
    }

    private void setLocationListener() throws SecurityException {
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 0f, map);
    }

    private void unsetLocationListener() throws SecurityException {
        locationManager.removeUpdates(map);
    }

    public void onPause() {
        unsetLocationListener();
        dataCapturingService.removeDataCapturingListener(this);
        cameraService.removeCameraListener(this);
    }

    /**
     * We cannot set the {@link DataCapturingService} in the constructor as there is a circular dependency:
     * - the {@code DataCapturingService} constructor requires the {@link DataCapturingButton}
     * ({@link DataCapturingListener}
     * - the {@code DataCapturingButton} requires the {@code DataCapturingService}
     * <p>
     * Thus, we first create the {@code DataCapturingButton}, then reference it when creating the
     * {@code DataCapturingService} and then register the {@code DataCapturingService} in here to the button.
     * <p>
     * This also sets the {@link DataCapturingButton} status to the correct state.
     *
     * @param dataCapturingService The {@code DataCapturingService} required to control and check the capturing.
     * @param cameraService The {@code CameraService} required to control and check the visual capturing.
     */
    public void onResume(@NonNull final CyfaceDataCapturingService dataCapturingService,
            @NonNull final CameraService cameraService) {
        Log.d(TAG, "onResume: reconnecting ...");
        this.dataCapturingService = dataCapturingService;
        this.cameraService = cameraService;
        updateCachedTrack();
        dataCapturingService.addDataCapturingListener(this);
        cameraService.addCameraListener(this);

        // OPEN: running capturing
        if (dataCapturingService.reconnect(IS_RUNNING_CALLBACK_TIMEOUT)) {
            Log.d(TAG, "onResume: reconnecting DCS succeeded");
            setLocationListener();
            // We re-sync the button here as the data capturing can be canceled while the app is closed
            setButtonStatus(button, OPEN);
            setButtonEnabled(button);
            // mainFragment.showUnfinishedTracksOnMap(persistenceLayer.loadCurrentlyCapturedMeasurement().getIdentifier());

            // Also try to reconnect to CameraService if it's alive
            if (cameraService.reconnect(IS_RUNNING_CALLBACK_TIMEOUT)) {
                // It does not matter whether isCameraServiceRequested() as this can change all the time
                Log.d(TAG, "onResume: reconnecting CameraService succeeded");
            }
            return;
        }

        // PAUSED or FINISHED capturing
        Log.d(TAG, "onResume: reconnecting timed out");
        try {
            if (persistenceLayer.hasMeasurement(PAUSED)) {
                setButtonStatus(button, PAUSED);
                // mainFragment.
                // showUnfinishedTracksOnMap(persistenceLayer.loadCurrentlyCapturedMeasurement().getIdentifier());
            } else {
                setButtonStatus(button, FINISHED);
            }
        } catch (final CursorIsNullException e) {
            throw new IllegalStateException(e);
        }
        setButtonEnabled(button);

        // Check if there is a zombie CameraService running
        // In case anything went wrong and the camera is still bound by this app we're releasing it so that it
        // can be used by other apps again
        if (cameraService.reconnect(IS_RUNNING_CALLBACK_TIMEOUT)) {
            Log.w(Constants.TAG, "Zombie CameraService is running and it's "
                    + (isCameraServiceRequested() ? "" : "*not*") + " requested");
            cameraService.stop(
                    new ShutDownFinishedHandler(de.cyface.camera_service.MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED) {
                        @Override
                        public void shutDownFinished(long measurementIdentifier) {
                            Log.d(TAG, "onResume: zombie CameraService stopped");
                        }
                    });
            throw new IllegalStateException(
                    "Camera stopped manually as the camera was not released. This should not happen!");
        }
    }

    @Override
    public void onClick(View view) {
        // Disables the button. This is used to avoid a duplicate start command which crashes the SDK
        // until CY-4098 is implemented. It's automatically re-enabled as soon as the callback arrives.
        button.setEnabled(false);

        // The MaterialDialog implementation of the EnergySettings dialogs are not shown when called
        // from inside the IsRunningCallback. Thus, we call it here for now instead of in startCapturing()
        if ((buttonStatus.equals(FINISHED) || buttonStatus.equals(PAUSED)) && isRestrictionActive()) {
            return;
        }

        dataCapturingService.isRunning(IS_RUNNING_CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS, new IsRunningCallback() {
            @Override
            public void isRunning() {
                Validate.isTrue(buttonStatus == OPEN, "DataCapturingButton is out of sync.");
                stopCapturing();
            }

            @Override
            public void timedOut() {
                Validate.isTrue(buttonStatus != OPEN, "DataCapturingButton is out of sync.");

                try {
                    // If Measurement is paused, resume the measurement on a normal click
                    if (persistenceLayer.hasMeasurement(PAUSED)) {
                        resumeCapturing();
                        return;
                    }
                    startCapturing();

                } catch (final CursorIsNullException e) {
                    throw new IllegalStateException(e);
                }

            }
        });
    }

    @Override
    public boolean onLongClick(View v) {
        // Disables the button. This is used to avoid a duplicate start command which crashes the SDK
        // until CY-4098 is implemented. It's automatically re-enabled as soon as the callback arrives.
        button.setEnabled(false);

        // The MaterialDialog implementation of the EnergySettings dialogs are not shown when called
        // from inside the IsRunningCallback. Thus, we call it here for now instead of in startCapturing()
        if ((buttonStatus.equals(FINISHED) || buttonStatus.equals(PAUSED)) && isRestrictionActive()) {
            return true;
        }

        dataCapturingService.isRunning(IS_RUNNING_CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS, new IsRunningCallback() {
            @Override
            public void isRunning() {
                Validate.isTrue(buttonStatus == OPEN, "DataCapturingButton is out of sync.");
                pauseCapturing();
            }

            @Override
            public void timedOut() {
                Validate.isTrue(buttonStatus != OPEN, "DataCapturingButton is out of sync.");

                try {
                    // If Measurement is paused, stop the measurement on long press
                    if (persistenceLayer.hasMeasurement(PAUSED)) {
                        stopCapturing();
                        return;
                    }
                    startCapturing();

                } catch (final CursorIsNullException e) {
                    throw new IllegalStateException(e);
                }
            }
        });

        return true;
    }

    /**
     * Pause capturing
     */
    private void pauseCapturing() {
        try {
            dataCapturingService.pause(new ShutDownFinishedHandler(MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED) {
                @Override
                public void shutDownFinished(final long measurementIdentifier) {
                    setButtonStatus(button, PAUSED);
                    setButtonEnabled(button);
                    Toast.makeText(context, R.string.toast_measurement_paused, Toast.LENGTH_SHORT).show();

                    // Also pause CameraService
                    // TODO: this way we don't notice when the camera was stopped unexpectedly
                    cameraService.isRunning(IS_RUNNING_CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS,
                            new IsRunningCallback() {
                                @Override
                                public void isRunning() {
                                    cameraService.pause(
                                            new ShutDownFinishedHandler(
                                                    de.cyface.camera_service.MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED) {
                                                @Override
                                                public void shutDownFinished(long measurementIdentifier) {
                                                    Log.d(TAG, "pauseCapturing: CameraService stopped");
                                                }
                                            });
                                }

                                @Override
                                public void timedOut() {
                                    Log.d(Constants.TAG, "pauseCapturing: no CameraService running, nothing to do");
                                }
                            });
                }
            });
        } catch (final NoSuchMeasurementException | CursorIsNullException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Stop capturing
     */
    private void stopCapturing() {
        try {
            dataCapturingService.stop(new ShutDownFinishedHandler(MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED) {
                @Override
                public void shutDownFinished(final long measurementIdentifier) {
                    currentMeasurementsTracks = null;
                    setButtonStatus(button, FINISHED);
                    setButtonEnabled(button);
                }
            });
            runOnUiThread(() -> map.clearMap());
        } catch (final NoSuchMeasurementException | CursorIsNullException e) {
            throw new IllegalStateException(e);
        }

        // Stop camera capturing if it is running
        // TODO: this way we don't notice when the camera was stopped unexpectedly
        cameraService.isRunning(IS_RUNNING_CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS, new IsRunningCallback() {
            @Override
            public void isRunning() {
                cameraService.stop(new ShutDownFinishedHandler(
                        de.cyface.camera_service.MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED) {
                    @Override
                    public void shutDownFinished(long measurementIdentifier) {
                        Log.d(TAG, "stopCapturing: CameraService stopped");
                    }
                });
            }

            @Override
            public void timedOut() {
                Log.d(Constants.TAG, "stopCapturing: no CameraService running, nothing to do");
            }
        });
    }

    /**
     * Starts capturing
     */
    private void startCapturing() {

        // Measurement is stopped, so we start a new measurement
        try {
            if (persistenceLayer.hasMeasurement(OPEN) && isProblematicManufacturer()) {
                showToastOnMainThread(
                        context.getString(R.string.toast_last_tracking_crashed),
                        true);
            }
        } catch (final CursorIsNullException e) {
            throw new IllegalStateException(e);
        }

        // We use a handler to run the UI Code on the main thread as it is supposed to be
        runOnUiThread(() -> {
            calibrationProgressDialog = createAndShowCalibrationDialog();
            scheduleProgressDialogDismissal(calibrationProgressDialog, calibrationDialogListener);
        });

        // TODO [CY-3855]: we have to provide a listener for the button (<- ???)
        try {
            final Modality modality = Modality.valueOf(preferences.getString(PREFERENCES_MODALITY_KEY, null));
            Validate.notNull(modality);

            currentMeasurementsTracks = new ArrayList<>();
            currentMeasurementsTracks.add(new Track());

            dataCapturingService.start(modality,
                    new StartUpFinishedHandler(MessageCodes.getServiceStartedActionId(context.getPackageName())) {
                        @Override
                        public void startUpFinished(final long measurementIdentifier) {
                            Log.v(TAG, "startUpFinished");
                            setButtonStatus(button, OPEN);
                            setButtonEnabled(button);

                            // Start CameraService
                            if (isCameraServiceRequested()) {
                                Log.d(Constants.TAG, "CameraServiceRequested");
                                try {
                                    startCameraService(measurementIdentifier);
                                } catch (DataCapturingException | MissingPermissionException
                                        | CursorIsNullException e) {
                                    throw new IllegalStateException(e);
                                }
                            }
                        }
                    });
        } catch (final DataCapturingException | CursorIsNullException | MissingPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean isRestrictionActive() {

        if (context == null) {
            Log.w(TAG, "Context is null, restrictions cannot be checked");
            return false;
        }
        final Activity activity = mainFragment.getActivity();
        if (activity == null) {
            Log.w(TAG, "Activity is null. If needed, dialogs wont appear.");
        }

        if (!spaceAvailable()) {
            showToastOnMainThread(context.getString(R.string.error_message_capturing_canceled_no_space), false);
            setButtonEnabled(button);
            return true;
        }
        if (!isGnssEnabled(context)) {
            showGnssWarningDialog(activity);
            setButtonEnabled(button);
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isEnergySaferActive(context)) {
            showEnergySaferWarningDialog(activity);
            setButtonEnabled(button);
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isBackgroundProcessingRestricted(context)) {
            showRestrictedBackgroundProcessingWarningDialog(activity);
            setButtonEnabled(button);
            return true;
        }
        return false;
    }

    /**
     * Resumes capturing
     */
    private void resumeCapturing() {

        Log.d(TAG, "resumeCachedTrack: Adding new sub track to existing cached track");
        currentMeasurementsTracks.add(new Track());

        try {
            dataCapturingService.resume(
                    new StartUpFinishedHandler(MessageCodes.getServiceStartedActionId(context.getPackageName())) {
                        @Override
                        public void startUpFinished(final long measurementIdentifier) {
                            Log.v(TAG, "resumeCapturing: startUpFinished");
                            setButtonStatus(button, OPEN);
                            setButtonEnabled(button);
                            Toast.makeText(context, R.string.toast_measurement_resumed, Toast.LENGTH_SHORT).show();

                            // Start CameraService
                            if (isCameraServiceRequested()) {
                                Log.d(Constants.TAG, "CameraServiceRequested");
                                try {
                                    startCameraService(measurementIdentifier);
                                } catch (DataCapturingException | MissingPermissionException
                                        | CursorIsNullException e) {
                                    throw new IllegalStateException(e);
                                }
                            }
                        }
                    });
        } catch (final NoSuchMeasurementException | DataCapturingException | CursorIsNullException
                | MissingPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Shows a toast message explicitly on the main thread.
     *
     * @param toastMessage The message to show
     * @param longDuration {@code True} if the toast should be shown for a longer time
     */
    private void showToastOnMainThread(final String toastMessage, final boolean longDuration) {
        new Handler(Looper.getMainLooper()).post(() -> Toast
                .makeText(context, toastMessage, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show());
    }

    /**
     * Starts the camera service and, thus, the camera capturing.
     *
     * @param measurementId the id of the measurement for which camera data is to be captured
     * @throws DataCapturingException If the asynchronous background service did not start successfully or no valid
     *             Android context was available.
     * @throws CursorIsNullException If {@link ContentProvider} was inaccessible.
     * @throws MissingPermissionException If no Android <code>ACCESS_FINE_LOCATION</code> has been granted. You may
     *             register a {@link UIListener} to ask the user for this permission and prevent the
     *             <code>Exception</code>. If the <code>Exception</code> was thrown the service does not start.
     */
    private void startCameraService(final long measurementId)
            throws DataCapturingException, MissingPermissionException, CursorIsNullException {

        final boolean rawModeSelected = preferences.getBoolean(PREFERENCES_CAMERA_RAW_MODE_ENABLED_KEY, false);
        final boolean videoModeSelected = preferences.getBoolean(PREFERENCES_CAMERA_VIDEO_MODE_ENABLED_KEY, false);
        // We need to load and pass the preferences for the camera focus here as the preferences
        // do not work reliably on multi-process access. https://stackoverflow.com/a/27987956/5815054
        final boolean staticFocusSelected = preferences.getBoolean(PREFERENCES_CAMERA_STATIC_FOCUS_ENABLED_KEY,
                false);
        final float staticFocusDistance = preferences.getFloat(PREFERENCES_CAMERA_STATIC_FOCUS_DISTANCE_KEY,
                Constants.DEFAULT_STATIC_FOCUS_DISTANCE);
        final boolean distanceBasedTriggeringSelected = preferences.getBoolean(
                PREFERENCES_CAMERA_DISTANCE_BASED_TRIGGERING_ENABLED_KEY,
                true);
        final float triggeringDistance = preferences.getFloat(PREFERENCES_CAMERA_TRIGGERING_DISTANCE_KEY,
                Constants.DEFAULT_TRIGGERING_DISTANCE);
        final boolean staticExposureTimeSelected = preferences.getBoolean(
                PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_ENABLED_KEY,
                false);
        final long staticExposureTime = preferences.getLong(PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_KEY,
                Constants.DEFAULT_STATIC_EXPOSURE_TIME);
        final int exposureValueIso100 = preferences.getInt(PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_EXPOSURE_VALUE_KEY,
                Constants.DEFAULT_STATIC_EXPOSURE_VALUE_ISO_100);

        cameraService.start(measurementId, videoModeSelected, rawModeSelected, staticFocusSelected,
                staticFocusDistance, staticExposureTimeSelected, staticExposureTime, exposureValueIso100,
                distanceBasedTriggeringSelected, triggeringDistance,
                new StartUpFinishedHandler(
                        de.cyface.camera_service.MessageCodes.getServiceStartedActionId(context.getPackageName())) {
                    @Override
                    public void startUpFinished(final long measurementIdentifier) {
                        Log.v(Constants.TAG, "startCameraService: CameraService startUpFinished");
                    }
                });
    }

    /**
     * Creates and shows {@link ProgressDialog} to inform the user about calibration at the beginning of a
     * measurement.
     *
     * @return A reference to the ProgressDialog which can be used to dismiss it.
     */
    private ProgressDialog createAndShowCalibrationDialog() {
        return ProgressDialog.show(context, context.getString(R.string.title_dialog_starting_data_capture),
                context.getString(R.string.msg_calibrating), true, false, dialog -> {
                    try {
                        dataCapturingService
                                .stop(new ShutDownFinishedHandler(MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED) {
                                    @Override
                                    public void shutDownFinished(final long l) {
                                        // nothing to do
                                    }
                                });
                        if (isCameraServiceRequested()) {
                            cameraService.stop(new ShutDownFinishedHandler(
                                    de.cyface.camera_service.MessageCodes.LOCAL_BROADCAST_SERVICE_STOPPED) {
                                @Override
                                public void shutDownFinished(final long l) {
                                    // nothing to do
                                }
                            });
                        }
                    } catch (final NoSuchMeasurementException | CursorIsNullException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    /**
     * Dismisses a (calibration) ProgressDialog and informs the {@link CalibrationDialogListener}s
     * about the dismissal.
     *
     * @param progressDialog The {@link ProgressDialog} to dismiss
     * @param calibrationDialogListener The {@link Collection<CalibrationDialogListener>} to inform
     */
    private void scheduleProgressDialogDismissal(final ProgressDialog progressDialog,
            final Collection<CalibrationDialogListener> calibrationDialogListener) {
        new Handler().postDelayed(() -> dismissCalibrationDialog(progressDialog, calibrationDialogListener),
                CALIBRATION_DIALOG_TIMEOUT);
    }

    private void dismissCalibrationDialog(final ProgressDialog progressDialog,
            final Collection<CalibrationDialogListener> calibrationDialogListener) {
        if (progressDialog != null) {
            progressDialog.dismiss();
            for (CalibrationDialogListener calibrationDialogListener1 : calibrationDialogListener) {
                calibrationDialogListener1.onCalibrationDialogFinished();
            }
        }
    }

    /**
     * Loads the track of the currently captured measurement from the database.
     * <p>
     * This is required when the app is resumed after being in background and probably missing locations
     * and when the app is restarted.
     */
    private void updateCachedTrack() {
        try {
            if (!persistenceLayer.hasMeasurement(MeasurementStatus.OPEN)
                    && !persistenceLayer.hasMeasurement(MeasurementStatus.PAUSED)) {
                Log.d(TAG, "updateCachedTrack: No unfinished measurement found, un-setting cache.");
                currentMeasurementsTracks = null;
                return;
            }

            Log.d(TAG, "updateCachedTrack: Unfinished measurement found, loading track from database.");
            final Measurement measurement = dataCapturingService.loadCurrentlyCapturedMeasurement();
            final List<Track> loadedList = persistenceLayer.loadTracks(measurement.getIdentifier(),
                    new DefaultLocationCleaningStrategy());
            // We need to make sure we return a list which supports "add" even when an empty list is returned
            // or else the onHostResume method cannot add a new sub track to a loaded empty list
            currentMeasurementsTracks = new ArrayList<>(loadedList);
        } catch (NoSuchMeasurementException | CursorIsNullException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Track> getCurrentMeasurementsTracks() {
        return currentMeasurementsTracks;
    }

    public List<Event> loadCurrentMeasurementsEvents() throws CursorIsNullException, NoSuchMeasurementException {
        final Measurement measurement = dataCapturingService.loadCurrentlyCapturedMeasurement();
        return persistenceLayer.loadEvents(measurement.getIdentifier(),
                Event.EventType.MODALITY_TYPE_CHANGE);
    }

    @Override
    public void onDestroyView() {
        button.setOnClickListener(null);
        disconnect(dataCapturingService, cameraService);
        dismissCalibrationDialog(calibrationProgressDialog, calibrationDialogListener);
    }

    /**
     * Unbinds the services. They continue to run in the background but won't send any updates to this button.
     * <p>
     * Instead of only disconnecting when `isRunning()` returns a timeout we always disconnect.
     * This way the race condition between view destruction and timeout is prevented [MOV-588].
     *
     * @param dataCapturingService the capturing service to unregister from
     * @param cameraService the camera service to unregister from
     */
    private void disconnect(final DataCapturingService dataCapturingService, final CameraService cameraService) {
        if (dataCapturingService == null) {
            Log.w(TAG, "Skipping DCS.disconnect() as DCS is null");
            // This should not happen, thus, reporting to Sentry

            if (isReportingEnabled) {
                Sentry.captureMessage("DCButton.onDestroyView: dataCapturingService is null");
            }
        } else {
            try {
                dataCapturingService.disconnect();
            } catch (DataCapturingException e) {
                // This just tells us there is no running capturing in the background, see [MOV-588]
                Log.d(TAG, "No need to unbind as the background service was not running.");
            }
        }

        if (cameraService == null) {
            Log.d(TAG, "Skipping CameraService.disconnect() as CameraService is null");
            // No need to capture this as this is always null when camera is disabled
        } else {
            try {
                cameraService.disconnect();
            } catch (DataCapturingException e) {
                // This just tells us there is no running capturing in the background, see [MOV-588]
                Log.d(TAG, "No need to unbind as the camera background service was not running.");
            }
        }
    }

    @Override
    public void addButtonListener(final ButtonListener buttonListener) {
        Validate.notNull(buttonListener);
        this.listener.add(buttonListener);
    }

    private boolean isCameraServiceRequested() {
        return preferences.getBoolean(PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY, false);
    }

    @Override
    public void onFixAcquired() {
        // Nothing to do
    }

    @Override
    public void onFixLost() {
        // Nothing to do
    }

    @Override
    public void onNewGeoLocationAcquired(GeoLocation geoLocation) {
        Log.d(TAG, "onNewGeoLocationAcquired");
        final Measurement measurement;
        try {
            measurement = persistenceLayer.loadCurrentlyCapturedMeasurement();
        } catch (final NoSuchMeasurementException e) {
            // GeoLocations may also arrive shortly after a measurement was stopped. Thus, this may not crash.
            // This happened on the Emulator with emulated live locations.
            Log.w(TAG, "onNewGeoLocationAcquired: No currently captured measurement found, doing nothing.");
            if (!onNewGeoLocationAcquiredExceptionTriggered[0] && isReportingEnabled) {
                onNewGeoLocationAcquiredExceptionTriggered[0] = true;
                Sentry.captureException(e);
            }
            return;
        } catch (final CursorIsNullException e) {
            throw new IllegalStateException(e);
        }
        final int distanceMeter = (int)Math.round(measurement.getDistance());
        final double distanceKm = distanceMeter == 0 ? 0.0 : distanceMeter / 1000.0;
        final String distanceText = distanceKm + " km";
        Log.d(TAG, "Distance update: " + distanceText);
        distanceTextView.setText(distanceText);
        Log.d(TAG, "distanceTextView: " + distanceTextView.getText());
        Log.d(TAG, "distanceTextView: " + distanceTextView.isEnabled());

        addLocationToCachedTrack(geoLocation);
        final List<Event> currentMeasurementsEvents;
        try {
            currentMeasurementsEvents = loadCurrentMeasurementsEvents();
            mainFragment.getMap().renderMeasurement(currentMeasurementsTracks, currentMeasurementsEvents, false);
        } catch (final CursorIsNullException e) {
            Log.w(TAG, "onNewGeoLocationAcquired() failed to loadCurrentMeasurementsEvents(). "
                    + "Thus, map.renderMeasurement() is ignored. This should only happen id "
                    + "the capturing already stopped.");
            if (!onNewGeoLocationAcquiredExceptionTriggered[1] && isReportingEnabled) {
                onNewGeoLocationAcquiredExceptionTriggered[1] = true;
                Sentry.captureException(e);
            }
        } catch (NoSuchMeasurementException e) {
            Log.w(TAG, "onNewGeoLocationAcquired() failed to loadCurrentMeasurementsEvents(). "
                    + "Thus, map.renderMeasurement() is ignored. This should only happen id "
                    + "the capturing already stopped.");
            if (!onNewGeoLocationAcquiredExceptionTriggered[2] && isReportingEnabled) {
                onNewGeoLocationAcquiredExceptionTriggered[2] = true;
                Sentry.captureException(e);
            }
        }
    }

    private void addLocationToCachedTrack(@NonNull final GeoLocation location) {
        Validate.notNull("onNewGeoLocation - cached track is null", currentMeasurementsTracks);

        if (!location.isValid()) {
            Log.d(TAG, "updateCachedTrack: ignoring invalid point");
            return;
        }

        if (currentMeasurementsTracks.size() == 0) {
            Log.d(TAG, "updateCachedTrack: Loaded track is empty, creating new list with empty sub track");
            currentMeasurementsTracks = new ArrayList<>();
            currentMeasurementsTracks.add(new Track());
        }

        currentMeasurementsTracks.get(currentMeasurementsTracks.size() - 1).add(location);
    }

    @Override
    public void onNewSensorDataAcquired(CapturedData capturedData) {
        // Nothing to do here
    }

    @Override
    public void onNewPictureAcquired(final int picturesCaptured) {
        Log.d(Constants.TAG, "onNewPictureAcquired");
        final String text = context.getString(R.string.camera_images) + " " + picturesCaptured;
        cameraInfoTextView.setText(text);
        Log.d(TAG, "cameraInfoTextView: " + cameraInfoTextView.getText());
    }

    @Override
    public void onNewVideoStarted() {
        Log.d(Constants.TAG, "onNewVideoStarted");
    }

    @Override
    public void onVideoStopped() {
        Log.d(Constants.TAG, "onVideoStopped");
    }

    @Override
    public void onLowDiskSpace(DiskConsumption diskConsumption) {
        // Nothing to do here - handled by <code>DataCapturingEventHandler</code>
    }

    @Override
    public void onSynchronizationSuccessful() {
        // Nothing to do here
    }

    @Override
    public void onErrorState(final Exception e) {
        throw new IllegalStateException(e);
    }

    @Override
    public boolean onRequiresPermission(final String permission, final Reason reason) {
        return false;
    }

    @Override
    public void onCapturingStopped() {
        setButtonStatus(button, FINISHED);
    }

    /*
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
}
