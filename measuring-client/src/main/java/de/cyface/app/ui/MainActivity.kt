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
package de.cyface.app.ui

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AccountManagerFuture
import android.accounts.AuthenticatorException
import android.accounts.OperationCanceledException
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.preference.PreferenceManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import de.cyface.app.BuildConfig
import de.cyface.app.CameraServiceProvider
import de.cyface.app.R
import de.cyface.app.databinding.ActivityMainBinding
import de.cyface.app.ui.notification.CameraEventHandler
import de.cyface.app.ui.notification.DataCapturingEventHandler
import de.cyface.app.utils.Constants
import de.cyface.app.utils.ServiceProvider
import de.cyface.app.utils.SharedConstants.ACCEPTED_REPORTING_KEY
import de.cyface.app.utils.SharedConstants.DEFAULT_SENSOR_FREQUENCY
import de.cyface.app.utils.SharedConstants.PREFERENCES_SENSOR_FREQUENCY_KEY
import de.cyface.app.utils.SharedConstants.PREFERENCES_SYNCHRONIZATION_KEY
import de.cyface.camera_service.CameraListener
import de.cyface.camera_service.CameraService
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.DataCapturingListener
import de.cyface.datacapturing.exception.SetupException
import de.cyface.datacapturing.model.CapturedData
import de.cyface.datacapturing.ui.Reason
import de.cyface.energy_settings.TrackingSettings.showEnergySaferWarningDialog
import de.cyface.energy_settings.TrackingSettings.showGnssWarningDialog
import de.cyface.energy_settings.TrackingSettings.showProblematicManufacturerDialog
import de.cyface.energy_settings.TrackingSettings.showRestrictedBackgroundProcessingWarningDialog
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.synchronization.WiFiSurveyor
import de.cyface.synchronization.exception.SynchronisationException
import de.cyface.utils.DiskConsumption
import de.cyface.utils.Validate
import io.sentry.Sentry
import java.io.IOException
import java.lang.ref.WeakReference

/**
 * The base `Activity` for the actual Cyface measurement client. It's called by the [TermsOfUseActivity]
 * class.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.3.1
 * @since 1.0.0
 */
class MainActivity : AppCompatActivity(), ServiceProvider, CameraServiceProvider {

    /**
     * The generated class which holds all bindings from the layout file.
     */
    private lateinit var binding: ActivityMainBinding

    /**
     * The `DataCapturingService` which represents the API of the Cyface Android SDK.
     */
    override lateinit var capturing: CyfaceDataCapturingService

    /**
     * The `CameraService` which collects camera data if the user did activate this feature.
     */
    override lateinit var cameraService: CameraService

    /**
     * The controller which allows to navigate through the navigation graph.
     */
    private lateinit var navigation: NavController

    /**
     * The `SharedPreferences` used to store the user's preferences.
     */
    private var preferences: SharedPreferences? = null

    /**
     * Instead of registering the `DataCapturingButton/CapturingFragment` here, the `CapturingFragment`
     * just registers and unregisters itself.
     */
    private val unInterestedListener: DataCapturingListener = object : DataCapturingListener {
        override fun onFixAcquired() {}
        override fun onFixLost() {}
        override fun onNewGeoLocationAcquired(position: ParcelableGeoLocation) {}
        override fun onNewSensorDataAcquired(data: CapturedData) {}
        override fun onLowDiskSpace(allocation: DiskConsumption) {}
        override fun onSynchronizationSuccessful() {}
        override fun onErrorState(e: Exception) {}
        override fun onRequiresPermission(permission: String, reason: Reason): Boolean {
            return false
        }

        override fun onCapturingStopped() {}
    }

    private val unInterestedCameraListener: CameraListener = object : CameraListener {
        override fun onNewPictureAcquired(picturesCaptured: Int) {}
        override fun onNewVideoStarted() {}
        override fun onVideoStopped() {}
        override fun onLowDiskSpace(allocation: DiskConsumption) {}
        override fun onErrorState(e: Exception) {}
        override fun onRequiresPermission(permission: String, reason: Reason): Boolean {
            return false
        }

        override fun onCapturingStopped() {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        // Location permissions are requested by MainFragment which needs to react to results

        // If camera service is requested, check needed permissions
        val cameraEnabled = preferences!!.getBoolean(
            de.cyface.camera_service.Constants.PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY,
            false
        )
        val permissionsMissing = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED /*
         * No permissions needed as we write the data to the app specific directory.
         * || ContextCompat.checkSelfPermission(this,
         * Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
         * || ContextCompat.checkSelfPermission(this,
         * Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
         */
        if (cameraEnabled && permissionsMissing) {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.CAMERA /*
                                                             * , Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                             * Manifest.permission.READ_EXTERNAL_STORAGE
                                                             */
                ),
                de.cyface.camera_service.Constants.PERMISSION_REQUEST_CAMERA_AND_STORAGE_PERMISSION
            )
        }

