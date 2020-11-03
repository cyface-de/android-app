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

import de.cyface.synchronization.SyncService;

/**
 * This class holds all constants required by multiple classes. This avoids unnecessary dependencies
 * which would only be needed to access those constants.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.2.0
 * @since 1.0.0
 */
public class Constants {

    public final static int PERMISSION_REQUEST_ACCESS_FINE_LOCATION = 1;
    public final static int PERMISSION_REQUEST_EXTERNAL_STORAGE_FOR_EXPORT = 2;

    public final static String PACKAGE = "de.cyface.app";
    public final static String TAG = PACKAGE; // This can be references as default TAG for this app
    public final static String SUPPORT_EMAIL = "mail@cyface.de";
    /**
     * Identifies the accepted terms version in the preferences.
     */
    public final static String ACCEPTED_TERMS_KEY = PACKAGE + ".accepted_terms";
    /**
     * Identifies if the user opted-in to error reporting in the preferences.
     */
    public final static String ACCEPTED_REPORTING_KEY = PACKAGE + ".accepted_reporting";
    public final static String PREFERENCES_SERVER_KEY = SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY;
    public final static String PREFERENCES_MODALITY_KEY = PACKAGE + ".modality";
    public final static String PREFERENCES_MOVE_TO_LOCATION_KEY = PACKAGE + ".zoom_to_location";
    public final static String PREFERENCES_SYNCHRONIZATION_KEY = PACKAGE + ".synchronization_enabled";

    /**
     * must be different from other SDK using apps
     */
    public final static String AUTHORITY = "de.cyface.app.provider";
    public final static String ACCOUNT_TYPE = "de.cyface.app.pro";

    /**
     * Name of the database used by the content provider to store data. This always must be the same
     * as the SDK.DatabaseHelper.DATABASE_NAME constant. We don't want to make DatabaseHelper
     */
    public static final String DATABASE_NAME = "measures";

    /**
     * Identifies the {@link android.app.NotificationChannel} responsible for warnings.
     * The channel is used for critical notifications, e.g. when the capturing was forced to stop.
     */
    public final static String NOTIFICATION_CHANNEL_ID_WARNING = "cyface_warnings";
    /**
     * Identifies the {@link android.app.NotificationChannel} responsible for showing a notification during data
     * capturing.
     */
    public final static String NOTIFICATION_CHANNEL_ID_RUNNING = "cyface_running";

    // Notifications ids - keep them in one place to avoid duplicate id usage

    /**
     * Identifies the space warning {@link android.app.Notification} which can be implemented by sdk using apps.
     */
    public final static int SPACE_WARNING_NOTIFICATION_ID = 2;

    /**
     * Identifies the camera access lost {@link android.app.Notification} which can be implemented by sdk using apps.
     */
    public final static int CAMERA_ACCESS_LOST_NOTIFICATION_ID = 3;

    /**
     * Identifies the picture capturing rate decrease {@link android.app.Notification} which can be implemented by sdk
     * using apps.
     */
    public final static int PICTURE_CAPTURING_DECREASED_NOTIFICATION_ID = 4;

    /**
     * The key for the {@code SharedPreferences}. This entry contains the preferred sensor frequency.
     */
    public static final String PREFERENCES_SENSOR_FREQUENCY_KEY = PACKAGE + ".sensor_frequency";

    /**
     * Default value in Hz when there is no user preference stored yet.
     */
    public final static int DEFAULT_SENSOR_FREQUENCY = 100;
}
