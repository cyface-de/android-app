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
package de.cyface.app.ui;

import static de.cyface.app.ui.MainActivity.getMainActivityFromContext;
import static de.cyface.app.utils.Constants.ACCEPTED_REPORTING_KEY;
import static de.cyface.app.utils.Constants.ACCOUNT_TYPE;
import static de.cyface.app.utils.Constants.AUTHORITY;
import static de.cyface.app.utils.Constants.DEFAULT_SENSOR_FREQUENCY;
import static de.cyface.app.utils.Constants.PREFERENCES_MODALITY_KEY;
import static de.cyface.app.utils.Constants.PREFERENCES_SENSOR_FREQUENCY_KEY;
import static de.cyface.app.utils.Constants.PREFERENCES_SYNCHRONIZATION_KEY;
import static de.cyface.persistence.model.Modality.BICYCLE;
import static de.cyface.persistence.model.Modality.BUS;
import static de.cyface.persistence.model.Modality.CAR;
import static de.cyface.persistence.model.Modality.TRAIN;
import static de.cyface.persistence.model.Modality.WALKING;
import static de.cyface.synchronization.Constants.AUTH_TOKEN_TYPE;

import java.io.IOException;
import java.util.List;

import com.github.lzyzsd.circleprogress.DonutProgress;
import com.google.android.material.tabs.TabLayout;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import de.cyface.app.BuildConfig;
import de.cyface.app.R;
import de.cyface.app.ui.button.DataCapturingButton;
import de.cyface.app.ui.button.SynchronizationButton;
import de.cyface.app.ui.dialog.ModalityDialog;
import de.cyface.app.ui.notification.CameraEventHandler;
import de.cyface.app.ui.notification.DataCapturingEventHandler;
import de.cyface.app.utils.Constants;
import de.cyface.camera_service.CameraService;
import de.cyface.datacapturing.CyfaceDataCapturingService;
import de.cyface.datacapturing.exception.SetupException;
import de.cyface.persistence.exception.NoSuchMeasurementException;
import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.Modality;
import de.cyface.persistence.model.Track;
import de.cyface.synchronization.ConnectionStatusListener;
import de.cyface.synchronization.WiFiSurveyor;
import de.cyface.synchronization.exception.SynchronisationException;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;
import io.sentry.Sentry;

/**
 * A {@code Fragment} for the main UI used for data capturing and supervision of the capturing process.
 *
 * @author Armin Schnabel
 * @version 1.4.2
 * @since 1.0.0
 */
public class MainFragment extends Fragment implements ConnectionStatusListener {

    /**
     * The identifier for the {@link ModalityDialog} request which asks the user (initially) to select a
     * {@link Modality} preference.
     */
    public final static int DIALOG_INITIAL_MODALITY_SELECTION_REQUEST_CODE = 201909191;
    /**
     * The tag used to identify logging from this class.
     */
    private final static String TAG = "de.cyface.app.frag.main";
    /**
     * The {@code DataCapturingButton} which allows the user to control the capturing lifecycle.
     */
    private DataCapturingButton dataCapturingButton;
    /**
     * The {@code SynchronizationButton} which allows the user to manually trigger the synchronization
     * and to see the synchronization progress.
     */
    private SynchronizationButton syncButton;
    /**
     * The root element of this {@code Fragment}s {@code View}
     */
    private View fragmentRoot;
    /**
     * The {@code Map} used to visualize the ongoing capturing.
     */
    private Map map;
    /**
     * The {@code SharedPreferences} used to store the user's preferences.
     */
    private SharedPreferences preferences;
    /**
     * The {@code DataCapturingService} which represents the API of the Cyface Android SDK.
     */
    private CyfaceDataCapturingService dataCapturingService;
    /**
     * The {@code CameraService} which collects camera data if the user did activate this feature.
     */
    private CameraService cameraService;
    /**
     * The {@code Runnable} triggered when the {@code Map} is loaded and ready.
     */
    private final Runnable onMapReadyRunnable = new Runnable() {
        @Override
        public void run() {
            final List<Track> currentMeasurementsTracks = dataCapturingButton.getCurrentMeasurementsTracks();
            if (currentMeasurementsTracks == null) {
                return;
            }
            final List<Event> currentMeasurementsEvents;
            try {
                currentMeasurementsEvents = dataCapturingButton.loadCurrentMeasurementsEvents();
                map.renderMeasurement(currentMeasurementsTracks, currentMeasurementsEvents, false);
            } catch (final NoSuchMeasurementException | CursorIsNullException e) {
                final boolean isReportingEnabled = preferences.getBoolean(ACCEPTED_REPORTING_KEY, false);
                if (isReportingEnabled) {
                    Sentry.captureException(e);
                }
                Log.w(TAG,
                        "onMapReadyRunnable failed to loadCurrentMeasurementsEvents. Thus, map.renderMeasurement() is not executed. This should only happen when the capturing already stopped.");
            }
        }
    };

