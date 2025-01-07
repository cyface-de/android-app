/*
 * Copyright 2017-2024 Cyface GmbH
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
package de.cyface.app.digural.auth

import android.accounts.Account
import android.accounts.AccountAuthenticatorActivity
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText
import de.cyface.app.digural.Application
import de.cyface.app.digural.Application.Companion.errorHandler
import de.cyface.app.digural.R
import de.cyface.app.digural.upload.WebdavSyncService
import de.cyface.app.digural.utils.Constants
import de.cyface.app.digural.utils.Constants.ACCOUNT_TYPE
import de.cyface.app.digural.utils.Constants.AUTHORITY
import de.cyface.app.digural.utils.Constants.TAG
import de.cyface.synchronization.CyfaceAuthenticator
import de.cyface.synchronization.ErrorHandler
import de.cyface.synchronization.ErrorHandler.ErrorCode
import de.cyface.utils.Validate
import de.cyface.utils.settings.AppSettings
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.RuntimeException
import java.lang.ref.WeakReference
import java.util.regex.Pattern

/**
 * A login screen that offers login via email/password.
 *
 * @author Armin Schnabel
 * @version 3.3.0
 * @since 1.0.0
 */
class LoginActivity : AccountAuthenticatorActivity() {
    private lateinit var context: WeakReference<Context>

    private val activityScope = CoroutineScope(Job() + Dispatchers.IO)

    /**
     * The settings used by both, UIs and libraries.
     */
    private val appSettings: AppSettings
        get() = Application.appSettings

    // UI references
    private var progressBar: ProgressBar? = null

    /**
     * A `Button` which is used to confirm the entered credentials.
     */
    private var loginButton: Button? = null

    /**
     * Needs to be resettable for testing. That's the only way to mock a single method of Android's Activity's
     */
    @JvmField
    var loginInput: TextInputEditText? = null

    @JvmField
    var passwordInput: TextInputEditText? = null

    /**
     * Needs to be nullable and resettable for testing as Android's `Patterns` is null in unit test.
     *
     * That's the only way to mock a single method of Android's Activity's.
     */
    @JvmField
    var eMailPattern: Pattern? = Patterns.EMAIL_ADDRESS

