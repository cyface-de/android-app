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

import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Tests that [ErrorTracking] removes personal data before it is sent to Sentry.
 */
class ErrorTrackingTest {
    @Test
    fun testScrubText() {
        assertEquals(
            "https://cyface.de/api/v4/users/[filtered]/measurements?page=2",
            ErrorTracking.scrubText("https://cyface.de/api/v4/users/max@example.com/measurements?page=2")
        )
        assertEquals(
            "https://webdav.example.com/files/[filtered]/",
            ErrorTracking.scrubText("https://webdav.example.com/files/truck-7/")
        )
        assertEquals(
            "Account [email] not found",
            ErrorTracking.scrubText("Account max.muster@example.co.uk not found")
        )
        assertEquals("/measurements", ErrorTracking.scrubText("/measurements"))
    }

    @Test
    fun testScrubEvent() {
        val event = SentryEvent()
        event.user = User().also {
            it.id = "installation-id"
            it.email = "max@example.com"
            it.username = "max@example.com"
        }
        event.message = Message().also { it.formatted = "Upload failed for max@example.com" }
        event.setExtra("account", "max@example.com")

        ErrorTracking.scrubEvent(event)

        assertEquals("installation-id", event.user!!.id)
        assertEquals(null, event.user!!.email)
        assertEquals(null, event.user!!.username)
        assertFalse(event.message!!.formatted!!.contains("max"))
        assertEquals("[email]", event.getExtra("account"))
    }

    @Test
    fun testScrubBreadcrumb() {
        // The app uses the user ID in the path, e.g. to delete the account
        val url = "https://cyface.de/provider/api/v4/users/0b6c1f2e-8a3d-4c5e-9f71-2d4b6a8c0e13"
        val breadcrumb = Breadcrumb.http(url, "DELETE")

        ErrorTracking.scrubBreadcrumb(breadcrumb)

        assertEquals("https://cyface.de/provider/api/v4/users/[filtered]", breadcrumb.getData("url"))
    }
}
