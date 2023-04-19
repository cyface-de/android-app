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
package de.cyface.app.r4r.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.util.Patterns
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.ProgressBar
import androidx.fragment.app.FragmentActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.hcaptcha.sdk.HCaptcha
import com.hcaptcha.sdk.HCaptchaConfig
import com.hcaptcha.sdk.HCaptchaException
import com.hcaptcha.sdk.HCaptchaSize
import com.hcaptcha.sdk.HCaptchaTheme
import com.hcaptcha.sdk.HCaptchaTokenResponse
import de.cyface.app.r4r.Application.Companion.errorHandler
import de.cyface.app.r4r.BuildConfig
import de.cyface.app.r4r.R
import de.cyface.app.r4r.utils.Constants.TAG
import de.cyface.synchronization.ErrorHandler
import de.cyface.synchronization.ErrorHandler.ErrorCode
import de.cyface.synchronization.SyncService
import de.cyface.utils.Validate
import java.lang.ref.WeakReference
import java.util.regex.Pattern


/**
 * A registration screen that offers registration via email/password and captcha.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.3.0
 */
class RegistrationActivity : FragmentActivity() /* HCaptcha requires FragmentActivity */ {
    private lateinit var context: WeakReference<Context>
    private var preferences: SharedPreferences? = null

    // UI references
    private var progressBar: ProgressBar? = null

    /**
     * A `Button` which is used to confirm the entered credentials.
     */
    private var registrationButton: Button? = null

    /**
     * Needs to be resettable for testing. That's the only way to mock a single method of Android's Activity's
     */
    private var emailInput: TextInputEditText? = null
    private var passwordInput: TextInputEditText? = null
    private var passwordConfirmationInput: TextInputEditText? = null
    private var messageView: MaterialTextView? = null

    /**
     * Intent for switching back to the login activity when the user clicks the link.
     */
    private var callLoginActivityIntent: Intent? = null

    /**
     * Needs to be resettable for testing. That's the only way to mock a single method of Android's Activity's
     */
    private var eMailPattern: Pattern = Patterns.EMAIL_ADDRESS

    private lateinit var hCaptcha: HCaptcha
    private var tokenResponse: HCaptchaTokenResponse? = null

    // FIXME: test typical error from registration (see web-app)
    private val errorListener = ErrorHandler.ErrorListener { errorCode, errorMessage ->
        if (errorCode == ErrorCode.UNAUTHORIZED) {
            passwordInput!!.error = errorMessage
            passwordInput!!.requestFocus()

            // All other errors are shown as toast by the MeasuringClient
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)
        context = WeakReference(this)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        setServerUrl() // TODO [CY-3735]: via Android's settings

        // Set up the login form
        emailInput = findViewById(R.id.input_email)
        passwordInput = findViewById(R.id.input_password)
        passwordConfirmationInput = findViewById(R.id.input_password_confirmation)
        messageView = findViewById(R.id.registration_message)
        registrationButton = findViewById(R.id.registration_button)
        registrationButton!!.setOnClickListener { attemptRegistration() }
        progressBar = findViewById(R.id.registration_progress_bar)
        callLoginActivityIntent = Intent(this, LoginActivity::class.java)
        registerLoginLink()
        errorHandler!!.addListener(errorListener)

        // HCaptcha
        hCaptcha = HCaptcha.getClient(context.get()!!).setup(hCaptchaConfig())
        setupHCaptcha(hCaptcha)
    }

    override fun onDestroy() {
        errorHandler!!.removeListener(errorListener)
        super.onDestroy()
    }

    private fun hCaptchaConfig(): HCaptchaConfig {
        return HCaptchaConfig.builder()
            .siteKey(BuildConfig.hCaptchaKey)
            .size(HCaptchaSize.NORMAL)
            .theme(HCaptchaTheme.LIGHT)
            .build()
    }

    private fun setupHCaptcha(hCaptcha: HCaptcha) {
        hCaptcha
            .addOnSuccessListener { response: HCaptchaTokenResponse ->
                tokenResponse = response
                //val userResponseToken = response.tokenResult // FIXME
                returnToLogin(true)
            }
            .addOnFailureListener { e: HCaptchaException ->
                Log.d(TAG, "hCaptcha failed: " + e.message + "(" + e.statusCode + ")")
                messageView!!.text =
                    "Captcha failed, please try again or contact support @??" // FIXME
                messageView!!.visibility = VISIBLE
                tokenResponse = null
            }
            .addOnOpenListener {
                messageView!!.text = ""
                messageView!!.visibility = GONE
            }
    }