    private val errorListener = ErrorHandler.ErrorListener { errorCode, errorMessage, _ ->
        if (errorCode == ErrorCode.UNAUTHORIZED) {
            passwordInput!!.error = errorMessage
            passwordInput!!.requestFocus()

            // All other errors are shown as toast by the MeasuringClient
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        context = WeakReference(this)

        // Set up the login form
        loginInput = findViewById(R.id.input_login)
        passwordInput = findViewById(R.id.input_password)
        loginButton = findViewById(R.id.login_button)
        loginButton!!.setOnClickListener { attemptLogin() }
        progressBar = findViewById(R.id.login_progress_bar)
        errorHandler!!.addListener(errorListener)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Required to get the latest intent in `onResume`, when using `singleTask` launch mode
        setIntent(intent)
    }

    override fun onDestroy() {
        errorHandler!!.removeListener(errorListener)
        super.onDestroy()
    }

    /**
     * Attempts to sign in with the account specified by the login form.
     *
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private fun attemptLogin() {
        // Update view
        loginInput!!.error = null
        passwordInput!!.error = null
        loginButton!!.isEnabled = false

        // Check for valid credentials
        Validate.notNull(loginInput!!.text)
        Validate.notNull(passwordInput!!.text)
        val login = loginInput!!.text.toString()
        val password = passwordInput!!.text.toString()
        if (!credentialsAreValid(login, password, LOGIN_MUST_BE_AN_EMAIL_ADDRESS)) {
            loginButton!!.isEnabled = true
            return
        }

        // Update view
        progressBar!!.isIndeterminate = true
        progressBar!!.visibility = View.VISIBLE

        // The CyfaceAuthenticator reads the credentials from the account so we store them there
        updateAccount(this, login, password)

        // Send async login attempt
        activityScope.launch {
            try {
                val account: Account = getAccount(context.get()!!)

                // Verify the login credentials
                WebdavAuth(context.get()!!, WebdavAuthenticator.settings).login(
                    login, password, context.get()!!, ACCOUNT_TYPE, AUTHORITY
                )

                // Explicitly calling WebdavAuthenticator.getAuthToken(), see its documentation
                val authenticator = WebdavAuthenticator(context.get()!!)
                val authToken = authenticator.getAuthToken(
                        null,
                        account,
                        WebdavSyncService.AUTH_TOKEN_TYPE,
                        null
                    ).getString(AccountManager.KEY_AUTHTOKEN)!!

                Validate.notNull(authToken)
                Log.d(TAG, "Setting auth token to: **" + authToken.substring(authToken.length - 7))
                val accountManager = AccountManager.get(context.get())
                accountManager.setAuthToken(
                    account,
                    WebdavSyncService.AUTH_TOKEN_TYPE,
                    authToken
                )
                //return@async AuthTokenRequestParams(account, true)

                // Equals tutorial's "finishLogin()"
                val intent = Intent()
                intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, account.name)
                intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, ACCOUNT_TYPE)
                intent.putExtra(
                    AccountManager.KEY_AUTHTOKEN,
                    WebdavSyncService.AUTH_TOKEN_TYPE
                )

                // Return the information back to the Authenticator
                setAccountAuthenticatorResult(intent.extras)
                setResult(RESULT_OK, intent)

                // Hide the keyboard or else it can overlap the permission request
                runOnUiThread {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        loginButton!!.windowInsetsController!!.hide(WindowInsetsCompat.Type.ime())
                    } else {
                        ViewCompat.getWindowInsetsController(loginButton!!)!!
                            .hide(WindowInsetsCompat.Type.ime())
                    }
                    progressBar!!.visibility = View.GONE
                    finish()
                }
            } catch (e: Exception) {
                // Using ErrorHandler to show soft error like "account not activated" instead
                when (e) {
                    is LoginFailed -> {
                        ErrorHandler.sendErrorIntent(
                            context.get(),
                            ErrorCode.AUTHENTICATION_ERROR.code,
                            e.message,
                            false
                        )
                    }
                }

                reportError(e)

                runOnUiThread {
                    progressBar!!.visibility = View.GONE
                    // Clean up if the getAuthToken failed, else the LoginActivity is probably not shown
                    try {
                        val account: Account = getAccount(context.get()!!)
                        deleteAccount(context.get()!!, account)
                    } catch (e: RuntimeException) {
                        Log.e(TAG, "Failed to delete account after login failure.", e)
                    }
                    loginButton!!.isEnabled = true
                }
            }
        }
    }

    private fun reportError(e: Exception) {
        // Before, we could not capture the exceptions in CyfaceAuthenticator as it's part of the SDK.
        // We also didn't want to capture the errors in the error handler as we don't have the stacktrace there.
        val reportErrors = runBlocking { appSettings.reportErrorsFlow.first() }
        if (reportErrors) {
            Sentry.captureException(e)
        }
        // "the authenticator could not honor the request due to a network error"
        Log.d(TAG, "Login failed - removing account to allow new login.", e)
    }

    /**
     * Returns the Cyface account. Throws a Runtime Exception if no or more than one account exists.
     *
     * @param context the Context to load the AccountManager
     * @return the only existing account
     */
    private fun getAccount(context: Context): Account {
        val accountManager = AccountManager.get(context)
        val existingAccounts = accountManager.getAccountsByType(ACCOUNT_TYPE)
        Validate.isTrue(existingAccounts.size < 2, "More than one account exists.")
        Validate.isTrue(existingAccounts.isNotEmpty(), "No account exists.")
        return existingAccounts[0]
    }

    /**
     * Checks if the format of the credentials provided is valid, i.e. has the allowed length and
     * is not empty and, if requested, checks if the login is an email address.
     *
     * @param login The login string
     * @param password The password string
     * @param loginMustBeAnEmailAddress True if the login should be checked to be a valid email address
     * @return true is the credentials are in a valid format
     */
    fun credentialsAreValid(
        login: String?,
        password: String?,
        loginMustBeAnEmailAddress: Boolean
    ): Boolean {
        var valid = true
        if (login.isNullOrEmpty()) {
            loginInput!!.error =
                getString(R.string.error_message_field_required)
            loginInput!!.requestFocus()
            valid = false
        } else if (login.length < 4) {
            loginInput!!.error =
                getString(R.string.error_message_login_too_short)
            loginInput!!.requestFocus()
            valid = false
        } else if (loginMustBeAnEmailAddress && !eMailPattern!!.matcher(login).matches()) {
            loginInput!!.error = getString(R.string.error_message_invalid_email)
            loginInput!!.requestFocus()
            valid = false
        }
        if (password.isNullOrEmpty()) {
            passwordInput!!.error =
                getString(R.string.error_message_field_required)
            passwordInput!!.requestFocus()
            valid = false
        } else if (password.length < 6) {
            passwordInput!!.error =
                getString(R.string.error_message_password_too_short)
            passwordInput!!.requestFocus()
            valid = false
        } else if (password.length > 33) {
            passwordInput!!.error =
                getString(R.string.error_message_password_too_short)
            passwordInput!!.requestFocus()
            valid = false
        }
        return valid
    }

    companion object {
        // True if the login must match the email pattern TODO [CY-4099]: Needs to be configurable from outside
        private const val LOGIN_MUST_BE_AN_EMAIL_ADDRESS = false

        /**
         * Updates the credentials
         *
         * @param context The [Context] required to add an [Account]
         * @param login The username of the account
         * @param password The password of the account
         */
        private fun updateAccount(context: Context, login: String, password: String) {
            Validate.notEmpty(login)
            Validate.notEmpty(password)
            val accountManager = AccountManager.get(context)
            val account = Account(login, ACCOUNT_TYPE)

            // Update credentials if the account already exists
            var accountUpdated = false
            val existingAccounts = accountManager.getAccountsByType(ACCOUNT_TYPE)
            for (existingAccount in existingAccounts) {
                if (existingAccount == account) {
                    accountManager.setPassword(account, password)
                    accountUpdated = true
                    Log.d(TAG, "Updated existing account.")
                }
            }

            // Add new account when it does not yet exist
            if (!accountUpdated) {

                // Delete unused Cyface accounts
                for (existingAccount in existingAccounts) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                        accountManager.removeAccountExplicitly(existingAccount)
                    } else {
                        accountManager.removeAccount(account, null, null)
                    }
                    Log.d(TAG, "Removed existing account: $existingAccount")
                }
                createAccount(context, login, password)
            }
            Validate.isTrue(accountManager.getAccountsByType(ACCOUNT_TYPE).size == 1)
        }

