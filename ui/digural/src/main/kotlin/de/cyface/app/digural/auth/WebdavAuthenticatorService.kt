/*
 * Copyright 2024 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.app.digural.auth

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import de.cyface.app.digural.utils.Constants

/**
 * The Android service used to communicate with the Stub Authenticator. This has been implemented as described in
 * [the Android documentation](https://developer.android.com/training/sync-adapters/creating-authenticator.html#CreateAuthenticatorService).
 *
 * This is a custom implementation of the [de.cyface.synchronization.AuthenticatorService].
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.8.0
 */
class WebdavAuthenticatorService : Service() {
    /**
     * The `Authenticator` called from this service.
     */
    private var authenticator: WebdavAuthenticator? = null

    override fun onCreate() {
        authenticator = WebdavAuthenticator(this)
    }

    override fun onBind(intent: Intent): IBinder? {
        return authenticator!!.iBinder
    }
}