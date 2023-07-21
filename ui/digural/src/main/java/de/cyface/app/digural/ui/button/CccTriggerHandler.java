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
package de.cyface.app.digural.ui.button;

import static de.cyface.camera_service.Constants.TAG;

import android.location.Location;
import android.os.Parcel;
import android.util.Log;

import androidx.annotation.NonNull;

import de.cyface.camera_service.TriggerHandler;

/**
 * TODO
 *
 * @author Klemens Muthmann
 * @since 4.2.0
 */
public class CccTriggerHandler implements TriggerHandler {

    /**
     * The world-unique identifier of the device causing the trigger.
     */
    private String deviceId;
    private String endpoint;

    /**
     * The <code>Parcelable</code> creator as required by the Android Parcelable specification.
     */
    @SuppressWarnings("Convert2Diamond") // `cannot use '<>' with anonymous inner classes`
    public static final Creator<CccTriggerHandler> CREATOR = new Creator<CccTriggerHandler>() {
        @Override
        public CccTriggerHandler createFromParcel(final Parcel in) {
            return new CccTriggerHandler(in);
        }

        @Override
        public CccTriggerHandler[] newArray(final int size) {
            return new CccTriggerHandler[size];
        }
    };

    public CccTriggerHandler(final String deviceId) {
        this.deviceId = deviceId;
    }

    /**
     * Constructor as required by {@code Parcelable} implementation.
     *
     * @param in A {@code Parcelable} that is a serialized version of a {@code CccTriggerHandler}.
     */
    private CccTriggerHandler(@SuppressWarnings("unused") final @NonNull Parcel in) {
        // Nothing to do here.
    }

    @Override
    public void trigger(long measurementId, Location location) {
        // TODO: call endpoint on trigger with `deviceId` and `measurementId`
        // and the timestamp and lat/lon from `location`

        // TODO: is it correct that we use the timestamp of the last geo-location (linked to lat/lon)?

        Log.d(TAG, "Image capturing triggered");
    }

    @Override
    public int describeContents() {
        return 0; // FIXME
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // FIXME
    }
}