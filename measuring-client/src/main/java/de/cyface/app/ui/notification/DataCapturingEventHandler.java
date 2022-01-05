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
package de.cyface.app.ui.notification;

import static android.content.Context.NOTIFICATION_SERVICE;
import static de.cyface.app.utils.Constants.NOTIFICATION_CHANNEL_ID_RUNNING;
import static de.cyface.app.utils.Constants.NOTIFICATION_CHANNEL_ID_WARNING;
import static de.cyface.app.utils.Constants.SPACE_WARNING_NOTIFICATION_ID;
import static de.cyface.app.utils.Constants.TAG;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Parcel;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import de.cyface.app.R;
import de.cyface.app.ui.MainActivity;
import de.cyface.datacapturing.EventHandlingStrategy;
import de.cyface.datacapturing.backend.DataCapturingBackgroundService;
import de.cyface.utils.Validate;

/**
 * A {@link EventHandlingStrategy} to respond to specified events triggered by the
 * {@link DataCapturingBackgroundService}.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 3.0.0
 * @since 2.5.0
 */
public class DataCapturingEventHandler implements EventHandlingStrategy {

    /**
     * The <code>Parcelable</code> creator as required by the Android Parcelable specification.
     */
    public static final Creator<DataCapturingEventHandler> CREATOR = new Creator<>() {
        @Override
        public DataCapturingEventHandler createFromParcel(final Parcel in) {
            return new DataCapturingEventHandler(in);
        }

        @Override
        public DataCapturingEventHandler[] newArray(final int size) {
            return new DataCapturingEventHandler[size];
        }
    };

    public DataCapturingEventHandler() {
        // Nothing to do here.
    }

    /**
     * Constructor as required by <code>Parcelable</code> implementation.
     *
     * @param in A <code>Parcel</code> that is a serialized version of a <code>DataCapturingEventHandler</code>.
     */
    private DataCapturingEventHandler(@SuppressWarnings({"unused", "RedundantSuppression"}) final @NonNull Parcel in) {
        // Nothing to do here.
    }

    /**
     * Since Android 8 it is necessary to create a new notification channel for a foreground service notification. To
     * save system resources this should only happen if the channel does not exist. This method does just that.
     *
     * @param context The Android <code>Context</code> to use to create the notification channel.
     * @param channelId The identifier of the created or existing channel.
     */
    private static void createNotificationChannelIfNotExists(final @NonNull Context context,
            final @NonNull String channelId, final @NonNull String channelName, final @NonNull String description,
            final int importance, final boolean enableLights, final int lightColor, final boolean enableVibration) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return;
        }

        final NotificationManager manager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        Validate.notNull(manager, "Manager for service notifications not available.");

        if (manager.getNotificationChannel(channelId) == null) {
            final NotificationChannel channel = new NotificationChannel(channelId, channelName, importance);
            channel.setDescription(description);
            channel.enableLights(enableLights);
            channel.enableVibration(enableVibration);
            channel.setLightColor(lightColor);

            manager.createNotificationChannel(channel);
        }

    }

    @Override
    public void handleSpaceWarning(@NonNull final DataCapturingBackgroundService dataCapturingBackgroundService) {

        showSpaceWarningNotification(dataCapturingBackgroundService.getApplicationContext());
        dataCapturingBackgroundService.stopSelf();
        dataCapturingBackgroundService.sendStoppedItselfMessage();
        Log.i(TAG, "Low space event triggered - DCS capturing stopped.");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
    }

    /**
     * A {@link Notification} shown when the {@link DataCapturingBackgroundService} triggered the low space event.
     *
     * @param context The context if the service used to show the {@link Notification}. It stays even
     *            when the service is stopped as long as a unique id is used.
     */
    private void showSpaceWarningNotification(final Context context) {
        final Intent onClickIntent = new Intent(context, MainActivity.class);
        final PendingIntent onClickPendingIntent = PendingIntent.getActivity(context, 0, onClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        final NotificationManager notificationManager = (NotificationManager)context
                .getSystemService(NOTIFICATION_SERVICE);
        Validate.notNull(notificationManager);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            createNotificationChannelIfNotExists(context, NOTIFICATION_CHANNEL_ID_WARNING,
                    context.getString(R.string.notification_channel_name_warning),
                    context.getString(R.string.notification_channel_description_warning),
                    NotificationManager.IMPORTANCE_HIGH, true, Color.RED, true);
        }
        final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context,
                NOTIFICATION_CHANNEL_ID_WARNING).setContentIntent(onClickPendingIntent)
                        .setSmallIcon(R.drawable.ic_logo_only_c)
                        .setContentTitle(context.getString(R.string.notification_title_capturing_stopped))
                        .setContentText(context.getString(R.string.error_message_capturing_canceled_no_space))
                        .setOngoing(false).setWhen(System.currentTimeMillis()).setPriority(2).setAutoCancel(true)
                        .setVibrate(new long[] {500, 1500})
                        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
        notificationManager.notify(SPACE_WARNING_NOTIFICATION_ID, notificationBuilder.build());
    }

    @Override
    @NonNull
    public Notification buildCapturingNotification(@NonNull final DataCapturingBackgroundService context) {
        Validate.notNull(context, "No context provided!");
        final String channelId = NOTIFICATION_CHANNEL_ID_RUNNING;

        // Open Activity when the notification is clicked
        final Intent onClickIntent = new Intent(context, MainActivity.class);
        final PendingIntent onClickPendingIntent = PendingIntent.getActivity(context, 0, onClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            createNotificationChannelIfNotExists(context, channelId, "Cyface",
                    context.getString(de.cyface.datacapturing.R.string.notification_channel_description_running),
                    NotificationManager.IMPORTANCE_LOW, false, Color.GREEN, false);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setContentTitle(context.getText(de.cyface.datacapturing.R.string.capturing_active))
                .setContentIntent(onClickPendingIntent).setWhen(System.currentTimeMillis()).setOngoing(true)
                .setAutoCancel(false);

        // APIs < 21 crash when replacing a notification icon with a vector icon
        // What works is: use a png to replace the icon on API < 21 or to reuse the same vector icon
        // The most elegant solution seems to be to have PNGs for the icons and the vector xml in drawable-anydpi-v21,
        // see https://stackoverflow.com/a/37334176/5815054
        builder.setSmallIcon(R.drawable.ic_logo_only_c);
        return builder.build();
    }
}