        /**
         * Creates a temporary `Account` which can only be used to check the credentials.
         *
         * **ATTENTION:** If the login is successful you need to use `WiFiSurveyor.makeAccountSyncable`
         * to ensure the `WifiSurveyor` works as expected. We cannot inject the `WiFiSurveyor` as the
         * [LoginActivity] is called by Android.
         *
         * @param context The current Android context (i.e. Activity or Service).
         * @param username The username of the account to be created.
         * @param password The password of the account to be created. May be null if a custom [CyfaceAuthenticator] is
         * used instead of a LoginActivity to return tokens as in `MovebisDataCapturingService`.
         */
        private fun createAccount(
            context: Context, username: String,
            password: String
        ) {
            val accountManager = AccountManager.get(context)
            val newAccount = Account(username, ACCOUNT_TYPE)
            Validate.isTrue(accountManager.addAccountExplicitly(newAccount, password, Bundle.EMPTY))
            Validate.isTrue(accountManager.getAccountsByType(ACCOUNT_TYPE).size == 1)
            Log.v(de.cyface.synchronization.Constants.TAG, "New account added")
            ContentResolver.setSyncAutomatically(newAccount, Constants.AUTHORITY, false)
            // Synchronization can be disabled via {@link CyfaceDataCapturingService#setSyncEnabled}
            ContentResolver.setIsSyncable(newAccount, Constants.AUTHORITY, 1)
            // Do not use validateAccountFlags in production code as periodicSync flags are set async

            // PeriodicSync and syncAutomatically is set dynamically by the {@link WifiSurveyor}
        }

        /**
         * This method removes the existing account. This is useful as we add a temporary account to check
         * the credentials but we have to remove it when the credentials are incorrect.
         *
         * This static method must be implemented as in the non-static `WiFiSurveyor.deleteAccount`.
         * We cannot inject the `WiFiSurveyor` as the [LoginActivity] is called by Android.
         *
         * @param context The [Context] to get the [AccountManager]
         * @param account the `Account` to be removed
         */
        private fun deleteAccount(context: Context, account: Account) {
            ContentResolver.removePeriodicSync(account, Constants.AUTHORITY, Bundle.EMPTY)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                AccountManager.get(context).removeAccount(account, null, null)
            } else {
                AccountManager.get(context).removeAccountExplicitly(account)
            }
        }
    }
}