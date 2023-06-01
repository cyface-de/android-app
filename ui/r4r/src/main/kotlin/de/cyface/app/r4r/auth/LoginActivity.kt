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
package de.cyface.app.r4r.auth

import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.View.VISIBLE
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.AnyThread
import androidx.annotation.ColorRes
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.google.android.material.snackbar.Snackbar
import de.cyface.app.r4r.R
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
import net.openid.appauth.browser.ExactBrowserMatcher
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A login screen that offers login via email/password.
 *
 * @author Armin Schnabel
 * @version 4.0.0
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
        mConfiguration = Configuration.getInstance(this)
        if (mAuthStateManager.current.isAuthorized
            && !mConfiguration.hasConfigurationChanged()
        ) {
            Log.i(TAG, "User is already authenticated, proceeding to token activity")
            startActivity(Intent(this, TokenActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_login)
        findViewById<View>(R.id.retry).setOnClickListener {
            mExecutor.submit { initializeAppAuth() }
        }
        findViewById<View>(R.id.start_auth).setOnClickListener { startAuth() }
        (findViewById<View>(R.id.login_hint_value) as EditText).addTextChangedListener(
            LoginHintChangeHandler(this@LoginActivity)
        )
        if (!mConfiguration.isValid) {
            displayError(mConfiguration.configurationError!!, false)
            return
        }
        configureBrowserSelector()
        if (mConfiguration.hasConfigurationChanged()) {
            // discard any existing authorization state due to the change of configuration
            Log.i(TAG, "Configuration change detected, discarding old state")
            mAuthStateManager.replace(AuthState())
            mConfiguration.acceptConfiguration()
        }
        if (intent.getBooleanExtra(EXTRA_FAILED, false)) {
            displayAuthCancelled()
        }
        displayLoading("Initializing")
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
            displayAuthCancelled()
        } else {
            val intent = Intent(this, TokenActivity::class.java)
            intent.putExtras(data!!.extras!!)
            startActivity(intent)
        }
    }

    @MainThread
    fun startAuth() {
        displayLoading("Making authorization request")
        mUsePendingIntents =
            (findViewById<View>(R.id.pending_intents_checkbox) as CheckBox).isChecked

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
            Log.i(TAG, "Creating auth config from res/raw/auth_config.json")
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
        runOnUiThread { displayLoading("Retrieving discovery document") }
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
            displayError("Failed to retrieve discovery document: " + ex!!.message, true)
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
        runOnUiThread { displayLoading("Dynamically registering client") }
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
            displayErrorLater("Failed to register client: " + ex!!.message, true)
            return
        }
        Log.i(TAG, "Dynamically registered client: " + response.clientId)
        mClientId.set(response.clientId)
        initializeAuthRequest()
    }

    /**
     * Enumerates the browsers installed on the device and populates a spinner, allowing the
     * demo user to easily test the authorization flow against different browser and custom
     * tab configurations.
     */
    @MainThread
    private fun configureBrowserSelector() {
        val spinner = findViewById<View>(R.id.browser_selector) as Spinner
        val adapter = BrowserSelectionAdapter(this)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val info = adapter.getItem(position)
                if (info == null) {
                    mBrowserMatcher = AnyBrowserMatcher.INSTANCE
                    return
                } else {
                    mBrowserMatcher = ExactBrowserMatcher(info.mDescriptor)
                }
                recreateAuthorizationService()
                createAuthRequest(this@LoginActivity, getLoginHint(this@LoginActivity))
                warmUpBrowser(this@LoginActivity)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                mBrowserMatcher = AnyBrowserMatcher.INSTANCE
            }
        }
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
        if (mUsePendingIntents) {
            val completionIntent = Intent(this, TokenActivity::class.java)
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
        createAuthRequest(this@LoginActivity, getLoginHint(this@LoginActivity))
        warmUpBrowser(this@LoginActivity)
        displayAuthOptions()
    }

    @MainThread
    private fun displayAuthOptions() {
        findViewById<View>(R.id.auth_container).visibility = VISIBLE
        findViewById<View>(R.id.loading_container).visibility = View.GONE
        findViewById<View>(R.id.error_container).visibility = View.GONE
        val state: AuthState = mAuthStateManager.current
        val config = state.authorizationServiceConfiguration
        var authEndpointStr = if (config!!.discoveryDoc != null) {
            "Discovered auth endpoint: \n"
        } else {
            "Static auth endpoint: \n"
        }
        authEndpointStr += config.authorizationEndpoint
        (findViewById<View>(R.id.auth_endpoint) as TextView).text = authEndpointStr
        var clientIdStr = if (state.lastRegistrationResponse != null) {
            "Dynamic client ID: \n"
        } else {
            "Static client ID: \n"
        }
        clientIdStr += mClientId
        (findViewById<View>(R.id.client_id) as TextView).text = clientIdStr
    }

    private fun displayAuthCancelled() {
        Snackbar.make(
            findViewById(R.id.coordinator),
            "Authorization canceled",
            Snackbar.LENGTH_SHORT
        )
            .show()
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun getColorCompat(@ColorRes color: Int): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getColor(color)
        } else {
            resources.getColor(color)
        }
    }

    /**
     * Responds to changes in the login hint. After a "debounce" delay, warms up the browser
     * for a request with the new login hint; this avoids constantly re-initializing the
     * browser while the user is typing.
     */
    private class LoginHintChangeHandler(val loginActivity: LoginActivity) : TextWatcher {
        private val mHandler = Handler(Looper.getMainLooper())
        private var mTask: RecreateAuthRequestTask

        init {
            mTask = RecreateAuthRequestTask(loginActivity)
        }

        override fun beforeTextChanged(cs: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(cs: CharSequence, start: Int, before: Int, count: Int) {
            mTask.cancel()
            mTask = RecreateAuthRequestTask(loginActivity)
            mHandler.postDelayed(mTask, DEBOUNCE_DELAY_MS.toLong())
        }

        override fun afterTextChanged(ed: Editable) {}

        companion object {
            private const val DEBOUNCE_DELAY_MS = 500
        }
    }

    private class RecreateAuthRequestTask(val loginActivity: LoginActivity) : Runnable {
        private val mCanceled = AtomicBoolean()
        override fun run() {
            if (mCanceled.get()) {
                return
            }
            createAuthRequest(loginActivity, getLoginHint(loginActivity))
            warmUpBrowser(loginActivity)
        }

        fun cancel() {
            mCanceled.set(true)
        }
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

        private fun createAuthRequest(loginActivity: LoginActivity, loginHint: String?) {
            Log.i(loginActivity.TAG, "Creating auth request for login hint: $loginHint")
            val authRequestBuilder = AuthorizationRequest.Builder(
                loginActivity.mAuthStateManager.current.authorizationServiceConfiguration!!,
                loginActivity.mClientId.get(),
                ResponseTypeValues.CODE,
                loginActivity.mConfiguration.redirectUri
            )
                .setScope(loginActivity.mConfiguration.scope)
            if (!TextUtils.isEmpty(loginHint)) {
                authRequestBuilder.setLoginHint(loginHint)
            }
            loginActivity.mAuthRequest.set(authRequestBuilder.build())
        }

        private fun getLoginHint(loginActivity: LoginActivity): String {
            return (loginActivity.findViewById<View>(R.id.login_hint_value) as EditText)
                .text
                .toString()
                .trim { it <= ' ' }
        }
    }
}