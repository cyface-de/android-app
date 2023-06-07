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
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.snackbar.Snackbar
import de.cyface.app.r4r.auth.AuthStateManager
import de.cyface.app.r4r.auth.Configuration
import de.cyface.app.r4r.auth.LoginActivity
import de.cyface.app.r4r.auth.TokenActivity
import de.cyface.app.r4r.capturing.CapturingViewModel
import de.cyface.app.r4r.capturing.CapturingViewModelFactory
import de.cyface.app.r4r.databinding.ActivityMainBinding
import de.cyface.app.r4r.utils.Constants.ACCOUNT_TYPE
import de.cyface.app.r4r.utils.Constants.AUTHORITY
import de.cyface.app.r4r.utils.Constants.SUPPORT_EMAIL
import de.cyface.app.r4r.utils.Constants.TAG
import de.cyface.app.utils.ServiceProvider
import de.cyface.app.utils.SharedConstants
import de.cyface.app.utils.SharedConstants.DEFAULT_SENSOR_FREQUENCY
import de.cyface.app.utils.SharedConstants.PREFERENCES_SYNCHRONIZATION_KEY
import de.cyface.app.utils.trips.incentives.Incentives.Companion.INCENTIVES_ENDPOINT_URL_SETTINGS_KEY
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
import de.cyface.synchronization.WiFiSurveyor
import de.cyface.uploader.exception.SynchronisationException
import de.cyface.utils.DiskConsumption
import de.cyface.utils.Validate
import io.sentry.Sentry
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.Charset
import java.util.Date
import java.util.concurrent.Executors

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
 * @version 1.1.0
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
    private lateinit var preferences: SharedPreferences

    /**
     * The service used for authorization.
     */
    private lateinit var mAuthService: AuthorizationService

    /**
     * The authorization state.
     */
    private lateinit var mStateManager: AuthStateManager

    /**
     * The configuration of the OAuth 2 endpoint to authorize against.
     */
    private lateinit var mConfiguration: Configuration

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
        val persistence = capturing.persistenceLayer
        CapturingViewModelFactory(
            persistence.measurementRepository!!, persistence.eventRepository!!
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        preferences = PreferenceManager.getDefaultSharedPreferences(this)

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
                BuildConfig.authServer,
                CapturingEventHandler(),
                unInterestedListener,
                DEFAULT_SENSOR_FREQUENCY
            )
            // Needs to be called after new CyfaceDataCapturingService() for the SDK to check and throw
            // a specific exception when the LOGIN_ACTIVITY was not set from the SDK using app.
            //FIXME: startSynchronization() - to disable endless loop after adding new auth flow
            // We don't have a sync progress button: `capturingService.addConnectionStatusListener(this)`
            /*cameraService = CameraService(
                fragmentRoot.getContext(), fragmentRoot.getContext().getContentResolver(),
                de.cyface.app.utils.Constants.AUTHORITY, CameraEventHandler(), dataCapturingButton
            )*/
        } catch (e: SetupException) {
            throw IllegalStateException(e)
        }

        /****************************************************************************************/
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

        // Authorization
        mStateManager = AuthStateManager.getInstance(this)
        //mExecutor = Executors.newSingleThreadExecutor()
        mConfiguration = Configuration.getInstance(this)
        val config = Configuration.getInstance(this)
        if (config.hasConfigurationChanged()) {
            Toast.makeText(this, "Authentifizierung ist abgelaufen", Toast.LENGTH_SHORT).show()
            signOut()
            return
        }
        mAuthService = AuthorizationService(
            this,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(config.connectionBuilder)
                .build()
        )
    }

    override fun onStart() {
        super.onStart()

        // Authorization
        if (mStateManager.current.isAuthorized) {
            displayAuthorized()
            return
        }
        // the stored AuthState is incomplete, so check if we are currently receiving the result of
        // the authorization flow from the browser.
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)
        if (response != null || ex != null) {
            mStateManager.updateAfterAuthorization(response, ex)
        }
        if (response?.authorizationCode != null) {
            // authorization code exchange is required
            mStateManager.updateAfterAuthorization(response, ex)
            exchangeAuthorizationCode(response)
        } else if (ex != null) {
            displayNotAuthorized("Authorization flow failed: " + ex.message)
        } else {
            displayNotAuthorized("No authorization state retained - reauthorization required")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Authorization
        if (requestCode == END_SESSION_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            signOut()
            finish()
        } else {
            displayEndSessionCancelled()
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
            val isReportingEnabled =
                preferences.getBoolean(SharedConstants.ACCEPTED_REPORTING_KEY, false)
            if (isReportingEnabled) {
                Sentry.captureException(e)
            }
            Log.w(TAG, "Failed to shut down CyfaceDataCapturingService. ", e)
        }

        // Authorization
        mAuthService.dispose()
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

        // The LoginActivity is called by Android which handles the account creation
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
                    val syncEnabledPreference =
                        preferences.getBoolean(PREFERENCES_SYNCHRONIZATION_KEY, true)
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
        val storedServer = preferences.getString(INCENTIVES_ENDPOINT_URL_SETTINGS_KEY, null)
        val server = BuildConfig.incentivesServer
        @Suppress("KotlinConstantConditions")
        Validate.isTrue(server != "null")
        if (storedServer == null || storedServer != server) {
            Log.d(
                TAG,
                "Updating Cyface Incentives API URL from " + storedServer + "to" + server
            )
            val editor = preferences.edit()
            editor.putString(INCENTIVES_ENDPOINT_URL_SETTINGS_KEY, server)
            editor.apply()
        }
    }

    @MainThread
    private fun displayNotAuthorized(explanation: String) {
        showSnackbar("unauthorized, logging out ...")
        Handler().postDelayed({signOut()}, 2000)
    }

    @MainThread
    private fun displayLoading(message: String) {
        showSnackbar("loading ...")
    }

    @MainThread
    private fun displayAuthorized() {
        showSnackbar("authorized!")
        val state = mStateManager.current
        //refreshTokenInfoView.text = if (state.refreshToken == null) "no_refresh_token_returned" else "refresh_token_returned"
        //idTokenInfoView.text = if (state.idToken == null) "no_id_token_returned" else "id_token_returned"
        /*if (state.accessToken == null) {
            accessTokenInfoView.text = "no_access_token_returned"
        } else {
            val expiresAt = state.accessTokenExpirationTime
            if (expiresAt == null) {
                accessTokenInfoView.text = "no_access_token_expiry"
            } else if (expiresAt < System.currentTimeMillis()) {
                accessTokenInfoView.text = "access_token_expired"
            } else {
                val template = "access_token_expires_at"
                accessTokenInfoView.text = java.lang.String.format(
                    template,
                    Date(expiresAt).toString()
                )
            }
        }*/

        /*val refreshTokenButton = findViewById<View>(R.id.refresh_token) as Button
        refreshTokenButton.visibility = if (state.refreshToken != null) View.VISIBLE else View.GONE
        refreshTokenButton.setOnClickListener { refreshAccessToken() }*/

        /*val viewProfileButton = findViewById<View>(R.id.view_profile) as Button
        val discoveryDoc = state.authorizationServiceConfiguration!!.discoveryDoc
        if ((discoveryDoc?.userinfoEndpoint == null) && mConfiguration.userInfoEndpointUri == null) {
            viewProfileButton.visibility = View.GONE
        } else {
            viewProfileButton.visibility = View.VISIBLE
            viewProfileButton.setOnClickListener { fetchUserInfo() }
        }*/

        //findViewById<View>(R.id.sign_out).setOnClickListener { endSession() }

        /*val userInfoCard: View = findViewById(R.id.userinfo_card)
        val userInfo: JSONObject? = mUserInfoJson.get()
        if (userInfo == null) {
            userInfoCard.visibility = View.INVISIBLE
        } else {
            try {
                var name = "???"
                if (userInfo.has("name")) {
                    name = userInfo.getString("name")
                }
                (findViewById<View>(R.id.userinfo_name) as TextView).text = name
                /*if (userInfo.has("picture")) {
                    GlideApp.with(this@TokenActivity)
                        .load(Uri.parse(userInfo.getString("picture")))
                        .fitCenter()
                        .into(findViewById<View>(R.id.userinfo_profile) as ImageView?)
                }*/
                (findViewById<View>(R.id.userinfo_json) as TextView).text = mUserInfoJson.toString()
                userInfoCard.visibility = View.VISIBLE
            } catch (ex: JSONException) {
                Log.e(TAG, "Failed to read userinfo JSON", ex)
            }
        }*/
    }

    @MainThread
    private fun refreshAccessToken() {
        displayLoading("Refreshing access token")
        performTokenRequest(mStateManager.current.createTokenRefreshRequest()) { tokenResponse: TokenResponse?, authException: AuthorizationException? ->
            handleAccessTokenResponse(
                tokenResponse,
                authException
            )
        }
    }

    @MainThread
    private fun exchangeAuthorizationCode(authorizationResponse: AuthorizationResponse) {
        displayLoading("Exchanging authorization code")
        performTokenRequest(
            authorizationResponse.createTokenExchangeRequest()
        ) { tokenResponse: TokenResponse?, authException: AuthorizationException? ->
            handleCodeExchangeResponse(
                tokenResponse,
                authException
            )
        }
    }

    @MainThread
    private fun performTokenRequest(
        request: TokenRequest,
        callback: AuthorizationService.TokenResponseCallback
    ) {
        val clientAuthentication = try {
            mStateManager.current.clientAuthentication
        } catch (ex: ClientAuthentication.UnsupportedAuthenticationMethod) {
            Log.d(
                TAG, "Token request cannot be made, client authentication for the token "
                        + "endpoint could not be constructed (%s)", ex
            )
            displayNotAuthorized("Client authentication method is unsupported")
            return
        }
        mAuthService.performTokenRequest(
            request,
            clientAuthentication,
            callback
        )
    }

    @WorkerThread
    private fun handleAccessTokenResponse(
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?
    ) {
        mStateManager.updateAfterTokenResponse(tokenResponse, authException)
        runOnUiThread { displayAuthorized() }
    }

    @WorkerThread
    private fun handleCodeExchangeResponse(
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?
    ) {
        mStateManager.updateAfterTokenResponse(tokenResponse, authException)
        if (!mStateManager.current.isAuthorized) {
            val message = ("Authorization Code exchange failed"
                    + if (authException != null) authException.error else "")

            // WrongThread inference is incorrect for lambdas
            runOnUiThread { displayNotAuthorized(message) }
        } else {
            runOnUiThread { displayAuthorized() }
        }
    }

    /**
     * Demonstrates the use of [AuthState.performActionWithFreshTokens] to retrieve
     * user info from the IDP's user info endpoint. This callback will negotiate a new access
     * token / id token for use in a follow-up action, or provide an error if this fails.
     */
    @MainThread
    private fun fetchUserInfo() {
        displayLoading("Fetching user info")
        mStateManager.current.performActionWithFreshTokens(
            mAuthService
        ) { accessToken: String?, idToken: String?, ex: AuthorizationException? ->
            this.fetchUserInfo(
                accessToken!!,
                idToken!!,
                ex
            )
        }
    }

    @MainThread
    private fun fetchUserInfo(accessToken: String, idToken: String, ex: AuthorizationException?) {
        if (ex != null) {
            Log.e(TAG, "Token refresh failed when fetching user info")
            //mUserInfoJson.set(null)
            runOnUiThread { displayAuthorized() }
            return
        }
        val discovery = mStateManager.current
            .authorizationServiceConfiguration!!.discoveryDoc
        val userInfoEndpoint = if (mConfiguration.userInfoEndpointUri != null) Uri.parse(
            mConfiguration.userInfoEndpointUri.toString()
        ) else Uri.parse(discovery!!.userinfoEndpoint.toString())
        /*mExecutor.submit {
            try {
                val conn = mConfiguration.connectionBuilder.openConnection(
                    userInfoEndpoint
                )
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.instanceFollowRedirects = false
                val response: String = conn.inputStream.source().buffer()
                    .readString(Charset.forName("UTF-8"))
                mUserInfoJson.set(JSONObject(response))
            } catch (ioEx: IOException) {
                Log.e(TAG, "Network error when querying userinfo endpoint", ioEx)
                showSnackbar("Fetching user info failed")
            } catch (jsonEx: JSONException) {
                Log.e(TAG, "Failed to parse userinfo response")
                showSnackbar("Failed to parse user info")
            }
            runOnUiThread { displayAuthorized() }
        }*/
    }

    private fun displayEndSessionCancelled() {
        showSnackbar("Sign out canceled")
    }

    @MainThread
    private fun endSession() {
        val currentState: AuthState = mStateManager.current
        val config: AuthorizationServiceConfiguration =
            currentState.authorizationServiceConfiguration!!
        if (config.endSessionEndpoint != null) {
            val endSessionIntent: Intent = mAuthService.getEndSessionRequestIntent(
                EndSessionRequest.Builder(config)
                    .setIdTokenHint(currentState.idToken)
                    .setPostLogoutRedirectUri(mConfiguration.endSessionRedirectUri)
                    .build()
            )
            startActivityForResult(endSessionIntent, END_SESSION_REQUEST_CODE)
        } else {
            signOut()
        }
    }

    @MainThread
    private fun signOut() {
        // discard the authorization and token state, but retain the configuration and
        // dynamic client registration (if applicable), to save from retrieving them again.
        val currentState: AuthState = mStateManager.current
        val clearedState = AuthState(currentState.authorizationServiceConfiguration!!)
        if (currentState.lastRegistrationResponse != null) {
            clearedState.update(currentState.lastRegistrationResponse)
        }
        mStateManager.replace(clearedState)
        val mainIntent = Intent(this, LoginActivity::class.java)
        mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(mainIntent)
        finish()
    }

    @MainThread
    private fun showSnackbar(message: String) {
        Snackbar.make(
            findViewById(R.id.container),
            message,
            Snackbar.LENGTH_SHORT
        )
            .show()
    }

    companion object {
        private const val END_SESSION_REQUEST_CODE = 911
    }
}