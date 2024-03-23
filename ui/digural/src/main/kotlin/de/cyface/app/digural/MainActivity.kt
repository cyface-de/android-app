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
package de.cyface.app.digural

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AccountManagerFuture
import android.accounts.AuthenticatorException
import android.accounts.OperationCanceledException
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import de.cyface.app.digural.auth.WebdavAuth
import de.cyface.app.digural.auth.WebdavAuthenticator
import de.cyface.app.digural.button.ExternalCameraController
import de.cyface.app.digural.capturing.settings.SettingsProvider
import de.cyface.app.digural.capturing.settings.CustomSettings
import de.cyface.app.digural.databinding.ActivityMainBinding
import de.cyface.app.digural.notification.CameraEventHandler
import de.cyface.app.digural.notification.DataCapturingEventHandler
import de.cyface.app.digural.utils.Constants
import de.cyface.app.digural.utils.Constants.ACCOUNT_TYPE
import de.cyface.app.digural.utils.Constants.AUTHORITY
import de.cyface.app.utils.ServiceProvider
import de.cyface.app.utils.capturing.settings.UiSettings
import de.cyface.camera_service.settings.CameraSettings
import de.cyface.camera_service.background.camera.CameraListener
import de.cyface.camera_service.foreground.CameraService
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.DataCapturingListener
import de.cyface.persistence.SetupException
import de.cyface.datacapturing.model.CapturedData
import de.cyface.datacapturing.ui.Reason
import de.cyface.energy_settings.TrackingSettings.showEnergySaferWarningDialog
import de.cyface.energy_settings.TrackingSettings.showGnssWarningDialog
import de.cyface.energy_settings.TrackingSettings.showProblematicManufacturerDialog
import de.cyface.energy_settings.TrackingSettings.showRestrictedBackgroundProcessingWarningDialog
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.app.digural.upload.WebdavSyncService
import de.cyface.synchronization.WiFiSurveyor
import de.cyface.uploader.exception.SynchronisationException
import de.cyface.utils.settings.AppSettings
import de.cyface.utils.DiskConsumption
import de.cyface.utils.Validate
import io.sentry.Sentry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.lang.ref.WeakReference

/**
 * The base `Activity` for the actual Cyface measurement client. It's called by the [TermsOfUseActivity]
 * class.
 *
 * It calls the [de.cyface.app.digural.auth.LoginActivity] if the user is unauthorized and uses the
 * outcome to authorize [WebdavAuth].
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 5.0.0
 * @since 1.0.0
 */
class MainActivity : AppCompatActivity(), ServiceProvider, CameraServiceProvider, SettingsProvider {

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
     * The settings used by both, UIs and libraries.
     */
    override val appSettings: AppSettings
        get() = MeasuringClient.appSettings

    /**
     * The settings used by multiple UIs.
     */
    override lateinit var uiSettings: UiSettings

    /**
     * The settings specific to this ui.
     */
    override lateinit var customSettings: CustomSettings

    /**
     * The settings used to store the user preferences for the camera.
     */
    override lateinit var cameraSettings: CameraSettings

    /**
     * The authorization.
     */
    override lateinit var auth: WebdavAuth

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

    private val unInterestedCameraListener: CameraListener = object :
        CameraListener {
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
        uiSettings = UiSettings(this, "https://NOT_IN_USE")
        cameraSettings = CameraSettings(this)
        customSettings = CustomSettings(this)

        // Start DataCapturingService and CameraService
        val sensorFrequency = runBlocking { appSettings.sensorFrequencyFlow.first() }
        try {
            capturing = CyfaceDataCapturingService(
                this.applicationContext,
                AUTHORITY,
                ACCOUNT_TYPE,
                DataCapturingEventHandler(),
                unInterestedListener,  // here was the capturing button but it registers itself, too
                sensorFrequency,
                WebdavAuthenticator(this)
            )
            val deviceIdentifier = capturing.persistenceLayer.restoreOrCreateDeviceId()
            // Needs to be called after new CyfaceDataCapturingService() for the SDK to check and throw
            // a specific exception when the LOGIN_ACTIVITY was not set from the SDK using app.
            startSynchronization()
            // TODO: dataCapturingService!!.addConnectionStatusListener(this)
            cameraService = CameraService(
                this.applicationContext,
                CameraEventHandler(),
                unInterestedCameraListener, // here was the capturing button but it registers itself, too
                ExternalCameraController(deviceIdentifier)
            )
        } catch (e: SetupException) {
            throw IllegalStateException(e)
        }

        // Authorization
        auth = WebdavAuth(applicationContext, WebdavAuthenticator.settings)

        /****************************************************************************************/
        // Crashes with RuntimeException: `capturing`/`auth` not initialized when this is above
        // `capturing=` or `auth=` [RFR-618].
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
        showProblematicManufacturerDialog(this, false, Constants.SUPPORT_EMAIL, lifecycleScope)
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
            val reportErrors = runBlocking { appSettings.reportErrorsFlow.first() }
            if (reportErrors) {
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
    private fun startSynchronization() {
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

        // The LoginActivity is called by Android which handles the account creation (authentication)
        Log.d(TAG, "startSynchronization: No validAccountExists, requesting LoginActivity")
        accountManager.addAccount(
            ACCOUNT_TYPE,
            WebdavSyncService.AUTH_TOKEN_TYPE,
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
                    val account = accountManager1.getAccountsByType(ACCOUNT_TYPE)[0]
                    Validate.notNull(account)

                    // Set synchronizationEnabled to the current user preferences
                    val uploadEnabled = runBlocking { appSettings.uploadEnabledFlow.first() }
                    Log.d(
                        WiFiSurveyor.TAG,
                        "Setting syncEnabled for new account to preference: $uploadEnabled"
                    )
                    capturing.wiFiSurveyor.makeAccountSyncable(
                        account,
                        uploadEnabled
                    )
                    Log.d(TAG, "Starting WifiSurveyor with new account.")
                    capturing.startWifiSurveyor()
                } catch (e: OperationCanceledException) {
                    // Remove temp account when LoginActivity is closed during login [CY-5087]
                    val accounts = accountManager1.getAccountsByType(ACCOUNT_TYPE)
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
            val existingAccounts = accountManager.getAccountsByType(ACCOUNT_TYPE)
            Validate.isTrue(existingAccounts.size < 2, "More than one account exists.")
            return existingAccounts.isNotEmpty()
        }
    }
}
