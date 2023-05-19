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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import androidx.fragment.app.FragmentActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.hcaptcha.sdk.HCaptcha
import com.hcaptcha.sdk.HCaptchaConfig
import com.hcaptcha.sdk.HCaptchaException
import com.hcaptcha.sdk.HCaptchaSize
import com.hcaptcha.sdk.HCaptchaTheme
import com.hcaptcha.sdk.HCaptchaTokenResponse
import de.cyface.app.r4r.utils.Constants.TAG
import de.cyface.app.utils.SharedConstants.ACCEPTED_REPORTING_KEY
import de.cyface.model.Activation
import de.cyface.synchronization.SyncService.AUTH_ENDPOINT_URL_SETTINGS_KEY
import de.cyface.uploader.DefaultAuthenticator
import de.cyface.uploader.Result
import de.cyface.uploader.exception.ConflictException
import de.cyface.uploader.exception.RegistrationFailed
import de.cyface.utils.Validate
import io.sentry.Sentry
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.regex.Pattern

/**
 * A registration screen that offers registration via email/password and captcha.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.3.0
 */
class RegistrationActivity : FragmentActivity() /* HCaptcha requires FragmentActivity */,
    AdapterView.OnItemSelectedListener {

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
    private lateinit var groupSpinner: Spinner

    /**
     * The group selected by the user during registration.
     */
    private var group: Group? = null
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)
        context = WeakReference(this)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        setServerUrl() // TODO [CY-3735]: via Android's settings

        // Set up the form
        emailInput = findViewById(R.id.input_email)
        passwordInput = findViewById(R.id.input_password)
        passwordConfirmationInput = findViewById(R.id.input_password_confirmation)
        groupSpinner = findViewById(R.id.group_spinner)
        ArrayAdapter.createFromResource(
            this,
            R.array.groups,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            groupSpinner.adapter = adapter
        }
        groupSpinner.onItemSelectedListener = this
        messageView = findViewById(R.id.registration_message)
        registrationButton = findViewById(R.id.registration_button)
        registrationButton!!.setOnClickListener { attemptRegistration() }
        progressBar = findViewById(R.id.registration_progress_bar)
        callLoginActivityIntent = Intent(this, LoginActivity::class.java)
        registerLoginLink()

        // HCaptcha
        hCaptcha = HCaptcha.getClient(context.get()!!).setup(hCaptchaConfig())
        setupHCaptcha(hCaptcha)
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val selected = parent!!.getItemAtPosition(position).toString()
        val group = Group.fromSpinnerText(selected)
        Validate.notNull(group, "Unknown spinner text: $selected")
        this.group = group
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        // Another interface callback
        Log.d(TAG, "onNothingSelected")
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
                // As we directly use the token we don't listen to the token timeout event
                // but instead mark the token instantly as used, to prevent "Captcha failed" error
                // after the correct error is shown in the Registration UI (and some time passed)
                response.markUsed()
                register(response.tokenResult)
            }
            .addOnFailureListener { e: HCaptchaException ->
                progressBar!!.visibility = GONE
                registrationButton!!.isEnabled = true
                Log.d(TAG, "hCaptcha failed: " + e.message + "(" + e.statusCode + ")")
                messageView!!.text = getString(de.cyface.app.utils.R.string.captcha_failed)
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
        // Update view
        emailInput!!.error = null
        passwordInput!!.error = null
        passwordConfirmationInput!!.error = null
        messageView!!.visibility = GONE
        messageView!!.text = ""
        registrationButton!!.isEnabled = false

        // Check for valid credentials
        Validate.notNull(emailInput!!.text)
        Validate.notNull(passwordInput!!.text)
        Validate.notNull(passwordConfirmationInput!!.text)
        Validate.notNull(group)
        val email = emailInput!!.text.toString()
        val password = passwordInput!!.text.toString()
        val passwordConfirmation = passwordConfirmationInput!!.text.toString()
        if (!credentialsAreValid(email, password, passwordConfirmation)) {
            registrationButton!!.isEnabled = true
            return
        }

        // Update view
        progressBar!!.isIndeterminate = true
        progressBar!!.visibility = VISIBLE
        hCaptcha.verifyWithHCaptcha() // calls register() on success
    }

    private fun register(captcha: String) {

        val email = emailInput!!.text.toString()
        val password = passwordInput!!.text.toString()
        GlobalScope.launch {
            // Load authUrl
            val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val url =
                preferences.getString(AUTH_ENDPOINT_URL_SETTINGS_KEY, null)
                    ?: throw IllegalStateException("Auth server url not available.")

            try {
                val authenticator = DefaultAuthenticator(url)

                // Try to send the request and handle expected errors
                // `CyfaceAuthenticator` can only throw `NetworkErrorException` but here we don't
                // have this limitation and don't need to use `sendErrorIntent()`.
                val response = authenticator.register(
                    email,
                    password,
                    captcha,
                    Activation.R4R_ANDROID
                )
                Log.d(TAG, "Response $response")

                when (response) {
                    Result.UPLOAD_SUCCESSFUL -> { // 201 created
                        runOnUiThread {
                            progressBar!!.visibility = GONE
                        }
                        returnToLogin(true)
                    }

                    else -> {
                        error("Unexpected response ($response), API only defines the above.")
                    }
                }
            } catch (e: Exception) {
                val reportingEnabled = preferences.getBoolean(ACCEPTED_REPORTING_KEY, false)

                when (e) {
                    is RegistrationFailed -> {
                        when (e.cause) {
                            is ConflictException -> {
                                runOnUiThread {
                                    emailInput!!.error =
                                        getString(de.cyface.app.utils.R.string.error_message_email_taken)
                                    emailInput!!.requestFocus()
                                }
                            }

                            // TODO: Show message for these expected exceptions in UI
                            /*is SynchronisationException,
                            is ForbiddenException,
                            is NetworkUnavailableException,
                            is TooManyRequestsException,
                            is HostUnresolvable,
                            is ServerUnavailableException,
                            is UnexpectedResponseCode,
                            is InternalServerErrorException*/
                            else -> {
                                /*ErrorHandler.sendErrorIntent(
                                    context.get(),
                                    ErrorCode.SERVER_UNAVAILABLE.code,
                                    e.message
                                )*/
                                if (reportingEnabled) {
                                    Sentry.captureException(e)
                                }
                                Log.e(TAG, "Registration failed with exception", e)
                            }
                        }
                    }

                    else -> {
                        reportError(e)
                    }
                }
                runOnUiThread {
                    progressBar!!.visibility = GONE
                    // Clean up if the getAuthToken failed, else the LoginActivity is probably not shown
                    registrationButton!!.isEnabled = true
                    messageView!!.visibility = VISIBLE
                    messageView!!.text = getString(de.cyface.app.utils.R.string.registration_failed)
                }
            }
        }
    }

    private fun reportError(e: Exception) {
        val reportingEnabled = preferences!!.getBoolean(ACCEPTED_REPORTING_KEY, false)
        if (reportingEnabled) {
            Sentry.captureException(e)
        }
        Log.e(TAG, "Registration failed with exception", e)
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
        } else if (password.length < 6) {
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
        val stored =
            preferences!!.getString(AUTH_ENDPOINT_URL_SETTINGS_KEY, null)
        val currentUrl = BuildConfig.authServer
        Validate.notNull(currentUrl)
        if (stored == null || stored != currentUrl) {
            Log.d(TAG, "Updating Auth API URL from $stored to $currentUrl")
            val editor = preferences!!.edit()
            editor.putString(AUTH_ENDPOINT_URL_SETTINGS_KEY, currentUrl)
            editor.apply()
        }
    }

    private fun registerLoginLink() {
        val loginLink = findViewById<View>(R.id.registration_link_login)
        loginLink.setOnClickListener { v: View? -> returnToLogin(false) }
    }

    private fun returnToLogin(registrationSuccessful: Boolean) {
        callLoginActivityIntent!!.putExtra(
            REGISTERED_EXTRA,
            registrationSuccessful
        )
        startActivity(callLoginActivityIntent)
        finish()
    }

    companion object {
        const val REGISTERED_EXTRA = "de.cyface.registration.successful"
    }
}