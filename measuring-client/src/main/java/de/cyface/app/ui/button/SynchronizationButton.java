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

import static de.cyface.app.ui.MainFragment.accountWithTokenExists;
import static de.cyface.app.utils.SharedConstants.PREFERENCES_SYNCHRONIZATION_KEY;
import static de.cyface.app.utils.Constants.TAG;

import java.util.Collection;
import java.util.HashSet;

import com.github.lzyzsd.circleprogress.DonutProgress;

import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;

import de.cyface.app.R;
import de.cyface.datacapturing.CyfaceDataCapturingService;
import de.cyface.synchronization.WiFiSurveyor;
import de.cyface.utils.Validate;

/**
 * A button listener for the button to trigger data sync and show its progress
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.3.4
 * @since 1.0.0
 */
public class SynchronizationButton implements AbstractButton {

    // TODO [CY-3855]: communication with MainFragment/Activity should use this listener instead of hard-coded
    // implementation
    private final Collection<ButtonListener> listener;
    private boolean isActivated;
    private Context context;
    /**
     * The actual java button object, this class implements behaviour for.
     */
    private ImageButton button;
    private DonutProgress progressView;
    private SharedPreferences preferences;
    /**
     * {@link CyfaceDataCapturingService} to check {@link WiFiSurveyor#isConnected()}
     */
    private final CyfaceDataCapturingService dataCapturingService;

    public SynchronizationButton(@NonNull final CyfaceDataCapturingService dataCapturingService) {
        this.listener = new HashSet<>();
        this.dataCapturingService = dataCapturingService;
    }

    @Override
    public void onCreateView(final ImageButton button, final DonutProgress progressView) {
        Validate.notNull(button);
        this.context = button.getContext();
        this.button = button;
        this.progressView = progressView;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
        onResume(); // TODO[MOV-621] the parent's onResume, thus, this class's onResume should automatically be called
        button.setOnClickListener(this);
    }

    /**
     * Method to be called in the parent's Android life-cycle onResume method to make sure the sync button is in the
     * right state as the sync status might have changed while the view was paused.
     *
     * TODO [CY-3857]: We removed the preferences flags for the sync state. To enable a synchronized check
     * for the sync state we need to implement a hook for this in the SDK
     */
    public void onResume() {
        updateSyncButton();
    }

    /**
     * The ContentResolver.isSyncActive does not work as expected on Nexus 5X when ~1,2GB of data is left to be synced.
     * It returns false before the sync is finished (often directly after starting the sync while it still runs in the
     * background).
     * The same issue is reported in the following:
     * http://porcupineprogrammer.blogspot.de/2013/01/android-sync-adapter-lifecycle.html
     * http://stackoverflow.com/a/13179369/5815054 (their solution didn't work)
     * For that reason we have to use a preference-flag to check the sync status / progress
     */
    public void isSynchronizingChanged(boolean newSyncState) {
        if (newSyncState && !isActivated)
            setActivated();
        if (!newSyncState && isActivated)
            setDeactivated();
    }

    private void setActivated() {
        button.setVisibility(View.INVISIBLE);
        progressView.setVisibility(View.VISIBLE);
        isActivated = true;
    }

    private void setDeactivated() {
        button.setVisibility(View.VISIBLE);
        progressView.setVisibility(View.INVISIBLE);
        progressView.setProgress(0); // Or else last progress is shown upon restart
        isActivated = false;
    }

    public void updateProgress(final float percent) {
        progressView.setProgress((int)percent);
    }

    @Override
    public void onClick(final View view) {
        final Context context = view.getContext();
        if (isActivated) {
            Log.w(TAG, "Data Sync button is out of sync.");
        }

        // We want to show the current state no matter if we actually perform a new sync or not
        updateSyncButton();

        // Check if syncable network is available
        final boolean isConnected = dataCapturingService.getWiFiSurveyor().isConnected();
        Log.v(WiFiSurveyor.TAG, (isConnected ? "" : "Not ") + "connected to syncable network");
        if (!isConnected) {
            Toast.makeText(context, context.getString(de.cyface.app.utils.R.string.error_message_sync_canceled_no_wifi), Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        // Check is sync is disabled via frontend
        final boolean syncEnabled = dataCapturingService.getWiFiSurveyor().isSyncEnabled();
        final boolean syncPreferenceEnabled = preferences.getBoolean(PREFERENCES_SYNCHRONIZATION_KEY, true);
        Validate.isTrue(syncEnabled == syncPreferenceEnabled, "sync " + (syncEnabled ? "enabled" : "disabled")
                + " but syncPreference " + (syncPreferenceEnabled ? "enabled" : "disabled"));
        if (!syncEnabled) {
            Toast.makeText(context, context.getString(de.cyface.app.utils.R.string.error_message_sync_canceled_disabled),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Request instant Synchronization
        dataCapturingService.scheduleSyncNow();
    }

    /**
     * When sync is already serializing (which does not push sync status updates to the UI) but the
     * sync was started before the current view was created the sync button would still show that no
     * sync is active. Until we make the serialization push progress updates to the ui clicking the button
     * should at least change the button to isSyncing
     */
    private void updateSyncButton() {

        // We can only check if periodic sync is enabled when there is already an account
        final AccountManager accountManager = AccountManager.get(context);
        final boolean validAccountExists = accountWithTokenExists(accountManager);
        if (!validAccountExists) {
            Log.d(TAG, "updateSyncButton: No validAccountExists, doing nothing.");
            return;
        }

        // TODO [MOV-621]: this returns false but the serialization may still be running but wifi off
        /*
         * final boolean connectedToSyncableConnection = dataCapturingService.getWiFiSurveyor().isConnected();
         * Log.d(TAG, "SyncButton was clicked, updating button status to:" + connectedToSyncableConnection);
         * isSynchronizingChanged(connectedToSyncableConnection);
         */
    }

    @Override
    public void onDestroyView() {
        button.setOnClickListener(null);
    }

    @Override
    public void addButtonListener(final ButtonListener buttonListener) {
        Validate.notNull(buttonListener);
        this.listener.add(buttonListener);
    }

    public Context getContext() {
        return context;
    }
}
