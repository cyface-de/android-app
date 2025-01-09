/*
 * Copyright 2017-2025 Cyface GmbH
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
package de.cyface.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import android.os.Parcel
import android.os.Parcelable.Creator
import android.util.Log
import androidx.core.app.NotificationCompat
import de.cyface.app.MainActivity
import de.cyface.app.R
import de.cyface.app.utils.Constants.TAG
import de.cyface.app.utils.SharedConstants.NOTIFICATION_CHANNEL_ID_RUNNING
import de.cyface.app.utils.SharedConstants.NOTIFICATION_CHANNEL_ID_WARNING
import de.cyface.app.utils.SharedConstants.SPACE_WARNING_NOTIFICATION_ID
import de.cyface.datacapturing.EventHandlingStrategy
import de.cyface.datacapturing.backend.DataCapturingBackgroundService
import de.cyface.utils.Validate

/**
 * A [EventHandlingStrategy] to respond to specified events triggered by the
 * [DataCapturingBackgroundService].
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 3.0.3
 * @since 2.5.0
 */
class CapturingEventHandler : EventHandlingStrategy {
    constructor() {
        // Nothing to do here.
    }

    /**
     * Constructor as required by `Parcelable` implementation.
     *
     * @param in A `Parcel` that is a serialized version of a `DataCapturingEventHandler`.
     */
    @Suppress("UNUSED_PARAMETER")
    private constructor(`in`: Parcel) {
        // Nothing to do here.
    }

    override fun handleSpaceWarning(dataCapturingBackgroundService: DataCapturingBackgroundService) {
        showSpaceWarningNotification(dataCapturingBackgroundService.applicationContext)
        dataCapturingBackgroundService.stopSelf()
        dataCapturingBackgroundService.sendStoppedItselfMessage()
        Log.i(TAG, "Low space event triggered - DCS capturing stopped.")
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {}

    /**
     * A [Notification] shown when the [DataCapturingBackgroundService] triggered the low space event.
     *
     * @param context The context if the service used to show the [Notification]. It stays even
     * when the service is stopped as long as a unique id is used.
     */
    private fun showSpaceWarningNotification(context: Context) {
        val onClickIntent = Intent(context, MainActivity::class.java)
        val onClickPendingIntent: PendingIntent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.getActivity(
                    context, 0, onClickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                // Ignore warning: immutable flag only available in API >= 23, see above
                PendingIntent.getActivity(
                    context, 0, onClickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
        val notificationManager = context
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Validate.notNull(notificationManager)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            createNotificationChannelIfNotExists(
                context, NOTIFICATION_CHANNEL_ID_WARNING,
                context.getString(de.cyface.app.utils.R.string.notification_channel_name_warning),
                context.getString(de.cyface.app.utils.R.string.notification_channel_description_warning),
                NotificationManager.IMPORTANCE_HIGH, true, Color.RED, true
            )
        }
        val notificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(
            context,
            NOTIFICATION_CHANNEL_ID_WARNING
        ).setContentIntent(onClickPendingIntent)
            .setSmallIcon(R.drawable.ic_logo_only_c)
            .setContentTitle(context.getString(de.cyface.app.utils.R.string.notification_title_capturing_stopped))
            .setContentText(context.getString(de.cyface.app.utils.R.string.error_message_capturing_canceled_no_space))
            .setOngoing(false).setWhen(System.currentTimeMillis()).setPriority(2)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(500, 1500))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        notificationManager.notify(SPACE_WARNING_NOTIFICATION_ID, notificationBuilder.build())
    }

    override fun buildCapturingNotification(context: DataCapturingBackgroundService): Notification {
        Validate.notNull(context, "No context provided!")
        val channelId: String = NOTIFICATION_CHANNEL_ID_RUNNING

        // Open Activity when the notification is clicked
        val onClickIntent = Intent(context, MainActivity::class.java)
        val onClickPendingIntent: PendingIntent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.getActivity(
                    context, 0, onClickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                // Ignore warning: immutable flag only available in API >= 23, see above
                PendingIntent.getActivity(
                    context, 0, onClickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            createNotificationChannelIfNotExists(
                context, channelId, "Cyface",
                context.getString(de.cyface.datacapturing.R.string.notification_channel_description_running),
                NotificationManager.IMPORTANCE_LOW, false, Color.GREEN, false
            )
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getText(de.cyface.datacapturing.R.string.capturing_active))
            .setContentIntent(onClickPendingIntent).setWhen(System.currentTimeMillis())
            .setOngoing(true)
            .setAutoCancel(false)

        // APIs < 21 crash when replacing a notification icon with a vector icon
        // What works is: use a png to replace the icon on API < 21 or to reuse the same vector icon
        // The most elegant solution seems to be to have PNGs for the icons and the vector xml in drawable-anydpi-v21,
        // see https://stackoverflow.com/a/37334176/5815054
        builder.setSmallIcon(R.drawable.ic_logo_only_c)
        return builder.build()
    }

    companion object {
        /**
         * The `Parcelable` creator as required by the Android Parcelable specification.
         */
        @Suppress("unused")
        @JvmField
        val CREATOR: Creator<CapturingEventHandler?> =
            object : Creator<CapturingEventHandler?> {
                override fun createFromParcel(`in`: Parcel): CapturingEventHandler {
                    return CapturingEventHandler(`in`)
                }

                override fun newArray(size: Int): Array<CapturingEventHandler?> {
                    return arrayOfNulls(size)
                }
            }

        /**
         * Since Android 8 it is necessary to create a new notification channel for a foreground service notification. To
         * save system resources this should only happen if the channel does not exist. This method does just that.
         *
         * @param context The Android `Context` to use to create the notification channel.
         * @param channelId The identifier of the created or existing channel.
         */
        private fun createNotificationChannelIfNotExists(
            context: Context,
            channelId: String, channelName: String, description: String,
            importance: Int, enableLights: Boolean, lightColor: Int, enableVibration: Boolean
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return
            }
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            Validate.notNull(manager, "Manager for service notifications not available.")
            if (manager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(channelId, channelName, importance)
                channel.description = description
                channel.enableLights(enableLights)
                channel.enableVibration(enableVibration)
                channel.lightColor = lightColor
                manager.createNotificationChannel(channel)
            }
        }
    }
}