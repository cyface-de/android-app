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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.result.ActivityResult
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.cyface.camera_service.background.TriggerMode
import de.cyface.camera_service.settings.AnonymizationSettings
import de.cyface.camera_service.settings.CameraSettings
import de.cyface.camera_service.settings.FileSelection
import de.cyface.utils.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
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
 * @version 3.0.0
 * @since 3.4.0
 * @property appSettings The settings used by both, UIs and libraries.
 * @property cameraSettings The settings used by the camera library.
 * @property customSettings The UI-specific settings.
 */
class SettingsViewModel(
    private val appSettings: AppSettings,
    internal val cameraSettings: CameraSettings,
    private val customSettings: CustomSettings
) : ViewModel() {

    /** app settings **/
    private val _centerMap = MutableLiveData<Boolean>()
    private val _upload = MutableLiveData<Boolean>()
    private val _sensorFrequency = MutableLiveData<Int>()

    /** camera settings  **/
    private val _cameraEnabled = MutableLiveData<Boolean>()
    private val _videoMode = MutableLiveData<Boolean>()
    private val _rawMode = MutableLiveData<Boolean>()
    private val _triggerMode = MutableLiveData<TriggerMode>()
    private val _triggeringDistance = MutableLiveData<Float>()
    private val _triggeringTime = MutableLiveData<Int>()
    private val _staticFocus = MutableLiveData<Boolean>()
    private val _staticFocusDistance = MutableLiveData<Float>()
    private val _staticExposure = MutableLiveData<Boolean>()
    private val _staticExposureTime = MutableLiveData<Long>()
    private val _staticExposureValue = MutableLiveData<Int>()

    /** custom settings **/
    private val _diguralServerUrl = MutableLiveData<URL>()
    private val _anonModel = MutableLiveData<AnonymizationSettings>()

    /**
     * {@code True} if the camera allows to control the sensors (focus, exposure, etc.) manually.
     */
    var manualSensorSupported = false

    /** app settings **/
    val centerMap: LiveData<Boolean> = appSettings.centerMapFlow.asLiveData()
    val uploadEnabled: LiveData<Boolean> = appSettings.uploadEnabledFlow.asLiveData()
    val sensorFrequency: LiveData<Int> = appSettings.sensorFrequencyFlow.asLiveData()

    /** camera settings  **/
    val cameraEnabled: LiveData<Boolean> = cameraSettings.cameraEnabledFlow.asLiveData()
    val videoMode: LiveData<Boolean> = cameraSettings.videoModeFlow.asLiveData()
    val rawMode: LiveData<Boolean> = cameraSettings.rawModeFlow.asLiveData()
    val triggerMode: LiveData<TriggerMode> = cameraSettings.triggerMode.asLiveData()
    val triggeringDistance: LiveData<Float> = cameraSettings.triggeringDistanceFlow.asLiveData()
    val triggeringTime: LiveData<Int> = cameraSettings.triggeringTimeFlow.asLiveData()
    val staticFocus: LiveData<Boolean> = cameraSettings.staticFocusFlow.asLiveData()
    val staticFocusDistance: LiveData<Float> = cameraSettings.staticFocusDistanceFlow.asLiveData()
    val staticExposure: LiveData<Boolean> = cameraSettings.staticExposureFlow.asLiveData()
    val staticExposureTime: LiveData<Long> = cameraSettings.staticExposureTimeFlow.asLiveData()
    val staticExposureValue: LiveData<Int> = cameraSettings.staticExposureValueFlow.asLiveData()

    /** custom settings **/
    val diguralServerUrl: LiveData<URL> = customSettings.diguralUrlFlow.asLiveData()
    val diguralAnonModel: LiveData<AnonymizationSettings> = cameraSettings.anonModelFlow.asLiveData()

    init {
        viewModelScope.launch {
            /** app settings **/
            _centerMap.value = appSettings.centerMapFlow.first()
            _upload.value = appSettings.uploadEnabledFlow.first()
            _sensorFrequency.value = appSettings.sensorFrequencyFlow.first()
            /** camera settings  **/
            _cameraEnabled.value = cameraSettings.cameraEnabledFlow.first()
            _videoMode.value = cameraSettings.videoModeFlow.first()
            _rawMode.value = cameraSettings.rawModeFlow.first()
            _triggerMode.value = cameraSettings.triggerMode.first()
            _triggeringDistance.value = cameraSettings.triggeringDistanceFlow.first()
            _triggeringTime.value = cameraSettings.triggeringTimeFlow.first()
            _staticFocus.value = cameraSettings.staticFocusFlow.first()
            _staticFocusDistance.value = cameraSettings.staticFocusDistanceFlow.first()
            _staticExposure.value = cameraSettings.staticExposureFlow.first()
            _staticExposureTime.value = cameraSettings.staticExposureTimeFlow.first()
            _staticExposureValue.value = cameraSettings.staticExposureValueFlow.first()
            /** custom settings **/
            _diguralServerUrl.value = customSettings.diguralUrlFlow.first()
            _anonModel.value = cameraSettings.anonModelFlow.first()
        }
    }

    /** app settings **/
    suspend fun setCenterMap(centerMap: Boolean) {
        appSettings.setCenterMap(centerMap)
    }

    suspend fun setUpload(upload: Boolean) {
        appSettings.setUploadEnabled(upload)
    }

    suspend fun setSensorFrequency(sensorFrequency: Int) {
        appSettings.setSensorFrequency(sensorFrequency)
    }

    /** camera settings **/
    suspend fun setCameraEnabled(cameraEnabled: Boolean) {
        cameraSettings.setCameraEnabled(cameraEnabled)
    }

    suspend fun setVideoMode(videoMode: Boolean) {
        cameraSettings.setVideoMode(videoMode)
    }

    suspend fun setRawMode(rawMode: Boolean) {
        cameraSettings.setRawMode(rawMode)
    }

    suspend fun setTriggerMode(triggerMode: TriggerMode) {
        cameraSettings.setTriggerMode(triggerMode)
    }

    suspend fun setTriggeringDistance(triggeringDistance: Float) {
        cameraSettings.setTriggeringDistance(triggeringDistance)
    }

    suspend fun setTriggeringTime(triggeringTime: Int) {
        cameraSettings.setTriggeringTime(triggeringTime)
    }

    suspend fun setStaticFocus(staticFocus: Boolean) {
        cameraSettings.setStaticFocus(staticFocus)
    }

    suspend fun setStaticFocusDistance(staticFocusDistance: Float) {
        cameraSettings.setStaticFocusDistance(staticFocusDistance)
    }

    suspend fun setStaticExposure(staticExposure: Boolean) {
        cameraSettings.setStaticExposure(staticExposure)
    }

    suspend fun setStaticExposureTime(staticExposureTime: Long) {
        cameraSettings.setStaticExposureTime(staticExposureTime)
    }

    suspend fun setStaticExposureValue(staticExposureValue: Int) {
        cameraSettings.setStaticExposureValue(staticExposureValue)
    }

    suspend fun setAnonModel(anonModel: AnonymizationSettings) {
        cameraSettings.setAnonModel(anonModel)
    }

    /** custom settings **/
    suspend fun setDiguralServerUrl(address: URL) {
        customSettings.setDiguralUrl(address)
    }

    fun modelFilePicked(result: ActivityResult, context: Context) {
        // Check if result is ok
        if (result.resultCode == Activity.RESULT_OK) {
            // Get the intent from the result
            val data: Intent? = result.data
            // Check if intent did contain data
            if (data != null) {
                val uri: Uri? = data.data
                if (uri != null) {
                    // Set the selected file as the selected model file.
                    viewModelScope.launch(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            FileOutputStream(File(context.getExternalFilesDir(null),"anon_model")).use { outputStream ->
                                val buffer = ByteArray(1024)
                                while (inputStream.read(buffer) != -1) {
                                    outputStream.write(buffer)
                                }
                            }
                        }

                        setAnonModel(FileSelection(uri.getName(context)))
                    }
                }
            }
        }
    }
}

