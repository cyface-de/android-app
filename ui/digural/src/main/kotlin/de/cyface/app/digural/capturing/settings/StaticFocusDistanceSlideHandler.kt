/*
 * Copyright 2023-2025 Cyface GmbH
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
package de.cyface.app.digural.capturing.settings

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.android.material.slider.Slider
import de.cyface.app.digural.capturing.settings.SettingsFragment.Companion.TAG
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Handles UI changes of the 'slider' used to adjust the 'focus distance' setting.
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 3.2.0
 */
class StaticFocusDistanceSlideHandler(
    private val viewModel: SettingsViewModel
) : Slider.OnChangeListener {
    override fun onValueChange(slider: Slider, newValue: Float, fromUser: Boolean) {
        val roundedDistance = (newValue * 100).roundToInt() / 100f
        if (viewModel.staticFocusDistance.value != roundedDistance) {
            viewModel.viewModelScope.launch {
                viewModel.setStaticFocusDistance(roundedDistance)
            }
        }
    }
}