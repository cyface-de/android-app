/*
 * Copyright 2017-2022 Cyface GmbH
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
package de.cyface.app;

import static de.cyface.app.utils.SharedConstants.ACCEPTED_REPORTING_KEY;

import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.multidex.MultiDexApplication;

import de.cyface.app.ui.LoginActivity;
import de.cyface.synchronization.CyfaceAuthenticator;
import de.cyface.synchronization.ErrorHandler;
import io.sentry.Sentry;

/**
 * The implementation of Android's {@code Application} class for this project.
 * <p>
 * This class is used to allow for a larger code base by inheriting <code>MultiDexApplication</code>.
 * <p>
 * It shows the errors (not handled by {@link LoginActivity}) as simple {@link Toast} to the user.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.5.1
 * @since 1.0.0
 */
public class MeasuringClient extends MultiDexApplication {

    /**
     * Stores the user's preferences.
     */
    private SharedPreferences preferences;

    /**
     * Allows to subscribe to error events.
     */
    private static ErrorHandler errorHandler;

    /**
     * Reports error events to the user via UI and to Sentry, if opted-in.
     */
    private final ErrorHandler.ErrorListener errorListener = (errorCode, errorMessage) -> {
        final var appName = getApplicationContext().getString(R.string.app_name);
        Toast.makeText(MeasuringClient.this, String.format("%s - %s", errorMessage, appName), Toast.LENGTH_LONG).show();

        // There are two cases we can have network errors
        // 1. during authentication (AuthTokenRequest), either login or before upload
        // 2. during upload (SyncPerformer/SyncAdapter)
        // In the first case we get the full stacktrace by a Sentry capture in AuthTokenRequest
        // but in the second case we cannot get the stacktrace as it's only available in the SDK.
        // For that reason we also capture a message here.
        // However, it seems like e.g. a interrupted upload shows a toast but does not trigger sentry.
        final boolean isReportingEnabled = preferences.getBoolean(ACCEPTED_REPORTING_KEY, false);
        if (isReportingEnabled) {
            Sentry.captureMessage(errorCode.name() + ": " + errorMessage);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Register the activity to be called by the authenticator to request credentials from the user.
        CyfaceAuthenticator.LOGIN_ACTIVITY = LoginActivity.class;

        // Register error listener
        errorHandler = new ErrorHandler();
        LocalBroadcastManager.getInstance(this).registerReceiver(errorHandler,
                new IntentFilter(ErrorHandler.ERROR_INTENT));
        getErrorHandler().addListener(errorListener);
    }

    @Override
    public void onTerminate() {
        getErrorHandler().removeListener(errorListener);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(errorHandler);
        super.onTerminate();
    }

    /**
     * @return the {@link ErrorHandler} required to register an {@link ErrorHandler.ErrorListener}
     */
    public static ErrorHandler getErrorHandler() {
        return errorHandler;
    }
}
