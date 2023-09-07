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
package de.cyface.app.digural.capturing.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.cyface.camera_service.settings.CameraSettings
import de.cyface.utils.AppPreferences
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.URL

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
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 3.4.0
 * @param appPreferences Persistence storage of the app preferences.
 * @param cameraSettings Persistence storage of the camera preferences.
 * @param customSettings Persistence storage of the ui custom preferences.
 */
class SettingsViewModel(
    private val appPreferences: AppPreferences,
    private val cameraSettings: CameraSettings,
    private val customSettings: CustomSettings
) : ViewModel() {

    private val _centerMap = MutableLiveData<Boolean>()
    private val _upload = MutableLiveData<Boolean>()
    private val _sensorFrequency = MutableLiveData<Int>()

    /** camera settings  **/
    private val _cameraEnabled = MutableLiveData<Boolean>()
    private val _videoMode = MutableLiveData<Boolean>()
    private val _rawMode = MutableLiveData<Boolean>()
    private val _distanceBasedTriggering = MutableLiveData<Boolean>()
    private val _triggeringDistance = MutableLiveData<Float>()
    private val _staticFocus = MutableLiveData<Boolean>()
    private val _staticFocusDistance = MutableLiveData<Float>()
    private val _staticExposure = MutableLiveData<Boolean>()
    private val _staticExposureTime = MutableLiveData<Long>()
    private val _staticExposureValue = MutableLiveData<Int>()

    /** custom settings **/
    private val _diguralServerUrl = MutableLiveData<URL>()

    /**
     * {@code True} if the camera allows to control the sensors (focus, exposure, etc.) manually.
     */
    var manualSensorSupported = false

    init {
        _centerMap.value = appPreferences.getCenterMap()
        _upload.value = appPreferences.getUpload()
        _sensorFrequency.value = appPreferences.getSensorFrequency()
        runBlocking { // FIXME
            /** camera settings  **/
            _cameraEnabled.value = cameraSettings.cameraEnabledFlow.first()
            _videoMode.value = cameraSettings.videoModeFlow.first()
            _rawMode.value = cameraSettings.rawModeFlow.first()
            _distanceBasedTriggering.value = cameraSettings.distanceBasedTriggeringFlow.first()
            _triggeringDistance.value = cameraSettings.triggeringDistanceFlow.first()
            _staticFocus.value = cameraSettings.staticFocusFlow.first()
            _staticFocusDistance.value = cameraSettings.staticFocusDistanceFlow.first()
            _staticExposure.value = cameraSettings.staticExposureFlow.first()
            _staticExposureTime.value = cameraSettings.staticExposureTimeFlow.first()
            _staticExposureValue.value = cameraSettings.staticExposureValueFlow.first()
            /** custom settings **/
            _diguralServerUrl.value = customSettings.diguralUrlFlow.first()
        }
    }

    val centerMap: LiveData<Boolean> = _centerMap
    val upload: LiveData<Boolean> = _upload
    val sensorFrequency: LiveData<Int> = _sensorFrequency

    /** camera settings  **/
    val cameraEnabled: LiveData<Boolean> = _cameraEnabled
    val videoMode: LiveData<Boolean> = _videoMode
    val rawMode: LiveData<Boolean> = _rawMode
    val distanceBasedTriggering: LiveData<Boolean> = _distanceBasedTriggering
    val triggeringDistance: LiveData<Float> = _triggeringDistance
    val staticFocus: LiveData<Boolean> = _staticFocus
    val staticFocusDistance: LiveData<Float> = _staticFocusDistance
    val staticExposure: LiveData<Boolean> = _staticExposure
    val staticExposureTime: LiveData<Long> = _staticExposureTime
    val staticExposureValue: LiveData<Int> = _staticExposureValue

    /** custom settings **/
    val diguralServerUrl: LiveData<URL> = _diguralServerUrl

    fun setCenterMap(centerMap: Boolean) {
        appPreferences.saveCenterMap(centerMap)
        _centerMap.postValue(centerMap)
    }

    fun setUpload(upload: Boolean) {
        appPreferences.saveUpload(upload)
        _upload.postValue(upload)
    }

    fun setSensorFrequency(sensorFrequency: Int) {
        appPreferences.saveSensorFrequency(sensorFrequency)
        _sensorFrequency.postValue(sensorFrequency)
    }

    /** camera settings  **/
    fun setCameraEnabled(cameraEnabled: Boolean) {
        GlobalScope.launch { cameraSettings.setCameraEnabled(cameraEnabled) } // FIXME
        _cameraEnabled.postValue(cameraEnabled)
    }

    fun setVideoMode(videoMode: Boolean) {
        GlobalScope.launch { cameraSettings.setVideoMode(videoMode) } // FIXME
        _videoMode.postValue(videoMode)
    }

    fun setRawMode(rawMode: Boolean) {
        GlobalScope.launch { cameraSettings.setRawMode(rawMode) } // FIXME
        _rawMode.postValue(rawMode)
    }

    fun setDistanceBasedTriggering(distanceBasedTriggering: Boolean) {
        GlobalScope.launch { cameraSettings.setDistanceBasedTriggering(distanceBasedTriggering) } // FIXME
        _distanceBasedTriggering.postValue(distanceBasedTriggering)
    }

    fun setTriggeringDistance(triggeringDistance: Float) {
        GlobalScope.launch { cameraSettings.setTriggeringDistance(triggeringDistance) } // FIXME
        _triggeringDistance.postValue(triggeringDistance)
    }

    fun setStaticFocus(staticFocus: Boolean) {
        GlobalScope.launch { cameraSettings.setStaticFocus(staticFocus) } // FIXME
        _staticFocus.postValue(staticFocus)
    }

    fun setStaticFocusDistance(staticFocusDistance: Float) {
        GlobalScope.launch { cameraSettings.setStaticFocusDistance(staticFocusDistance) } // FIXME
        _staticFocusDistance.postValue(staticFocusDistance)
    }

    fun setStaticExposure(staticExposure: Boolean) {
        GlobalScope.launch { cameraSettings.setStaticExposure(staticExposure) } // FIXME
        _staticExposure.postValue(staticExposure)
    }

    fun setStaticExposureTime(staticExposureTime: Long) {
        GlobalScope.launch { cameraSettings.setStaticExposureTime(staticExposureTime) } // FIXME
        _staticExposureTime.postValue(staticExposureTime)
    }

    fun setStaticExposureValue(staticExposureValue: Int) {
        GlobalScope.launch { cameraSettings.setStaticExposureValue(staticExposureValue) } // FIXME
        _staticExposureValue.postValue(staticExposureValue)
    }

    fun setDiguralServerUrl(address: URL) {
        GlobalScope.launch { customSettings.setDiguralUrl(address) } // FIXME
        _diguralServerUrl.postValue(address) // FIXME: race condition with line above?
    }
}

