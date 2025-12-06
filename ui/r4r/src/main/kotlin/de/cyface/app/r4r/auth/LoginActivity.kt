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
package de.cyface.app.r4r.auth

import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.VISIBLE
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.AnyThread
import androidx.annotation.ColorRes
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.cyface.app.r4r.MainActivity
import de.cyface.app.r4r.R
import de.cyface.synchronization.AuthStateManager
import de.cyface.synchronization.Configuration
import de.cyface.synchronization.CyfaceAuthenticator
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientSecretBasic
import net.openid.appauth.RegistrationRequest
import net.openid.appauth.RegistrationResponse
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.browser.AnyBrowserMatcher
import net.openid.appauth.browser.BrowserMatcher
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * A login screen that offers login via email/password.
 *
 * *ATTENTION*:
 * The browser page opened by this activity stops working on the emulator after some time.
 * Use a real device to test the auth workflow for now.
 *
 * @author Armin Schnabel
 * @version 4.0.1
 * @since 1.0.0
 */
class LoginActivity : AppCompatActivity() {
    private val TAG = "de.cyface.app.r4r.login"
    private val EXTRA_FAILED = "failed"
    private val RC_AUTH = 100

    private var mAuthService: AuthorizationService? = null
    private lateinit var mAuthStateManager: AuthStateManager
    private lateinit var mConfiguration: Configuration

    private val mClientId = AtomicReference<String>()
    private val mAuthRequest = AtomicReference<AuthorizationRequest?>()
    private val mAuthIntent = AtomicReference<CustomTabsIntent?>()
    private var mAuthIntentLatch = CountDownLatch(1)
    private lateinit var mExecutor: ExecutorService

    private var mUsePendingIntents = false

    private var mBrowserMatcher: BrowserMatcher = AnyBrowserMatcher.INSTANCE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mExecutor = Executors.newSingleThreadExecutor()
        mAuthStateManager = AuthStateManager.getInstance(this)
        mConfiguration = Configuration.getInstance(this, CyfaceAuthenticator.settings)

        // Already authorized
        if (mAuthStateManager.current.isAuthorized
            && !mConfiguration.hasConfigurationChanged()
        ) {
            // As the LoginActivity should only be shown when the user is not authenticated
            // we can't forward to `MainActivity` just like that as this would lead to a loop.
            //Log.i(TAG, "User is already authenticated, processing to token activity")
            Log.e(TAG, "User is already authenticated")
            show(getString(de.cyface.app.utils.R.string.user_is_already_authenticated))
            finish()
            return
        }

