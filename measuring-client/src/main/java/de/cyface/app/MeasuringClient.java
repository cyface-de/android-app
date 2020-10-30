/*
 * Copyright 2017 Cyface GmbH
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

import static de.cyface.app.utils.Constants.TAG;
import static de.cyface.synchronization.ErrorHandler.ErrorCode.UNAUTHORIZED;

import android.content.IntentFilter;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.multidex.MultiDexApplication;

import de.cyface.app.ui.LoginActivity;
import de.cyface.synchronization.CyfaceAuthenticator;
import de.cyface.synchronization.ErrorHandler;

/**
 * The implementation of Android's {@code Application} class for this project.
 * <p>
 * This class is used to allow for a larger code base by inheriting <code>MultiDexApplication</code>.
 * <p>
 * It shows the errors (not handled by {@link LoginActivity}) as simple {@link Toast} to the user.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.4.5
 * @since 1.0.0
 */
public class MeasuringClient extends MultiDexApplication {

    private static ErrorHandler errorHandler;
    private final ErrorHandler.ErrorListener errorListener = (errorCode, errorMessage) -> {
        // All other errors are shown by the LoginActivity
        if (errorCode != UNAUTHORIZED) {
            Toast.makeText(MeasuringClient.this, errorMessage, Toast.LENGTH_LONG).show();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Starting Cyface application.");
        CyfaceAuthenticator.LOGIN_ACTIVITY = LoginActivity.class;
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
