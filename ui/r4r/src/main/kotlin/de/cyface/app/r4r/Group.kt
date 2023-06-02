/*
 * Copyright 2023 Cyface GmbH
 *
 * This file is part of the Cyface SDK for Android.
 *
 * The Cyface SDK for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface SDK for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface SDK for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.app.r4r

/**
 * The groups the user can choose from during registration.
 *
 * This way we can enable group-specific achievements like vouchers.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.3.0
 * @property databaseIdentifier The [String] which represents the enumeration value in the database.
 * @property spinnerText The [String] which is shown in the `Spinner`.
 */
enum class Group(private val databaseIdentifier: String, val spinnerText: String) {
    // Keep the spinnerText in sync with `res/values/groups.xml`
    // Don't change the databaseIdentifier.
    @Suppress("SpellCheckingInspection")
    NONE_GERMAN("guest", "Kommune auswählen"),
    NONE_ENGLISH("guest", "Choose municipality"),

    @Suppress("SpellCheckingInspection")
    KOETHEN("koethen", "Köthen"),
    SCHKEUDITZ("schkeuditz", "Schkeuditz");

    companion object {
        private val spinnerTextValues = Group.values().associateBy(Group::spinnerText)

        /**
         * Returns the [Group] from the selected spinner text value.
         *
         * @param spinnerText The selected spinner text.
         */
        fun fromSpinnerText(spinnerText: String) = spinnerTextValues[spinnerText]
    }
}