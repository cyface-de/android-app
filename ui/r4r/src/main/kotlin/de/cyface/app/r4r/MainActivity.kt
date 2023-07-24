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
package de.cyface.app.r4r

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AccountManagerFuture
import android.accounts.AuthenticatorException
import android.accounts.OperationCanceledException
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import de.cyface.app.r4r.auth.LoginActivity
import de.cyface.app.r4r.capturing.CapturingViewModel
import de.cyface.app.r4r.capturing.CapturingViewModelFactory
import de.cyface.app.utils.capturing.settings.CustomPreferences
import de.cyface.app.r4r.databinding.ActivityMainBinding
import de.cyface.app.r4r.utils.Constants.ACCOUNT_TYPE
import de.cyface.app.r4r.utils.Constants.AUTHORITY
import de.cyface.app.r4r.utils.Constants.SUPPORT_EMAIL
import de.cyface.app.r4r.utils.Constants.TAG
import de.cyface.app.utils.ServiceProvider
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
import de.cyface.synchronization.Constants.AUTH_TOKEN_TYPE
import de.cyface.synchronization.OAuth2
import de.cyface.synchronization.OAuth2.Companion.END_SESSION_REQUEST_CODE
import de.cyface.synchronization.WiFiSurveyor
import de.cyface.uploader.exception.SynchronisationException
import de.cyface.utils.AppPreferences
import de.cyface.utils.AppPreferences.Companion.DEFAULT_SENSOR_FREQUENCY
import de.cyface.utils.DiskConsumption
import de.cyface.utils.Validate
import io.sentry.Sentry
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.TokenResponse
import java.io.IOException

/**
 * The base `Activity` for the actual Cyface measurement client. It's called by the
 * [de.cyface.app.r4r.TermsOfUseActivity] class.
 *
 * It calls the [de.cyface.app.r4r.auth.LoginActivity] if the user is unauthorized and uses the
 * outcome of the OAuth 2 authorization flow to negotiate the final authorized state. This is done
 * by performing the "authorization code exchange" if required.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 3.2.0
 */
class MainActivity : AppCompatActivity(), ServiceProvider {

    /**
     * The generated class which holds all bindings from the layout file.
     */
    private lateinit var binding: ActivityMainBinding

    /**
     * The capturing service object which controls data capturing and synchronization.
     */
    override lateinit var capturing: CyfaceDataCapturingService

    /**
     * The controller which allows to navigate through the navigation graph.
     */
    private lateinit var navigation: NavController

    /**
     * The `SharedPreferences` used to store the user's preferences.
     */
    private lateinit var preferences: CustomPreferences

    /**
     * The authorization.
     */
    override lateinit var auth: OAuth2

    /**
     * Instead of registering the `DataCapturingButton/CapturingFragment` here, the `CapturingFragment`
     * just registers and unregisters itself.
     *
     * TODO: Change interface of DCS constructor to not force us to do this.
     */
    private val unInterestedListener = object : DataCapturingListener {
        override fun onFixAcquired() {}
        override fun onFixLost() {}
        override fun onNewGeoLocationAcquired(position: ParcelableGeoLocation?) {}
        override fun onNewSensorDataAcquired(data: CapturedData?) {}
        override fun onLowDiskSpace(allocation: DiskConsumption?) {}
        override fun onSynchronizationSuccessful() {}
        override fun onErrorState(e: Exception?) {}
        override fun onRequiresPermission(permission: String, reason: Reason): Boolean {
            return false
        }

        override fun onCapturingStopped() {}
    }

