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
package de.cyface.app.utils

/**
 * This class holds all constants required by multiple classes. This avoids unnecessary dependencies
 * which would only be needed to access those constants.
 *
 * @author Armin Schnabel
 * @version 1.2.0
 * @since 3.2.0
 */
object SharedConstants {

    /**
     * The package of this library.
     */
    private const val PACKAGE = "de.cyface.app.utils"

    /**
     * The tag used for logging.
     */
    const val TAG = PACKAGE

    /**
     * Name of the database used by the content provider to store data. This always must be the same
     * as the SDK.DatabaseHelper.DATABASE_NAME constant. We don't want to make DatabaseHelper
     */
    const val DATABASE_NAME = "measures"

    /*
     * Notification channel ids
     */

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

    /*
     * Notifications ids - keep them in one place to avoid duplicate id usage
     */

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
}