/*
 * Copyright 2017-2024 Cyface GmbH
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
package de.cyface.app.r4r.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import de.cyface.app.r4r.MainActivity
import de.cyface.app.r4r.R
import de.cyface.app.utils.SharedConstants.CAMERA_ACCESS_LOST_NOTIFICATION_ID
import de.cyface.app.utils.SharedConstants.NOTIFICATION_CHANNEL_ID_RUNNING
import de.cyface.app.utils.SharedConstants.NOTIFICATION_CHANNEL_ID_WARNING
import de.cyface.app.utils.SharedConstants.PICTURE_CAPTURING_DECREASED_NOTIFICATION_ID
import de.cyface.camera_service.background.BackgroundService
import de.cyface.camera_service.foreground.NotificationStrategy
import de.cyface.utils.Validate
import kotlinx.parcelize.Parcelize

/**
 * A [NotificationStrategy] to respond to specified events triggered by the
 * [BackgroundService].
 *
 * @author Armin Schnabel
 */
@Parcelize
class CameraEventHandler : NotificationStrategy {
    /**
     * A [Notification] shown when the [BackgroundService] triggered the 'camera error' event.
     *
     * @param context The context if the service used to show the [Notification]. It stays even
     * when the service is stopped as long as a unique id is used.
     */
    override fun showCameraErrorNotification(context: Context, reason: String) {

        // Open Activity when the notification is clicked
        val onClickIntent = Intent(context, MainActivity::class.java)
        val onClickPendingIntent: PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                onClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notificationManager = context
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Validate.notNull(notificationManager)
        createNotificationChannelIfNotExists(
            context, NOTIFICATION_CHANNEL_ID_WARNING,
            context.getString(de.cyface.app.utils.R.string.notification_channel_name_warning),
            context.getString(de.cyface.app.utils.R.string.notification_channel_description_warning),
            NotificationManager.IMPORTANCE_HIGH, true, Color.RED, true
        )
        val notificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(
            context,
            NOTIFICATION_CHANNEL_ID_WARNING
        ).setContentIntent(onClickPendingIntent)
            .setSmallIcon(R.drawable.ic_logo_white)
            .setContentTitle(context.getString(de.cyface.app.utils.R.string.notification_title_capturing_stopped))
            .setContentText(reason)
            .setOngoing(false).setWhen(System.currentTimeMillis()).setPriority(2)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(500, 1500))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        notificationManager.notify(CAMERA_ACCESS_LOST_NOTIFICATION_ID, notificationBuilder.build())
    }

    /**
     * A [Notification] shown when the [BackgroundService] triggered the camera access lost event.
     *
     * @param context The context if the service used to show the [Notification]. It stays even
     * when the service is stopped as long as a unique id is used.
     */
    override fun showCameraAccessLostNotification(context: Context) {

        // Open Activity when the notification is clicked
        val onClickIntent = Intent(context, MainActivity::class.java)
        val onClickPendingIntent: PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                onClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notificationManager = context
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Validate.notNull(notificationManager)
        createNotificationChannelIfNotExists(
            context, NOTIFICATION_CHANNEL_ID_WARNING,
            context.getString(de.cyface.app.utils.R.string.notification_channel_name_warning),
            context.getString(de.cyface.app.utils.R.string.notification_channel_description_warning),
            NotificationManager.IMPORTANCE_HIGH,
            true,
            Color.RED,
            true,
        )
        val notificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(
            context,
            NOTIFICATION_CHANNEL_ID_WARNING
        ).setContentIntent(onClickPendingIntent)
            .setSmallIcon(R.drawable.ic_logo_white)
            .setContentTitle(context.getString(de.cyface.app.utils.R.string.notification_title_capturing_stopped))
            .setContentText(
                context.getString(
                    de.cyface.camera_service.R.string.notification_text_capturing_stopped_camera_disconnected
                )
            )
            .setOngoing(false).setWhen(System.currentTimeMillis()).setPriority(2)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(500, 1500))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        notificationManager.notify(CAMERA_ACCESS_LOST_NOTIFICATION_ID, notificationBuilder.build())
    }

    /**
     * A [Notification] shown when the [BackgroundService] triggered the picture capturing slowed down
     * event.
     *
     * @param context The context if the service used to show the [Notification]. It stays even
     * when the service is stopped as long as a unique id is used.
     */
    override fun showPictureCapturingDecreasedNotification(context: Context) {

        // Open Activity when the notification is clicked
        val onClickIntent = Intent(context, MainActivity::class.java)
        val onClickPendingIntent: PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                onClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notificationManager = context
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Validate.notNull(notificationManager)
        createNotificationChannelIfNotExists(
            context,
            NOTIFICATION_CHANNEL_ID_WARNING,
            context.getString(de.cyface.app.utils.R.string.notification_channel_name_warning),
            context.getString(de.cyface.app.utils.R.string.notification_channel_description_warning),
            NotificationManager.IMPORTANCE_HIGH,
            true,
            Color.RED,
            true,
        )
        val notificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(
            context,
            NOTIFICATION_CHANNEL_ID_WARNING
        ).setContentIntent(onClickPendingIntent)
            .setSmallIcon(R.drawable.ic_logo_white)
            .setContentTitle(
                context.getString(de.cyface.camera_service.R.string.notification_title_picture_capturing_decreased)
            )
            .setContentText(
                context.getString(de.cyface.camera_service.R.string.notification_text_picture_capturing_decreased)
            )
            .setOngoing(false).setWhen(System.currentTimeMillis()).setPriority(2)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(500, 1500))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        notificationManager.notify(
            PICTURE_CAPTURING_DECREASED_NOTIFICATION_ID,
            notificationBuilder.build()
        )
    }

    override fun buildCapturingNotification(
        context: BackgroundService,
        isVideoModeRequested: Boolean
    ): Notification {
        Validate.notNull(context, "No context provided!")
        val channelId: String = NOTIFICATION_CHANNEL_ID_RUNNING

        // Open Activity when the notification is clicked
        val onClickIntent = Intent(context, MainActivity::class.java)
        val onClickPendingIntent: PendingIntent =
            PendingIntent.getActivity(
                context, 0, onClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        createNotificationChannelIfNotExists(
            context, channelId, "Cyface",
            context.getString(de.cyface.datacapturing.R.string.notification_channel_description_running),
            NotificationManager.IMPORTANCE_LOW, false, Color.GREEN, false
        )
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getText(de.cyface.camera_service.R.string.camera_capturing_active))
            .setContentIntent(onClickPendingIntent).setWhen(System.currentTimeMillis())
            .setOngoing(true)
            .setAutoCancel(false)

        builder.setSmallIcon(if (isVideoModeRequested) R.drawable.ic_videocam else R.drawable.ic_photo_camera)
        return builder.build()
    }

    companion object {

        /**
         * Since Android 8 it is necessary to create a new notification channel for a foreground
         * service notification. To save system resources this should only happen if the channel
         * does not exist. This method does just that.
         *
         * @param context The Android `Context` to use to create the notification channel.
         * @param channelId The identifier of the created or existing channel.
         */
        private fun createNotificationChannelIfNotExists(
            context: Context,
            channelId: String,
            channelName: String,
            description: String,
            importance: Int,
            enableLights: Boolean,
            lightColor: Int,
            enableVibration: Boolean,
        ) {
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
