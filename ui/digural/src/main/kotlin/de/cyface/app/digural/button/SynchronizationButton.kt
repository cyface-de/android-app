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
package de.cyface.app.digural.button

import android.accounts.AccountManager
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import com.github.lzyzsd.circleprogress.DonutProgress
import de.cyface.app.digural.MainActivity.Companion.accountWithTokenExists
import de.cyface.app.digural.utils.Constants.TAG
import de.cyface.app.utils.R
import de.cyface.app.utils.SharedConstants.PREFERENCES_SYNCHRONIZATION_KEY
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.synchronization.WiFiSurveyor
import de.cyface.utils.Validate

/**
 * A button listener for the button to trigger data sync and show its progress
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.3.5
 * @since 1.0.0
 */
class SynchronizationButton(dataCapturingService: CyfaceDataCapturingService) : AbstractButton {
    // TODO [CY-3855]: communication with MainFragment/Activity should use this listener instead of hard-coded
    // implementation
    private val listener: MutableCollection<ButtonListener>
    private var isActivated = false
    var context: Context? = null
        private set

    /**
     * The actual java button object, this class implements behaviour for.
     */
    private var button: ImageButton? = null
    private var progressView: DonutProgress? = null
    private var preferences: SharedPreferences? = null

    /**
     * [CyfaceDataCapturingService] to check [WiFiSurveyor.isConnected]
     */
    private val dataCapturingService: CyfaceDataCapturingService

    init {
        listener = HashSet()
        this.dataCapturingService = dataCapturingService
    }

    override fun onCreateView(button: ImageButton?, progress: DonutProgress?) {
        Validate.notNull(button)
        context = button!!.context
        this.button = button
        this.progressView = progress
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        onResume() // TODO[MOV-621] the parent's onResume, thus, this class's onResume should automatically be called
        button.setOnClickListener(this)
    }

    /**
     * Method to be called in the parent's Android life-cycle onResume method to make sure the sync button is in the
     * right state as the sync status might have changed while the view was paused.
     *
     * TODO [CY-3857]: We removed the preferences flags for the sync state. To enable a synchronized check
     * for the sync state we need to implement a hook for this in the SDK
     */
    fun onResume() {
        updateSyncButton()
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
    fun isSynchronizingChanged(newSyncState: Boolean) {
        if (newSyncState && !isActivated) setActivated()
        if (!newSyncState && isActivated) setDeactivated()
    }

    private fun setActivated() {
        button!!.visibility = View.INVISIBLE
        progressView!!.visibility = View.VISIBLE
        isActivated = true
    }

    private fun setDeactivated() {
        button!!.visibility = View.VISIBLE
        progressView!!.visibility = View.INVISIBLE
        progressView!!.progress = 0 // Or else last progress is shown upon restart
        isActivated = false
    }

    fun updateProgress(percent: Float) {
        progressView!!.progress = percent.toInt()
    }

    override fun onClick(view: View) {
        val context = view.context
        if (isActivated) {
            Log.w(TAG, "Data Sync button is out of sync.")
        }

        // We want to show the current state no matter if we actually perform a new sync or not
        updateSyncButton()

        // Check if syncable network is available
        val isConnected = dataCapturingService.wiFiSurveyor.isConnected
        Log.v(WiFiSurveyor.TAG, (if (isConnected) "" else "Not ") + "connected to syncable network")
        if (!isConnected) {
            Toast.makeText(
                context,
                context.getString(R.string.error_message_sync_canceled_no_wifi),
                Toast.LENGTH_SHORT
            )
                .show()
            return
        }

        // Check is sync is disabled via frontend
        val syncEnabled = dataCapturingService.wiFiSurveyor.isSyncEnabled
        val syncPreferenceEnabled = preferences!!.getBoolean(PREFERENCES_SYNCHRONIZATION_KEY, true)
        Validate.isTrue(
            syncEnabled == syncPreferenceEnabled,
            "sync " + (if (syncEnabled) "enabled" else "disabled")
                    + " but syncPreference " + if (syncPreferenceEnabled) "enabled" else "disabled"
        )
        if (!syncEnabled) {
            Toast.makeText(
                context, context.getString(R.string.error_message_sync_canceled_disabled),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Request instant Synchronization
        dataCapturingService.scheduleSyncNow()
    }

    /**
     * When sync is already serializing (which does not push sync status updates to the UI) but the
     * sync was started before the current view was created the sync button would still show that no
     * sync is active. Until we make the serialization push progress updates to the ui clicking the button
     * should at least change the button to isSyncing
     */
    private fun updateSyncButton() {

        // We can only check if periodic sync is enabled when there is already an account
        val accountManager = AccountManager.get(context)
        val validAccountExists = accountWithTokenExists(accountManager)
        if (!validAccountExists) {
            Log.d(TAG, "updateSyncButton: No validAccountExists, doing nothing.")
            return
        }

        // TODO [MOV-621]: this returns false but the serialization may still be running but wifi off
        /*
         * final boolean connectedToSyncableConnection = dataCapturingService.getWiFiSurveyor().isConnected();
         * Log.d(TAG, "SyncButton was clicked, updating button status to:" + connectedToSyncableConnection);
         * isSynchronizingChanged(connectedToSyncableConnection);
         */
    }

    override fun onDestroyView() {
        button!!.setOnClickListener(null)
    }

    override fun addButtonListener(buttonListener: ButtonListener?) {
        Validate.notNull(buttonListener)
        listener.add(buttonListener!!)
    }
}