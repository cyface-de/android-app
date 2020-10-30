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
/**
 * This package contains classes to access the data storage used for road measurements.
 * <p>
 * Measurements are stored and managed by the Cyface SDK, which is included with this app.
 * If the app needs to show information about these measurements, these classes provide convenient accessors.
 * <p>
 * Measurements are mainly stored on disk in the apps local storage area, under a folder called measurements.
 * There are four sub folders for open, finished, synchronized and corrupted measurements.
 * Some metadata is also stored in the apps local SQLite database.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 3.0.0
 */
package de.cyface.app.storage;
