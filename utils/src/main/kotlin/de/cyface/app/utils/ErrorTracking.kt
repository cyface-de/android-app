/*
 * Copyright 2026 Cyface GmbH
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

import android.content.Context
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.protocol.User

/**
 * The error tracking with Sentry while the user is logged in, as in the web-app (CY-6870).
 *
 * The legal basis is described in the privacy policy, which the user accepts at the registration.
 * The apps start the error tracking after the login (and at the app start when the user is logged
 * in) and stop it at the logout. Before, the user opted in on the terms screen.
 *
 * `Sentry.capture*()` calls do nothing while the error tracking is stopped.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.26.0
 */
object ErrorTracking {
    /**
     * The DSN of the project `android-app` in the EU organization `cyface-eu` of Sentry (data
     * residency in the EU), used by all app variants. It identifies the project but is not a secret.
     */
    private const val DSN =
        "https://f8ff985885dd160b35076e64aaaf6c2b@o4511501184335872.ingest.de.sentry.io/4512174883602512"

    /**
     * The user name in paths like `users/<name>/...` (Cyface APIs, the user name is the email
     * address) or `files/<name>/...` (WebDAV of the Digural app).
     */
    private val USER_PATH = Regex("(/(?:users|files)/)[^/?#\\s\"']+")

    /**
     * An email address, also URL-encoded (`%40`), e.g. the account name in an error message.
     */
    private val EMAIL = Regex("[\\w.+-]+(?:@|%40)[\\w-]+(?:\\.[\\w-]+)+", RegexOption.IGNORE_CASE)

    /**
     * Starts the error tracking. Does nothing when it is already started.
     *
     * @param context The context to initialize Sentry with.
     */
    fun start(context: Context) {
        if (Sentry.isEnabled()) return
        SentryAndroid.init(context) { options ->
            options.dsn = DSN
            // No IP address and no device name
            options.isSendDefaultPii = false
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> scrubEvent(event) }
            options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ ->
                scrubBreadcrumb(breadcrumb)
            }
        }
    }

    /**
     * Stops the error tracking, e.g. at the logout.
     */
    fun stop() {
        Sentry.close()
    }

    /**
     * Replaces the user names in paths and all email addresses in a text.
     */
    fun scrubText(text: String): String {
        return EMAIL.replace(USER_PATH.replace(text, "\$1[filtered]"), "[email]")
    }

    /**
     * Removes personal data from an error event before it is sent.
     *
     * Keeps only the pseudonymous installation ID of the user context, which the privacy policy
     * names as "pseudonyme Gerätekennung".
     */
    fun scrubEvent(event: SentryEvent): SentryEvent {
        event.user = event.user?.id?.let { id -> User().also { it.id = id } }
        event.message?.let { message ->
            message.formatted = message.formatted?.let { scrubText(it) }
            message.message = message.message?.let { scrubText(it) }
        }
        event.exceptions?.forEach { exception ->
            exception.value = exception.value?.let { scrubText(it) }
        }
        event.request?.let { request ->
            request.url = request.url?.let { scrubText(it) }
            request.queryString = request.queryString?.let { scrubText(it) }
        }
        event.extras?.keys?.toList()?.forEach { key ->
            val value = event.getExtra(key)
            if (value is String) event.setExtra(key, scrubText(value))
        }
        return event
    }

    /**
     * Removes personal data from a breadcrumb, e.g. the URL of a request or a log message.
     */
    fun scrubBreadcrumb(breadcrumb: Breadcrumb): Breadcrumb {
        breadcrumb.message = breadcrumb.message?.let { scrubText(it) }
        breadcrumb.data.keys.toList().forEach { key ->
            val value = breadcrumb.getData(key)
            if (value is String) breadcrumb.setData(key, scrubText(value))
        }
        return breadcrumb
    }
}
