/*
 * Copyright 2017-2023 Cyface GmbH
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
package de.cyface.app.digural.utils

/**
 * This class holds all constants required by multiple classes. This avoids unnecessary dependencies
 * which would only be needed to access those constants.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.4.0
 * @since 1.0.0
 */
object Constants {
    const val PACKAGE = "de.cyface.app.digural"
    const val TAG = PACKAGE // This can be references as default TAG for this app
    const val SUPPORT_EMAIL = "support@cyface.de"

    /**
     * must be different from other SDK using apps
     */
    const val AUTHORITY = "de.cyface.app.digural.provider"
    const val ACCOUNT_TYPE = "de.cyface.app.digural"
}