    /**
     * Attempts to sign up with the data specified by the registration form.
     *
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual registration attempt is made.
     */
    private fun attemptRegistration() {
        /*if (loginTask != null) { FIXME
            Log.d(TAG, "Auth is already in progress, ignoring attemptLogin().")
            return  // Auth is already in progress
        }*/

        // Update view
        emailInput!!.error = null
        passwordInput!!.error = null
        passwordConfirmationInput!!.error = null
        registrationButton!!.isEnabled = false

        // Check for valid credentials
        Validate.notNull(emailInput!!.text)
        Validate.notNull(passwordInput!!.text)
        Validate.notNull(passwordConfirmationInput!!.text)
        val login = emailInput!!.text.toString()
        val password = passwordInput!!.text.toString()
        val passwordConfirmation = passwordConfirmationInput!!.text.toString()
        if (!credentialsAreValid(login, password, passwordConfirmation)) {
            registrationButton!!.isEnabled = true
            return
        }

        // Update view
        progressBar!!.isIndeterminate = true
        progressBar!!.visibility = View.VISIBLE

        // FIXME: on failure: see below - this is just a placeholder
        progressBar!!.visibility = View.GONE
        registrationButton!!.isEnabled = true
        hCaptcha.verifyWithHCaptcha(/*hCaptchaConfig()*//* args */)
        //returnToLogin(true)

        // FIXME: send registration task below
        // when successful, forward to Login page and show "registration successful, check emails to activate account before you can log in"

        // Send async login attempt
        // TODO [CY-3737]: warning will be resolved when moving the task to WifiSurveyor
        /*loginTask = object : AuthTokenRequest(context) {
            override fun onPostExecute(params: AuthTokenRequestParams?) {
                loginTask = null
                progressBar!!.visibility = View.GONE
                if (!params!!.isSuccessful) {
                    Log.d(TAG, "Login failed - removing account to allow new login.")
                    // Clean up if the getAuthToken failed, else the LoginActivity is probably not shown
                    deleteAccount(context.get()!!, params.getAccount())
                    loginButton!!.isEnabled = true
                    return
                }

                // Equals tutorial's "finishLogin()"
                val intent = Intent()
                intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, params.getAccount().name)
                intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, ACCOUNT_TYPE)
                intent.putExtra(AccountManager.KEY_AUTHTOKEN, Constants.AUTH_TOKEN_TYPE)

                // Return the information back to the Authenticator
                setAccountAuthenticatorResult(intent.extras)
                setResult(RESULT_OK, intent)

                // Hide the keyboard or else it can overlap the permission request
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    loginButton!!.windowInsetsController!!.hide(WindowInsetsCompat.Type.ime())
                } else {
                    ViewCompat.getWindowInsetsController(loginButton!!)!!
                        .hide(WindowInsetsCompat.Type.ime())
                }
                finish()
            }

            override fun onCancelled() {
                Log.d(TAG, "LoginTask canceled.")
                loginTask = null
                progressBar!!.visibility = View.GONE
            }
        }
        loginTask!!.execute()*/
    }

    /**
     * Checks if the format of the credentials provided is valid, i.e. has the allowed length and
     * is not empty and, if requested, checks if the login is an email address.
     *
     * @param email The login string
     * @param password The password string
     * @param passwordConfirmation The password confirmation string
     * @return true is the credentials are in a valid format
     */
    private fun credentialsAreValid(
        email: String?,
        password: String?,
        passwordConfirmation: String?
    ): Boolean {
        var valid = true
        if (email.isNullOrEmpty()) {
            emailInput!!.error =
                getString(de.cyface.app.utils.R.string.error_message_field_required)
            emailInput!!.requestFocus()
            valid = false
        } else if (!eMailPattern.matcher(email).matches()) {
            emailInput!!.error = getString(de.cyface.app.utils.R.string.error_message_invalid_email)
            emailInput!!.requestFocus()
            valid = false
        }
        if (password.isNullOrEmpty()) {
            passwordInput!!.error =
                getString(de.cyface.app.utils.R.string.error_message_field_required)
            passwordInput!!.requestFocus()
            valid = false
        } else if (password.length < 4) {
            passwordInput!!.error =
                getString(de.cyface.app.utils.R.string.error_message_password_too_short)
            passwordInput!!.requestFocus()
            valid = false
        } else if (password.length > 20) {
            passwordInput!!.error =
                getString(de.cyface.app.utils.R.string.error_message_password_too_short)
            passwordInput!!.requestFocus()
            valid = false
        }
        if (passwordConfirmation.isNullOrEmpty()) {
            passwordInput!!.error =
                getString(de.cyface.app.utils.R.string.error_message_field_required)
            passwordInput!!.requestFocus()
            valid = false
        }
        if (!passwordConfirmation.equals(password)) {
            passwordInput!!.error =
                getString(de.cyface.app.utils.R.string.error_message_passwords_do_not_match)
            passwordInput!!.requestFocus()
            valid = false
        }
        return valid
    }

    /**
     * As long as the server URL is hardcoded we want to reset it when it's different from the
     * default URL set in the [BuildConfig]. If not, hardcoded updates would not have an
     * effect.
     */
    private fun setServerUrl() {
        val storedServer = preferences!!.getString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, null)
        Validate.notNull(BuildConfig.cyfaceServer)
        if (storedServer == null || storedServer != BuildConfig.cyfaceServer) {
            Log.d(
                TAG,
                "Updating Cyface Server API URL from " + storedServer + "to" + BuildConfig.cyfaceServer
            )
            val editor = preferences!!.edit()
            editor.putString(SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY, BuildConfig.cyfaceServer)
            editor.apply()
        }
    }

    private fun registerLoginLink() {
        val loginLink = findViewById<View>(R.id.registration_link_login)
        loginLink.setOnClickListener { v: View? -> returnToLogin(false) }
    }

    private fun returnToLogin(registrationSuccessful: Boolean) {
        callLoginActivityIntent!!.putExtra(
            "registered",
            registrationSuccessful
        ) // FIXME hard-coded key
        startActivity(callLoginActivityIntent)
        finish()
    }
}