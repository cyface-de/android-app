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
package de.cyface.app.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import de.cyface.utils.Validate;

/**
 * Basic class which provides info about the connection status
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 1.0.0
 *
 * TODO Should move to the SDK (Wifi-Surveyor)
 */
public class ConnectionInfo {

    private Context context;

    public ConnectionInfo(Context context) {
        this.context = context;
    }

    public boolean isConnectedToWifi() {
        final ConnectivityManager manager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Validate.notNull(manager);
        final NetworkInfo networkInfo = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        Validate.notNull(networkInfo);
        return networkInfo.isConnected();
    }
}