        setContentView(R.layout.activity_login)
        // Fix for edge-to-edge introduced in targetSdkVersion 35
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.coordinator)) { view, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemInsets.top, 0, systemInsets.bottom)
            insets
        }
        // Set status bar appearance to light mode (dark icons/text) for visibility
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, findViewById(R.id.coordinator)).isAppearanceLightStatusBars = true

        findViewById<View>(R.id.retry).setOnClickListener {
            mExecutor.submit { initializeAppAuth() }
        }
        findViewById<View>(R.id.start_auth).setOnClickListener { startAuth() }
        if (!mConfiguration.isValid) {
            displayError(mConfiguration.configurationError!!, false)
            return
        }
        if (mConfiguration.hasConfigurationChanged()) {
            // discard any existing authorization state due to the change of configuration
            Log.i(TAG, "Configuration change detected, discarding old state")
            mAuthStateManager.replace(AuthState())
            mConfiguration.acceptConfiguration()
        }
        if (intent.getBooleanExtra(EXTRA_FAILED, false)) {
            show(getString(de.cyface.app.utils.R.string.authorization_canceled))
        }
        displayLoading(getString(de.cyface.app.utils.R.string.initializing))
        mExecutor.submit { initializeAppAuth() }
    }

    override fun onStart() {
        super.onStart()
        if (mExecutor.isShutdown) {
            mExecutor = Executors.newSingleThreadExecutor()
        }
    }

    override fun onStop() {
        super.onStop()
        mExecutor.shutdownNow()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mAuthService != null) {
            mAuthService!!.dispose()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        displayAuthOptions()
        if (resultCode == RESULT_CANCELED) {
            show(getString(de.cyface.app.utils.R.string.authorization_canceled))
        } else {
            show(getString(de.cyface.app.utils.R.string.logging_in))
            //Toast.makeText(applicationContext, "Logging in ...", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtras(data!!.extras!!)
            startActivity(intent)
            finish() // added because of our workflow where MainActivity calls LoginActivity
        }
    }

    @MainThread
    fun startAuth() {
        displayLoading(getString(de.cyface.app.utils.R.string.making_authorization_request))

        // WrongThread inference is incorrect for lambdas
        // noinspection WrongThread
        mExecutor.submit { doAuth() }
    }

    /**
     * Initializes the authorization service configuration if necessary, either from the local
     * static values or by retrieving an OpenID discovery document.
     */
    @WorkerThread
    private fun initializeAppAuth() {
        Log.i(TAG, "Initializing AppAuth")
        recreateAuthorizationService()
        if (mAuthStateManager.current.authorizationServiceConfiguration != null) {
            // configuration is already created, skip to client initialization
            Log.i(TAG, "auth config already established")
            initializeClient()
            return
        }

        // if we are not using discovery, build the authorization service configuration directly
        // from the static configuration values.
        if (mConfiguration.discoveryUri == null) {
            Log.i(TAG, "Creating auth config")
            val config = AuthorizationServiceConfiguration(
                mConfiguration.authEndpointUri!!,
                mConfiguration.tokenEndpointUri!!,
                mConfiguration.registrationEndpointUri,
                mConfiguration.endSessionEndpoint
            )
            mAuthStateManager.replace(AuthState(config))
            initializeClient()
            return
        }

        // WrongThread inference is incorrect for lambdas
        // noinspection WrongThread
        runOnUiThread { displayLoading(getString(de.cyface.app.utils.R.string.retrieving_discovery_document)) }
        Log.i(TAG, "Retrieving OpenID discovery doc")
        AuthorizationServiceConfiguration.fetchFromUrl(
            mConfiguration.discoveryUri!!,
            { config: AuthorizationServiceConfiguration?, ex: AuthorizationException? ->
                handleConfigurationRetrievalResult(
                    config,
                    ex
                )
            },
            mConfiguration.connectionBuilder
        )
    }

    @MainThread
    private fun handleConfigurationRetrievalResult(
        config: AuthorizationServiceConfiguration?,
        ex: AuthorizationException?
    ) {
        if (config == null) {
            Log.i(TAG, "Failed to retrieve discovery document", ex)
            val message = if (ex!!.message == "Network error") "Offline?" else ex.message
            displayError(
                getString(
                    de.cyface.app.utils.R.string.failed_to_retrieve_discovery,
                    message
                ), true
            )
            return
        }
        Log.i(TAG, "Discovery document retrieved")
        mAuthStateManager.replace(AuthState(config))
        mExecutor.submit { initializeClient() }
    }

    /**
     * Initiates a dynamic registration request if a client ID is not provided by the static
     * configuration.
     */
    @WorkerThread
    private fun initializeClient() {
        if (mConfiguration.clientId != null) {
            Log.i(TAG, "Using static client ID: " + mConfiguration.clientId)
            // use a statically configured client ID
            mClientId.set(mConfiguration.clientId)
            runOnUiThread { initializeAuthRequest() }
            return
        }
        val lastResponse = mAuthStateManager.current.lastRegistrationResponse
        if (lastResponse != null) {
            Log.i(TAG, "Using dynamic client ID: " + lastResponse.clientId)
            // already dynamically registered a client ID
            mClientId.set(lastResponse.clientId)
            runOnUiThread { initializeAuthRequest() }
            return
        }

        // WrongThread inference is incorrect for lambdas
        // noinspection WrongThread
        runOnUiThread { displayLoading(getString(de.cyface.app.utils.R.string.dynamically_registering_client)) }
        Log.i(TAG, "Dynamically registering client")
        val registrationRequest = RegistrationRequest.Builder(
            mAuthStateManager.current.authorizationServiceConfiguration!!,
            listOf(mConfiguration.redirectUri)
        )
            .setTokenEndpointAuthenticationMethod(ClientSecretBasic.NAME)
            .build()
        mAuthService!!.performRegistrationRequest(
            registrationRequest
        ) { response: RegistrationResponse?, ex: AuthorizationException? ->
            handleRegistrationResponse(
                response,
                ex
            )
        }
    }

    @MainThread
    private fun handleRegistrationResponse(
        response: RegistrationResponse?,
        ex: AuthorizationException?
    ) {
        mAuthStateManager.updateAfterRegistration(response, ex)
        if (response == null) {
            Log.i(TAG, "Failed to dynamically register client", ex)
            displayErrorLater(
                getString(
                    de.cyface.app.utils.R.string.failed_to_register_client,
                    ex!!.message
                ), true
            )
            return
        }
        Log.i(TAG, "Dynamically registered client: " + response.clientId)
        mClientId.set(response.clientId)
        initializeAuthRequest()
    }

    /**
     * Performs the authorization request, using the browser selected in the spinner,
     * and a user-provided `login_hint` if available.
     */
    @WorkerThread
    private fun doAuth() {
        try {
            mAuthIntentLatch.await()
        } catch (ex: InterruptedException) {
            Log.w(TAG, "Interrupted while waiting for auth intent")
        }
        if (mUsePendingIntents) { // We currently always use the other option below
            val completionIntent = Intent(this, MainActivity::class.java)
            val cancelIntent = Intent(this, LoginActivity::class.java)
            cancelIntent.putExtra(EXTRA_FAILED, true)
            cancelIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            var flags = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_MUTABLE
            }
            mAuthService!!.performAuthorizationRequest(
                mAuthRequest.get()!!,
                PendingIntent.getActivity(this, 0, completionIntent, flags),
                PendingIntent.getActivity(this, 0, cancelIntent, flags),
                mAuthIntent.get()!!
            )
        } else {
            val intent = mAuthService!!.getAuthorizationRequestIntent(
                mAuthRequest.get()!!,
                mAuthIntent.get()!!
            )
            startActivityForResult(intent, RC_AUTH)
        }
    }

    private fun recreateAuthorizationService() {
        if (mAuthService != null) {
            Log.i(TAG, "Discarding existing AuthService instance")
            mAuthService!!.dispose()
        }
        mAuthService = createAuthorizationService()
        mAuthRequest.set(null)
        mAuthIntent.set(null)
    }

    private fun createAuthorizationService(): AuthorizationService {
        Log.i(TAG, "Creating authorization service")
        val builder = AppAuthConfiguration.Builder()
        builder.setBrowserMatcher(mBrowserMatcher)
        builder.setConnectionBuilder(mConfiguration.connectionBuilder)
        return AuthorizationService(this, builder.build())
    }

    @MainThread
    private fun displayLoading(loadingMessage: String) {
        findViewById<View>(R.id.loading_container).visibility = VISIBLE
        findViewById<View>(R.id.auth_container).visibility = View.GONE
        findViewById<View>(R.id.error_container).visibility = View.GONE
        (findViewById<View>(R.id.loading_description) as TextView).text =
            loadingMessage
    }

    @MainThread
    private fun displayError(error: String, recoverable: Boolean) {
        findViewById<View>(R.id.error_container).visibility = VISIBLE
        findViewById<View>(R.id.loading_container).visibility = View.GONE
        findViewById<View>(R.id.auth_container).visibility = View.GONE
        (findViewById<View>(R.id.error_description) as TextView).text = error
        findViewById<View>(R.id.retry).visibility = if (recoverable) VISIBLE else View.GONE
    }

    // WrongThread inference is incorrect in this case
    @AnyThread
    private fun displayErrorLater(
        error: String,
        @Suppress("SameParameterValue") recoverable: Boolean
    ) {
        runOnUiThread { displayError(error, recoverable) }
    }

    @MainThread
    private fun initializeAuthRequest() {
        createAuthRequest(this@LoginActivity)
        warmUpBrowser(this@LoginActivity)
        displayAuthOptions()
    }

    @MainThread
    private fun displayAuthOptions() {
        findViewById<View>(R.id.auth_container).visibility = VISIBLE
        findViewById<View>(R.id.loading_container).visibility = View.GONE
        findViewById<View>(R.id.error_container).visibility = View.GONE
    }

    @Suppress("SameParameterValue")
    @MainThread
    private fun show(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun getColorCompat(@ColorRes color: Int): Int {
        return getColor(color)
    }

    companion object {
        private fun warmUpBrowser(loginActivity: LoginActivity) {
            loginActivity.mAuthIntentLatch = CountDownLatch(1)
            loginActivity.mExecutor.execute {
                Log.i(loginActivity.TAG, "Warming up browser instance for auth request")
                val intentBuilder = loginActivity.mAuthService!!.createCustomTabsIntentBuilder(
                    loginActivity.mAuthRequest.get()!!.toUri()
                )
                intentBuilder.setToolbarColor(loginActivity.getColorCompat(de.cyface.app.utils.R.color.defaultPrimary))
                loginActivity.mAuthIntent.set(intentBuilder.build())
                loginActivity.mAuthIntentLatch.countDown()
            }
        }

        private fun createAuthRequest(loginActivity: LoginActivity) {
            Log.i(loginActivity.TAG, "Creating auth request")
            val authRequestBuilder = AuthorizationRequest.Builder(
                loginActivity.mAuthStateManager.current.authorizationServiceConfiguration!!,
                loginActivity.mClientId.get(),
                ResponseTypeValues.CODE,
                loginActivity.mConfiguration.redirectUri
            )
                .setScope(loginActivity.mConfiguration.scope)
            loginActivity.mAuthRequest.set(authRequestBuilder.build())
        }
    }
}