    /**
     * Shared instance of the [CapturingViewModel] which is used by multiple `Fragments.
     */
    @Suppress("unused") // Used by Fragments
    private val capturingViewModel: CapturingViewModel by viewModels {
        CapturingViewModelFactory(
            capturing.persistenceLayer.measurementRepository!!,
            capturing.persistenceLayer.eventRepository!!,
            AppPreferences(this).getReportingAccepted()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        preferences = CustomPreferences(this)

        // The location permissions are requested in MapFragment which needs to react to results

        // If camera service is requested, check needed permissions
        /* final boolean cameraCapturingEnabled = preferences.getBoolean(PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY, false);
        final boolean permissionsMissing = ContextCompat.checkSelfPermission(this,
            Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        /*
         * No permissions needed as we write the data to the app specific directory.
         * || ContextCompat.checkSelfPermission(this,
         * Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
         * || ContextCompat.checkSelfPermission(this,
         * Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
         */;
        if (cameraCapturingEnabled && permissionsMissing) {
            ActivityCompat.requestPermissions(this,
                new String[] {Manifest.permission.CAMERA/*
                                                             * , Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                             * Manifest.permission.READ_EXTERNAL_STORAGE
                                                             */},
                PERMISSION_REQUEST_CAMERA_AND_STORAGE_PERMISSION);
        }*/

        // Start DataCapturingService and CameraService
        try {
            capturing = CyfaceDataCapturingService(
                applicationContext,
                AUTHORITY,
                ACCOUNT_TYPE,
                BuildConfig.cyfaceServer,
                OAuth2.Companion.oauthConfig(BuildConfig.oauthRedirect, BuildConfig.oauthDiscovery),
                CapturingEventHandler(),
                unInterestedListener,
                DEFAULT_SENSOR_FREQUENCY
            )
            // Needs to be called after new CyfaceDataCapturingService() for the SDK to check and throw
            // a specific exception when the LOGIN_ACTIVITY was not set from the SDK using app.
            //startSynchronization() // We do this in displayAuthorized() instead!
            // We don't have a sync progress button: `capturingService.addConnectionStatusListener(this)`
            /*cameraService = CameraService(
                fragmentRoot.getContext(), fragmentRoot.getContext().getContentResolver(),
                de.cyface.app.utils.Constants.AUTHORITY, CameraEventHandler(), dataCapturingButton
            )*/
        } catch (e: SetupException) {
            throw IllegalStateException(e)
        }

        // Authorization
        auth = OAuth2(applicationContext)

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
                    R.id.navigation_capturing,
                    de.cyface.app.utils.R.id.navigation_statistics
                )
            )
        )

        // Not showing manufacturer warning on each resume to increase likelihood that it's read
        showProblematicManufacturerDialog(this, false, SUPPORT_EMAIL)

        // Inject the Incentives API URL into the preferences, as the `Incentives` from `utils`
        // cannot reach the `ui.rfr.BuildConfig`.
        setIncentivesServerUrl()
    }

    override fun onStart() {
        super.onStart()

        // All good, user is authorized
        if (auth.isAuthorized()) {
            onAuthorized("onStart")
            return
        }

        // the stored AuthState is incomplete, so check if we are currently receiving the result of
        // the authorization flow from the browser.
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)
        if (response != null || ex != null) {
            auth.updateAfterAuthorization(response, ex)
        }
        if (response?.authorizationCode != null) {
            // authorization code exchange is required
            auth.updateAfterAuthorization(response, ex)
            exchangeAuthorizationCode(response)
        } else if (ex != null) {
            onUnauthorized("Auth flow failed: " + ex.message)
        } else {
            // The user is not logged in / logged out -> LoginActivity is called
            onUnauthorized("No auth state retained - re-auth required", false)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Authorization
        if (requestCode == END_SESSION_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Handler().postDelayed({ signOut(true); finish() }, 2000)
        } else {
            show("Sign out canceled")
        }
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
            if (preferences.getReportingAccepted()) {
                Sentry.captureException(e)
            }
            Log.w(TAG, "Failed to shut down CyfaceDataCapturingService. ", e)
        }

        // Authorization
        auth.dispose()
    }

    /**
     * Starts the synchronization.
     *
     * Creates an [Account] if there is none as an account is required for synchronization. If there was
     * no account the synchronization is started when the async account creation future returns to ensure
     * the account is available at that point.
     */
    fun startSynchronization() {
        val accountManager = AccountManager.get(this.applicationContext)
        val validAccountExists = accountWithTokenExists(accountManager)
        if (validAccountExists) {
            try {
                Log.d(TAG, "startSynchronization: Starting WifiSurveyor with exiting account.")
                capturing.startWifiSurveyor()
            } catch (e: SetupException) {
                throw java.lang.IllegalStateException(e)
            }
            return
        }

        // The LoginActivity is called by Android which handles the account creation (authentication)
        Log.d(TAG, "startSynchronization: No validAccountExists, requesting LoginActivity")
        accountManager.addAccount(
            ACCOUNT_TYPE,
            AUTH_TOKEN_TYPE,
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
                    val syncEnabledPreference = preferences.getUpload()
                    Log.d(
                        WiFiSurveyor.TAG,
                        "Setting syncEnabled for new account to preference: $syncEnabledPreference"
                    )
                    capturing.wiFiSurveyor.makeAccountSyncable(
                        account, syncEnabledPreference
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
                    throw java.lang.IllegalStateException(e)
                } catch (e: IOException) {
                    throw java.lang.IllegalStateException(e)
                } catch (e: SetupException) {
                    throw java.lang.IllegalStateException(e)
                }
            },
            null
        )
    }

    /**
     * Checks if there is an account with an authToken.
     *
     * @param accountManager A reference to the [AccountManager]
     * @return true if there is an account with an authToken
     * @throws RuntimeException if there is more than one account
     */
    private fun accountWithTokenExists(accountManager: AccountManager): Boolean {
        val existingAccounts = accountManager.getAccountsByType(ACCOUNT_TYPE)
        Validate.isTrue(existingAccounts.size < 2, "More than one account exists.")
        return existingAccounts.isNotEmpty()
    }

    /**
     * As long as the server URL is hardcoded we want to reset it when it's different from the
     * default URL set in the [BuildConfig]. If not, hardcoded updates would not have an
     * effect.
     */
    private fun setIncentivesServerUrl() {
        val storedServer = preferences.getIncentivesUrl()
        val server = BuildConfig.incentivesServer
        @Suppress("KotlinConstantConditions")
        Validate.isTrue(server != "null")
        if (storedServer == null || storedServer != server) {
            Log.d(TAG, "Updating Incentives API URL from " + storedServer + "to" + server)
            preferences.saveIncentivesUrl(server)
        }
    }

    @MainThread
    private fun onUnauthorized(explanation: String, explain: Boolean = true) {
        runOnUiThread {
            if (explain) {
                show("unauthorized, logging out ... ($explanation)")
                Handler().postDelayed({ signOut(false) }, 2000)
            } else {
                signOut(false)
            }
        }
    }

    @MainThread
    private fun onAuthorized(message: String) {
        runOnUiThread {
            Log.d(TAG, "authorized ($message)")
            startSynchronization()
        }
    }

    @MainThread
    private fun exchangeAuthorizationCode(authorizationResponse: AuthorizationResponse) {
        Log.d(TAG, "Exchanging authorization code")
        val requestSuccessful = auth.performTokenRequest(
            authorizationResponse.createTokenExchangeRequest()
        ) { tokenResponse: TokenResponse?, authException: AuthorizationException? ->
            val authSuccessful = auth.handleCodeExchangeResponse(
                tokenResponse,
                authException,
                ACCOUNT_TYPE,
                applicationContext,
                AUTHORITY
            )
            if (authSuccessful) {
                onAuthorized("code exchanged, account updated")
            } else {
                onUnauthorized(
                    ("Authorization Code exchange failed"
                            + if (authException != null) authException.error else "")
                )
            }
        }
        if (!requestSuccessful) {
            onUnauthorized("Client authentication method is unsupported")
        }
    }

    /**
     * When the user explicitly wants to sign out, we need to send an `endSession` request to the
     * auth server, see `MenuProvider.logout`.
     *
     * Here we only want to clear the local data about the current session, when something changes.
     */
    @MainThread
    private fun signOut(removeAccount: Boolean = false) {
        auth.signOut()

        // E.g. `MainActivity.onStart()` calls `signOut()` when the user is already signed out
        // so there is no account to be removed.
        if (removeAccount) {
            // Also remove account from account manager
            capturing.removeAccount(capturing.wiFiSurveyor.account.name)
        }

        val mainIntent = Intent(this, LoginActivity::class.java)
        mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(mainIntent)
        finish()
    }

    @MainThread
    private fun show(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}