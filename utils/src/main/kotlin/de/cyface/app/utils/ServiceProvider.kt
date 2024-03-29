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

import de.cyface.app.utils.capturing.settings.UiSettings
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.synchronization.Auth
import de.cyface.utils.settings.AppSettings

/**
 * Interface which defines the dependencies implemented by the `MainActivity` to be accessible from
 * the `Fragments`.
 *
 * @author Armin Schnabel
 * @version 1.2.0
 * @since 7.5.0
 */
interface ServiceProvider {
    val capturing: CyfaceDataCapturingService
    val auth: Auth
    val appSettings: AppSettings
    val uiSettings: UiSettings
}