    /**
     * All non-graphical initializations should go into onCreate (which might be called before Activity's onCreate
     * finishes). All view-related initializations go into onCreateView and final initializations which depend on the
     * Activity's onCreate and the fragment's onCreateView to be finished belong into the onActivityCreated method
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        fragmentRoot = inflater.inflate(R.layout.fragment_capturing, container, false);
        this.dataCapturingButton = new DataCapturingButton(this);

        preferences = PreferenceManager.getDefaultSharedPreferences(fragmentRoot.getContext());
        final int sensorFrequency = preferences.getInt(PREFERENCES_SENSOR_FREQUENCY_KEY,
                DEFAULT_SENSOR_FREQUENCY);

        // Start DataCapturingService and CameraService
        try {
            dataCapturingService = new CyfaceDataCapturingService(fragmentRoot.getContext(),
                    fragmentRoot.getContext().getContentResolver(), AUTHORITY, Constants.ACCOUNT_TYPE,
                    BuildConfig.cyfaceServer, new DataCapturingEventHandler(), dataCapturingButton, sensorFrequency);
            // Needs to be called after new CyfaceDataCapturingService() for the SDK to check and throw
            // a specific exception when the LOGIN_ACTIVITY was not set from the SDK using app.
            startSynchronization(fragmentRoot.getContext());
            dataCapturingService.addConnectionStatusListener(this);

            cameraService = new CameraService(fragmentRoot.getContext(), fragmentRoot.getContext().getContentResolver(),
                    AUTHORITY, new CameraEventHandler(), dataCapturingButton);
        } catch (final SetupException | CursorIsNullException e) {
            throw new IllegalStateException(e);
        }

        this.syncButton = new SynchronizationButton(dataCapturingService);

        showModalitySelectionDialogIfNeeded();

        dataCapturingButton.onCreateView(fragmentRoot.findViewById(R.id.capture_data_main_button), null);
        map = new Map(fragmentRoot.findViewById(R.id.mapView), savedInstanceState, onMapReadyRunnable);
        syncButton.onCreateView(fragmentRoot.findViewById(R.id.data_sync_button),
                fragmentRoot.findViewById(R.id.connection_status_progress));
        dataCapturingButton.bindMap(map);

        return fragmentRoot;
    }

    /**
     * Starts the synchronization. Creates an {@link Account} if there is none as an account is required
     * for synchronization. If there was no account the synchronization is started when the async account
     * creation future returns to ensure the account is available at that point.
     *
     * @param context the {@link Context} to load the {@link AccountManager}
     */
    public void startSynchronization(final Context context) {
        final AccountManager accountManager = AccountManager.get(context);
        final boolean validAccountExists = accountWithTokenExists(accountManager);

        if (validAccountExists) {
            try {
                Log.d(TAG, "startSynchronization: Starting WifiSurveyor with exiting account.");
                dataCapturingService.startWifiSurveyor();
            } catch (SetupException e) {
                throw new IllegalStateException(e);
            }
            return;
        }

        // The LoginActivity is called by Android which handles the account creation
        Log.d(TAG, "startSynchronization: No validAccountExists, requesting LoginActivity");
        accountManager.addAccount(Constants.ACCOUNT_TYPE, AUTH_TOKEN_TYPE, null, null,
                getMainActivityFromContext(context), future -> {
                    final AccountManager accountManager1 = AccountManager.get(context);
                    try {
                        // allows to detect when LoginActivity is closed
                        future.getResult();

                        // The LoginActivity created a temporary account which cannot be used for synchronization.
                        // As the login was successful we now register the account correctly:
                        final Account account = accountManager1.getAccountsByType(ACCOUNT_TYPE)[0];
                        Validate.notNull(account);

                        // Set synchronizationEnabled to the current user preferences
                        final boolean syncEnabledPreference = preferences
                                .getBoolean(PREFERENCES_SYNCHRONIZATION_KEY, true);
                        Log.d(WiFiSurveyor.TAG,
                                "Setting syncEnabled for new account to preference: " + syncEnabledPreference);
                        dataCapturingService.getWiFiSurveyor().makeAccountSyncable(account, syncEnabledPreference);

                        Log.d(TAG, "Starting WifiSurveyor with new account.");
                        dataCapturingService.startWifiSurveyor();
                    } catch (OperationCanceledException e) {
                        // Remove temp account when LoginActivity is closed during login [CY-5087]
                        final Account[] accounts = accountManager1.getAccountsByType(ACCOUNT_TYPE);
                        if (accounts.length > 0) {
                            final Account account = accounts[0];
                            accountManager1.removeAccount(account, null, null);
                        }
                        // This closes the app when the LoginActivity is closed
                        getMainActivityFromContext(context).finish();
                    } catch (AuthenticatorException | IOException | SetupException e) {
                        throw new IllegalStateException(e);
                    }
                }, null);
    }

