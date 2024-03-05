package de.cyface.app.digural.auth

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import de.cyface.synchronization.Auth
import de.cyface.synchronization.Constants
import de.cyface.synchronization.settings.SynchronizationSettings
import de.cyface.utils.Validate
import org.json.JSONObject

/**
 * The WebdavAuth class facilitates the authorization process in Android using Webdav auth mechanism.
 *
 * It helps in managing tokens, performing code exchanges, updating account credentials, creating
 * accounts, and handling sessions.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.8.0
 * @param context The context to load settings and accounts from.
 * @param settings The settings which store the user preferences.
 */
class WebdavAuth(context: Context, settings: SynchronizationSettings) : Auth {

    private var authorized = false

    private var sardine = OkHttpSardine()

    // FIXME: Use the old native LoginActivity to ask the user for the credentials.
    // Store the credentials in the account manager where the password is encrypted.

    override fun performActionWithFreshTokens(action: (accessToken: String?, idToken: String?, ex: Exception?) -> Unit) {
        // The webdav library automatically logs the user in again upon each API access
        // So there is no need to actively check the token freshness.
        action(WebdavAuthenticator.DUMMY_TOKEN, WebdavAuthenticator.DUMMY_TOKEN, null)
    }

    /**
     * Updates the credentials
     *
     * @param context The [Context] required to add an [Account]
     * @param username The username of the account
     */
    private fun updateAccount(
        context: Context,
        username: String,
        password: String,
        accountType: String,
        authority: String
    ) {
        require(username.isNotEmpty())
        require(password.isNotEmpty())
        val accountManager = AccountManager.get(context)
        val account = Account(username, accountType)

        // Update credentials if the account already exists
        var accountUpdated = false
        val existingAccounts = accountManager.getAccountsByType(accountType)
        for (existingAccount in existingAccounts) {
            if (existingAccount == account) {
                accountManager.setPassword(account, password)
                accountManager.setAuthToken(
                    account,
                    Constants.AUTH_TOKEN_TYPE,
                    WebdavAuthenticator.DUMMY_TOKEN
                )
                accountUpdated = true
                Log.d(Constants.TAG, "Updated existing account.")
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
                Log.d(Constants.TAG, "Removed existing account: $existingAccount")
            }
            createAccount(
                context,
                username,
                password,
                accountType,
                authority
            )
        }
        Validate.isTrue(accountManager.getAccountsByType(accountType).size == 1)
    }

    /**
     * Creates a temporary `Account` which can only be used to check the credentials.
     *
     * **ATTENTION:** If the login is successful you need to use `WiFiSurveyor.makeAccountSyncable`
     * to ensure the `WifiSurveyor` works as expected. We cannot inject the `WiFiSurveyor` as the
     * `LoginActivity` is called by Android.
     *
     * @param context The current Android context (i.e. Activity or Service).
     * @param username The username of the account to be created.
     * @param accountType The type of account to create.
     * @param authority The provider to create an account for.
     */
    private fun createAccount(
        context: Context,
        username: String,
        password: String,
        accountType: String,
        authority: String
    ) {
        val accountManager = AccountManager.get(context)
        val newAccount = Account(username, accountType)
        val userData = Bundle()
        Validate.isTrue(accountManager.addAccountExplicitly(newAccount, password, userData))
        accountManager.setAuthToken(
            newAccount,
            Constants.AUTH_TOKEN_TYPE,
            WebdavAuthenticator.DUMMY_TOKEN
        )
        Validate.isTrue(accountManager.getAccountsByType(accountType).size == 1)
        Log.v(Constants.TAG, "New account added")
        ContentResolver.setSyncAutomatically(newAccount, authority, false)
        // Synchronization can be disabled via {@link CyfaceDataCapturingService#setSyncEnabled}
        ContentResolver.setIsSyncable(newAccount, authority, 1)
        // Do not use validateAccountFlags in production code as periodicSync flags are set async

        // PeriodicSync and syncAutomatically is set dynamically by the {@link WifiSurveyor}
    }

    fun isAuthorized(): Boolean {
        // FIXME: This should be checked via the Account Manager as we use different instances
        // of [WebdavAuth].
        return authorized
    }

    fun signOut() {
        authorized = false
        sardine.setCredentials(null, null) // FIXME: is this allowed?
        // FIXME: we don't use the same sardine instance in the uploader
        // After this method is called, `MainActivity` deletes the account from account manager.
    }

    /**
     * To be called after the user just logged in. Updates the account in the account manager.
     *
     * FIXME: compare with pre-OAuth2 `LoginActivity.attemptLogin()` flow.
     */
    fun onLogIn(
        username: String,
        password: String,
        applicationContext: Context,
        accountType: String,
        authority: String
    ) {
        updateAccount(applicationContext, username, password, accountType, authority)

        // FIXME: we anyway don't use the same sardine instance in the uploader
        sardine.setCredentials(username, password)
        authorized = true
    }


    companion object {
        /**
         * Returns a dummy auth config as the [WebdavAuth] does not require such.
         */
        fun dummyAuthConfig(): JSONObject {
            // `discovery_url` needs to be set to a https address or else the app crashes.
            return JSONObject().put("discovery_uri", "https://NOT_REQUIRED_IN_WEBDAY")
        }
    }
}