        // Start DataCapturingService and CameraService
        val sensorFrequency =
            preferences!!.getInt(PREFERENCES_SENSOR_FREQUENCY_KEY, DEFAULT_SENSOR_FREQUENCY)
        try {
            capturing = CyfaceDataCapturingService(
                this.applicationContext,
                Constants.AUTHORITY,
                Constants.ACCOUNT_TYPE,
                BuildConfig.cyfaceServer,
                DataCapturingEventHandler(),
                unInterestedListener,  // here was the capturing button but it registers itself, too
                sensorFrequency
            )
            // Needs to be called after new CyfaceDataCapturingService() for the SDK to check and throw
            // a specific exception when the LOGIN_ACTIVITY was not set from the SDK using app.
            startSynchronization()
            // FIXME: dataCapturingService!!.addConnectionStatusListener(this)
            cameraService = CameraService(
                this.applicationContext,
                CameraEventHandler(),
                unInterestedCameraListener // here was the capturing button but it registers itself, too
            )
        } catch (e: SetupException) {
            throw IllegalStateException(e)
        }

        // Crashes with RuntimeException: capturing not initialized when this is at the top of `onCreate`
        super.onCreate(savedInstanceState)

        // To access the `Activity`s' `capturingService` from `Fragment.onCreate` the
        // `capturingService` has to be initialized before calling `inflate`
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setting up top action bar & bottom menu (no sidebar, see RFR-333]
        // Not using `findNavController()` as `FragmentContainerView` in `activity_main.xml` does not
        // work with with `findNavController()` (https://stackoverflow.com/a/60434988/5815054).
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navigation = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navigation)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        setupActionBarWithNavController(
            navigation, AppBarConfiguration(
                // Adding top-level destinations so they are added to the back stack while navigating
                setOf(
                    de.cyface.app.utils.R.id.navigation_trips,
                    R.id.navigation_capturing/*,
                    R.id.navigation_statistics*/
                )
            )
        )

        // Not showing manufacturer warning on each resume to increase likelihood that it's read
        showProblematicManufacturerDialog(this, false, Constants.SUPPORT_EMAIL)
    }

    /**
     * Fixes "home" (back) button in the top action bar when in the fragment details fragment.
     */
    override fun onSupportNavigateUp(): Boolean {
        return navigation.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onResume() {
        showGnssWarningDialog(this)
        showEnergySaferWarningDialog(this)
        showRestrictedBackgroundProcessingWarningDialog(this)
        super.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up CyfaceDataCapturingService
        try {
            // As the WifiSurveyor WiFiSurveyor.startSurveillance() tells us to
            capturing.shutdownDataCapturingService()
            // Before we only called: shutdownConnectionStatusReceiver();
        } catch (e: SynchronisationException) {
            val isReportingEnabled = preferences!!.getBoolean(ACCEPTED_REPORTING_KEY, false)
            if (isReportingEnabled) {
                Sentry.captureException(e)
            }
            Log.w(TAG, "Failed to shut down CyfaceDataCapturingService. ", e)
        }
    }

    /**
     * Starts the synchronization.
     *
     * Creates an [Account] if there is none as an account is required
     * for synchronization. If there was no account the synchronization is started when the async account
     * creation future returns to ensure the account is available at that point.
     */
    fun startSynchronization() {
        val accountManager = AccountManager.get(this.applicationContext)
        val validAccountExists = accountWithTokenExists(accountManager)
        if (validAccountExists) {
            try {
                Log.d(TAG, "startSynchronization: Starting WifiSurveyor with exiting account.")
                capturing.startWifiSurveyor()
            } catch (e: SetupException) {
                throw IllegalStateException(e)
            }
            return
        }

        // The LoginActivity is called by Android which handles the account creation
        Log.d(TAG, "startSynchronization: No validAccountExists, requesting LoginActivity")
        accountManager.addAccount(
            Constants.ACCOUNT_TYPE,
            de.cyface.synchronization.Constants.AUTH_TOKEN_TYPE,
            null,
            null,
            this,
            { future: AccountManagerFuture<Bundle?> ->
                val accountManager1 = AccountManager.get(this.applicationContext)
                try {
                    // allows to detect when LoginActivity is closed
                    future.result

                    // The LoginActivity created a temporary account which cannot be used for synchronization.
                    // As the login was successful we now register the account correctly:
                    val account = accountManager1.getAccountsByType(Constants.ACCOUNT_TYPE)[0]
                    Validate.notNull(account)

                    // Set synchronizationEnabled to the current user preferences
                    val syncEnabledPreference = preferences!!
                        .getBoolean(PREFERENCES_SYNCHRONIZATION_KEY, true)
                    Log.d(
                        WiFiSurveyor.TAG,
                        "Setting syncEnabled for new account to preference: $syncEnabledPreference"
                    )
                    capturing.wiFiSurveyor.makeAccountSyncable(
                        account,
                        syncEnabledPreference
                    )
                    Log.d(TAG, "Starting WifiSurveyor with new account.")
                    capturing.startWifiSurveyor()
                } catch (e: OperationCanceledException) {
                    // Remove temp account when LoginActivity is closed during login [CY-5087]
                    val accounts = accountManager1.getAccountsByType(Constants.ACCOUNT_TYPE)
                    if (accounts.isNotEmpty()) {
                        val account = accounts[0]
                        accountManager1.removeAccount(account, null, null)
                    }
                    // This closes the app when the LoginActivity is closed
                    this.finish()
                } catch (e: AuthenticatorException) {
                    throw IllegalStateException(e)
                } catch (e: IOException) {
                    throw IllegalStateException(e)
                } catch (e: SetupException) {
                    throw IllegalStateException(e)
                }
            },
            null
        )
    }

    /**
     * Handles incoming inter process communication messages from services used by this application.
     * This is required to update the UI based on changes within those services (e.g. status).
     * - The Handler code runs all on the same (e.g. UI) thread.
     * - We don't use Broadcasts here to reduce the amount of broadcasts.
     *
     * @author Klemens Muthmann
     * @author Armin Schnabel
     * @version 1.0.2
     * @since 1.0.0
     * @property context The context [MainActivity] for this message handler.
     */
    private class IncomingMessageHandler(context: MainActivity) :
        Handler(context.mainLooper) {
        /**
         * A weak reference to the context activity this handler handles messages for. The weak reference is
         * necessary since the lifetime of the handler might be longer than the activity's and a normal reference would
         * hinder the garbage collector to destroy the activity in that instance.
         */
        private val context: WeakReference<MainActivity>

        init {
            this.context = WeakReference(context)
        }

        override fun handleMessage(msg: Message) {
            val activity = context.get()
                ?: // noinspection UnnecessaryReturnStatement
                return
            /*
             * switch (msg.what) {
             * case Message.WARNING_SPACE:
             * Log.d(TAG, "received MESSAGE about WARNING SPACE: Unbinding services !");
             * activity.unbindDataCapturingService();
             * final MainFragment mainFragment = (MainFragment)activity.getFragmentManager()
             * .findFragmentByTag(MAIN_FRAGMENT_TAG);
             * if (mainFragment != null) { // the fragment was switch just now this one can be null
             * mainFragment.dataCapturingButton.setDeactivated();
             * }
             * break;
             * default:
             * super.handleMessage(msg);
             * }
             */
        }
    }

    companion object {
        /**
         * The tag used to identify logging messages send to logcat.
         */
        const val TAG = Constants.PACKAGE

        /**
         * Checks if there is an account with an authToken.
         *
         * @param accountManager A reference to the [AccountManager]
         * @return true if there is an account with an authToken
         * @throws RuntimeException if there is more than one account
         */
        @JvmStatic
        fun accountWithTokenExists(accountManager: AccountManager): Boolean {
            val existingAccounts = accountManager.getAccountsByType(Constants.ACCOUNT_TYPE)
            Validate.isTrue(existingAccounts.size < 2, "More than one account exists.")
            return existingAccounts.isNotEmpty()
        }
    }
}