    /**
     * Checks if there is an account with an authToken.
     *
     * @param accountManager A reference to the {@link AccountManager}
     * @return true if there is an account with an authToken
     * @throws RuntimeException if there is more than one account
     */
    public static boolean accountWithTokenExists(final AccountManager accountManager) {
        final Account[] existingAccounts = accountManager.getAccountsByType(ACCOUNT_TYPE);
        Validate.isTrue(existingAccounts.length < 2, "More than one account exists.");
        return existingAccounts.length != 0;
    }

    private void showModalitySelectionDialogIfNeeded() {
        registerModalityTabSelectionListener();

        final String selectedModality = preferences.getString(PREFERENCES_MODALITY_KEY, null);
        if (selectedModality != null) {
            selectModalityTab();
            return;
        }

        final FragmentManager fragmentManager = getFragmentManager();
        Validate.notNull(fragmentManager);
        final ModalityDialog dialog = new ModalityDialog();
        dialog.setTargetFragment(this, DIALOG_INITIAL_MODALITY_SELECTION_REQUEST_CODE);
        dialog.setCancelable(false);
        dialog.show(fragmentManager, "MODALITY_DIALOG");
    }

    private void registerModalityTabSelectionListener() {

        final TabLayout tabLayout = fragmentRoot.findViewById(R.id.tab_layout);
        final Modality[] newModality = new Modality[1];
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {

                @Nullable
                final String oldModalityId = preferences.getString(PREFERENCES_MODALITY_KEY, null);
                @Nullable
                final Modality oldModality = oldModalityId == null ? null : Modality.valueOf(oldModalityId);

                switch (tab.getPosition()) {
                    case 0:
                        newModality[0] = CAR;
                        break;
                    case 1:
                        newModality[0] = BICYCLE;
                        break;
                    case 2:
                        newModality[0] = WALKING;
                        break;
                    case 3:
                        newModality[0] = BUS;
                        break;
                    case 4:
                        newModality[0] = TRAIN;
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown tab selected: " + tab.getPosition());
                }

                preferences.edit().putString(PREFERENCES_MODALITY_KEY, newModality[0].getDatabaseIdentifier()).apply();

                if (oldModality != null && oldModality.equals(newModality[0])) {
                    Log.d(TAG, "changeModalityType(): old (" + oldModality + " and new Modality (" + newModality[0]
                            + ") types are equal not recording event.");
                    return;
                }
                dataCapturingService.changeModalityType(newModality[0]);

                // Deactivated for pro app until we show them their own tiles:
                // if (map != null) { map.loadCyfaceTiles(newModality[0].getDatabaseIdentifier()); }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Nothing to do here
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Nothing to do here
            }
        });
    }

    /**
     * We use the activity result method as a callback from the {@code Modality} dialog to the main fragment
     * to setup the tabs as soon as a {@link Modality} type is selected the first time
     *
     * @param requestCode is used to differentiate and identify requests
     * @param resultCode is used to describe the request's result
     * @param data an intent which may contain result data
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == DIALOG_INITIAL_MODALITY_SELECTION_REQUEST_CODE) {
            selectModalityTab();
        }
    }

    /**
     * Depending on which {@link Modality} is selected in the preferences the respective tab is selected.
     * Also, the tiles relative to the selected {@code Modality} are loaded onto the map, if enabled.
     *
     * Make sure the order (0, 1, 2 from left(start) to right(end)) in the TabLayout xml file is consistent
     * with the order here to map the correct enum to each tab.
     */
    private void selectModalityTab() {
        final TabLayout tabLayout = fragmentRoot.findViewById(R.id.tab_layout);
        final String modality = preferences.getString(PREFERENCES_MODALITY_KEY, null);
        Validate.notNull(modality, "Modality should already be set but isn't.");

        // Select the Modality tab
        final TabLayout.Tab tab;
        if (modality.equals(CAR.name())) {
            tab = tabLayout.getTabAt(0);
        } else if (modality.equals(BICYCLE.name())) {
            tab = tabLayout.getTabAt(1);
        } else if (modality.equals(WALKING.name())) {
            tab = tabLayout.getTabAt(2);
        } else if (modality.equals(BUS.name())) {
            tab = tabLayout.getTabAt(3);
        } else if (modality.equals(TRAIN.name())) {
            tab = tabLayout.getTabAt(4);
        } else {
            throw new IllegalArgumentException("Unknown Modality id: " + modality);
        }
        Validate.notNull(tab);
        tab.select();
    }

    void necessaryPermissionsGranted() {
        if (fragmentRoot != null && fragmentRoot.isShown()) {
            map.showAndMoveToCurrentLocation(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        syncButton.onResume();
        dataCapturingService.addConnectionStatusListener(this);
        map.onResume();
        dataCapturingButton.onResume(dataCapturingService, cameraService);
    }

    @Override
    public void onPause() {
        map.onPause();
        dataCapturingButton.onPause();
        dataCapturingService.removeConnectionStatusListener(this);
        super.onPause();
    }

    @Override
    public void onSyncStarted() {
        if (isAdded()) {
            syncButton.isSynchronizingChanged(true);
        } else {
            Log.w(TAG, "onSyncStarted called but fragment is not attached");
        }
    }

    @Override
    public void onSyncFinished() {
        if (isAdded()) {
            syncButton.isSynchronizingChanged(false);
        } else {
            Log.w(TAG, "onSyncFinished called but fragment is not attached");
        }
    }

    @Override
    public void onProgress(final float percent, final long measurementId) {
        if (isAdded()) {
            Log.v(TAG, "Sync progress received: " + percent + " %, mid: " + measurementId);
            syncButton.updateProgress(percent);
        }
    }

    @Override
    public void onDestroyView() {
        syncButton.onDestroyView();
        dataCapturingButton.onDestroyView();

        // Clean up CyfaceDataCapturingService
        try {
            // As the WifiSurveyor WiFiSurveyor.startSurveillance() tells us to
            dataCapturingService.shutdownDataCapturingService();
        } catch (SynchronisationException e) {
            final boolean isReportingEnabled = preferences.getBoolean(ACCEPTED_REPORTING_KEY, false);
            if (isReportingEnabled) {
                Sentry.captureException(e);
            }
            Log.w(TAG, "Failed to shut down CyfaceDataCapturingService. ", e);
        }
        dataCapturingService.removeConnectionStatusListener(this);
        Log.d(TAG, "onDestroyView: stopped CyfaceDataCapturingService");

        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        ImageView imageView = (ImageView)fragmentRoot.findViewById(R.id.capture_data_main_button).getTag();
        if (imageView != null)
            outState.putInt("capturing_button_resource_id", imageView.getId());
        imageView = (ImageView)fragmentRoot.findViewById(R.id.data_sync_button).getTag();
        if (imageView != null)
            outState.putInt("data_sync_button_id", imageView.getId());
        try {
            final DonutProgress donutProgress = (DonutProgress)fragmentRoot
                    .findViewById(R.id.connection_status_progress).getTag();
            if (imageView != null)
                outState.putInt("connection_status_progress_id", donutProgress.getId());
        } catch (final NullPointerException e) {
            Log.w(TAG, "Failed to save donutProgress view state");
        }
        super.onSaveInstanceState(outState);
    }

    public CyfaceDataCapturingService getDataCapturingService() {
        return dataCapturingService;
    }

    public Map getMap() {
        return map;
    }
}
