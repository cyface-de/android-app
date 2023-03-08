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
package de.cyface.app.r4r.utils

import de.cyface.synchronization.SyncService

/**
 * This class holds all constants required by multiple classes. This avoids unnecessary dependencies
 * which would only be needed to access those constants.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 3.3.1
 * @since 1.0.0
 */
object Constants {
    const val PERMISSION_REQUEST_ACCESS_FINE_LOCATION = 1
    const val PERMISSION_REQUEST_EXTERNAL_STORAGE_FOR_EXPORT = 2
    const val PACKAGE = "de.cyface.app.rfr"
    const val TAG = PACKAGE // This can be references as default TAG for this app
    const val SUPPORT_EMAIL = "support@cyface.de"

    /**
     * Identifies the accepted terms version in the preferences.
     */
    const val ACCEPTED_TERMS_KEY = "$PACKAGE.accepted_terms"

    /**
     * Identifies if the user opted-in to error reporting in the preferences.
     */
    const val ACCEPTED_REPORTING_KEY = "$PACKAGE.accepted_reporting"
    const val PREFERENCES_SERVER_KEY: String = SyncService.SYNC_ENDPOINT_URL_SETTINGS_KEY
    const val PREFERENCES_MODALITY_KEY = "$PACKAGE.modality"
    const val PREFERENCES_MOVE_TO_LOCATION_KEY = "$PACKAGE.zoom_to_location"
    const val PREFERENCES_SYNCHRONIZATION_KEY = "$PACKAGE.synchronization_enabled"

    /**
     * must be different from other SDK using apps
     */
    const val AUTHORITY = "de.cyface.app.rfr.provider"
    const val ACCOUNT_TYPE = "de.cyface.app.rfr"

    /**
     * Name of the database used by the content provider to store data. This always must be the same
     * as the SDK.DatabaseHelper.DATABASE_NAME constant. We don't want to make DatabaseHelper
     */
    const val DATABASE_NAME = "measures"

    /**
     * Identifies the [android.app.NotificationChannel] responsible for warnings.
     * The channel is used for critical notifications, e.g. when the capturing was forced to stop.
     */
    const val NOTIFICATION_CHANNEL_ID_WARNING = "cyface_warnings"

    /**
     * Identifies the [android.app.NotificationChannel] responsible for showing a notification during data
     * capturing.
     */
    const val NOTIFICATION_CHANNEL_ID_RUNNING = "cyface_running"
    // Notifications ids - keep them in one place to avoid duplicate id usage
    /**
     * Identifies the space warning [android.app.Notification] which can be implemented by sdk using apps.
     */
    const val SPACE_WARNING_NOTIFICATION_ID = 2

    /**
     * Identifies the camera access lost [android.app.Notification] which can be implemented by sdk using apps.
     */
    const val CAMERA_ACCESS_LOST_NOTIFICATION_ID = 3

    /**
     * Identifies the picture capturing rate decrease [android.app.Notification] which can be implemented by sdk
     * using apps.
     */
    const val PICTURE_CAPTURING_DECREASED_NOTIFICATION_ID = 4

    /**
     * The key for the `SharedPreferences`. This entry contains the preferred sensor frequency.
     */
    const val PREFERENCES_SENSOR_FREQUENCY_KEY = "$PACKAGE.sensor_frequency"

    /**
     * Default value in Hz when there is no user preference stored yet.
     */
    const val DEFAULT_SENSOR_FREQUENCY = 100
}