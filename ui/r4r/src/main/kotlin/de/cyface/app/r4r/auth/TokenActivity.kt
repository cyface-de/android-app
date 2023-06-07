/*
 * Copyright 2015 The AppAuth for Android Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cyface.app.r4r.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import de.cyface.app.r4r.R
import de.cyface.synchronization.AuthStateManager
import de.cyface.synchronization.Configuration
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
import okio.buffer
import okio.source
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.Charset
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Displays the authorized state of the user. This activity is provided with the outcome of the
 * authorization flow, which it uses to negotiate the final authorized state,
 * by performing an authorization code exchange if necessary. After this, the activity provides
 * additional post-authorization operations if available, such as fetching user info and refreshing
 * access tokens.
 *
 * I.e. this activity especially takes care of the third stage:
 * 1. Discovering or specifying the endpoints to interact with the provider.
 * 2. Authorizing the user, via a browser, in order to obtain an authorization code.
 * 3. Exchanging the authorization code with the authorization server, to obtain a refresh token and/or ID token.
 * 4. Using access tokens derived from the refresh token to interact with a resource server for further access to user data.
 */
class TokenActivity : AppCompatActivity() {
    private lateinit var mAuthService: AuthorizationService
    private lateinit var mStateManager: AuthStateManager
    private val mUserInfoJson: AtomicReference<JSONObject?> = AtomicReference<JSONObject?>()
    private lateinit var mExecutor: ExecutorService
    private lateinit var mConfiguration: Configuration
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mStateManager = AuthStateManager.getInstance(this)
        mExecutor = Executors.newSingleThreadExecutor()
        mConfiguration = Configuration.getInstance(this)
        val config = Configuration.getInstance(this)
        if (config.hasConfigurationChanged()) {
            Toast.makeText(
                this,
                "Configuration change detected",
                Toast.LENGTH_SHORT
            )
                .show()
            signOut()
            return
        }
        mAuthService = AuthorizationService(
            this,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(config.connectionBuilder)
                .build()
        )
        setContentView(R.layout.activity_token)
        displayLoading("Restoring state...")
        if (savedInstanceState != null) {
            try {
                mUserInfoJson.set(JSONObject(savedInstanceState.getString(KEY_USER_INFO)!!))
            } catch (ex: JSONException) {
                Log.e(TAG, "Failed to parse saved user info JSON, discarding", ex)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (mExecutor.isShutdown) {
            mExecutor = Executors.newSingleThreadExecutor()
        }
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

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        // user info is retained to survive activity restarts, such as when rotating the
        // device or switching apps. This isn't essential, but it helps provide a less
        // jarring UX when these events occur - data does not just disappear from the view.
        if (mUserInfoJson.get() != null) {
            state.putString(KEY_USER_INFO, mUserInfoJson.toString())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mAuthService.dispose()
        mExecutor.shutdownNow()
    }

    @MainThread
    private fun displayNotAuthorized(explanation: String) {
        findViewById<View>(R.id.not_authorized).visibility = View.VISIBLE
        findViewById<View>(R.id.authorized).visibility = View.GONE
        findViewById<View>(R.id.loading_container).visibility = View.GONE
        (findViewById<View>(R.id.explanation) as TextView).text = explanation
        findViewById<View>(R.id.reauth).setOnClickListener { signOut() }
    }

    @MainThread
    private fun displayLoading(message: String) {
        findViewById<View>(R.id.loading_container).visibility = View.VISIBLE
        findViewById<View>(R.id.authorized).visibility = View.GONE
        findViewById<View>(R.id.not_authorized).visibility = View.GONE
        (findViewById<View>(R.id.loading_description) as TextView).text = message
    }

    @MainThread
    private fun displayAuthorized() {
        findViewById<View>(R.id.authorized).visibility = View.VISIBLE
        findViewById<View>(R.id.not_authorized).visibility = View.GONE
        findViewById<View>(R.id.loading_container).visibility = View.GONE
        val state = mStateManager.current
        val refreshTokenInfoView: TextView = findViewById(R.id.refresh_token_info)
        refreshTokenInfoView.text = if (state.refreshToken == null) "no_refresh_token_returned" else "refresh_token_returned"
        val idTokenInfoView: TextView = findViewById<View>(R.id.id_token_info) as TextView
        idTokenInfoView.text = if (state.idToken == null) "no_id_token_returned" else "id_token_returned"
        val accessTokenInfoView: TextView = findViewById<View>(R.id.access_token_info) as TextView
        if (state.accessToken == null) {
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
        }
        val refreshTokenButton = findViewById<View>(R.id.refresh_token) as Button
        refreshTokenButton.visibility = if (state.refreshToken != null) View.VISIBLE else View.GONE
        refreshTokenButton.setOnClickListener { refreshAccessToken() }
        val viewProfileButton = findViewById<View>(R.id.view_profile) as Button
        val discoveryDoc = state.authorizationServiceConfiguration!!.discoveryDoc
        if ((discoveryDoc?.userinfoEndpoint == null) && mConfiguration.userInfoEndpointUri == null) {
            viewProfileButton.visibility = View.GONE
        } else {
            viewProfileButton.visibility = View.VISIBLE
            viewProfileButton.setOnClickListener { fetchUserInfo() }
        }
        findViewById<View>(R.id.sign_out).setOnClickListener { endSession() }
        val userInfoCard: View = findViewById(R.id.userinfo_card)
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
        }
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
            mUserInfoJson.set(null)
            runOnUiThread { displayAuthorized() }
            return
        }
        val discovery = mStateManager.current
            .authorizationServiceConfiguration!!.discoveryDoc
        val userInfoEndpoint = if (mConfiguration.userInfoEndpointUri != null) Uri.parse(
            mConfiguration.userInfoEndpointUri.toString()
        ) else Uri.parse(discovery!!.userinfoEndpoint.toString())
        mExecutor.submit {
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
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == END_SESSION_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            signOut()
            finish()
        } else {
            displayEndSessionCancelled()
        }
    }

    private fun displayEndSessionCancelled() {
        Snackbar.make(
            findViewById(R.id.coordinator),
            "Sign out canceled",
            Snackbar.LENGTH_SHORT
        )
            .show()
    }

    @MainThread
    private fun showSnackbar(message: String) {
        Snackbar.make(
            findViewById(R.id.coordinator),
            message,
            Snackbar.LENGTH_SHORT
        )
            .show()
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

    companion object {
        private const val TAG = "TokenActivity"
        private const val KEY_USER_INFO = "userInfo"
        private const val END_SESSION_REQUEST_CODE = 911
    }
}