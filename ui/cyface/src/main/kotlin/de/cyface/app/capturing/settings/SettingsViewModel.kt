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
package de.cyface.app.capturing.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.cyface.utils.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * This is the [ViewModel] for the [SettingsFragment].
 *
 * It holds the UI data/state for that UI element in a lifecycle-aware way, surviving configuration changes.
 *
 * It acts as a communicator between the data layer's `Repository` and the UI layer's UI elements.
 *
 * *Attention*:
 * - Don't keep references to a `Context` that has a shorter lifecycle than the [ViewModel].
 *   https://developer.android.com/codelabs/android-room-with-a-view-kotlin#9
 * - [ViewModel]s don't survive when the app's process is killed in the background.
 *   UI data which needs to survive this, use "Saved State module for ViewModels":
 *   https://developer.android.com/topic/libraries/architecture/viewmodel-savedstate
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.4.0
 * @property appSettings The settings used by both, UIs and libraries.
 */
class SettingsViewModel(private val appSettings: AppSettings) : ViewModel() {

    private val _centerMap = MutableLiveData<Boolean>()
    private val _upload = MutableLiveData<Boolean>()

    init {
        runBlocking {
            _centerMap.value = appSettings.centerMapFlow.first()
            _upload.value = appSettings.uploadEnabledFlow.first()
        }
    }

    val centerMap: LiveData<Boolean> = _centerMap
    val upload: LiveData<Boolean> = _upload

    fun setCenterMap(centerMap: Boolean) {
        viewModelScope.launch { appSettings.setCenterMap(centerMap) }
        _centerMap.postValue(centerMap)
    }

    fun setUpload(upload: Boolean) {
        viewModelScope.launch { appSettings.setUploadEnabled(upload) }
        _upload.postValue(upload)
    }
}

