/*
 * Copyright 2024 Cyface GmbH
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
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import com.thegrizzlylabs.sardineandroid.impl.SardineException
import de.cyface.app.digural.upload.WebdavSyncService
import de.cyface.app.digural.upload.WebdavUploader
import de.cyface.app.digural.utils.Constants.ACCOUNT_TYPE
import de.cyface.synchronization.Auth
import de.cyface.synchronization.Constants
import de.cyface.synchronization.settings.SynchronizationSettings
import de.cyface.utils.Validate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
class WebdavAuth(private val context: Context, private val settings: SynchronizationSettings) : Auth {

    private var authorized = false

    private var sardine = OkHttpSardine()

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
                    WebdavSyncService.AUTH_TOKEN_TYPE,
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
            WebdavSyncService.AUTH_TOKEN_TYPE,
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

    private fun signOut() {
        authorized = false
        sardine.setCredentials(null, null)
        // After this method is called, `MainActivity` deletes the account from account manager.
    }

    /**
     * To be called after the user just logged in. Updates the account in the account manager.
     */
    fun login(
        username: String,
        password: String,
        applicationContext: Context,
        accountType: String,
        authority: String
    ) {
        updateAccount(applicationContext, username, password, accountType, authority)

        sardine.setCredentials(username, password)

        // Send a small request to ensure the credentials are correct
        try {
            val rootDir = WebdavUploader.returnUrlWithTrailingSlash(collectorApi()) + "files/$username/"
            sardine.list(rootDir)
            Log.d(Constants.TAG, "Login successful.")
        } catch (e: SardineException) {
            Log.e(Constants.TAG, "Login failed: ${e.message}")
            signOut()
            throw LoginFailed(e)
        }

        // Credentials are valid, store password safely in the Keystore for Uploader to retrive

        authorized = true
    }

    /**
     * Reads the Collector API URL from the preferences.
     *
     * @return The URL as string
     */
    private fun collectorApi(): String {
        // The `collectorApi` stands for the webdav API which collects the data from us.
        val apiEndpoint =
            runBlocking { WebdavAuthenticator.settings.collectorUrlFlow.first() }
        Validate.notNull(
            apiEndpoint,
            "Sync canceled: Server url not available. Please set the applications server url preference."
        )
        return apiEndpoint
    }

    fun getAccount(): Account {
        val accountManager = AccountManager.get(context)
        return accountManager.getAccountsByType(ACCOUNT_TYPE)[0]
    }

    fun getPassword(account: Account): String {
        val accountManager = AccountManager.get(context)
        return accountManager.getPassword(account)
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