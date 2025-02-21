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
package de.cyface.app.digural.capturing

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import de.cyface.app.digural.utils.Constants.TAG
import de.cyface.persistence.model.GeoLocation
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.persistence.model.Track
import de.cyface.persistence.repository.EventRepository
import de.cyface.persistence.repository.MeasurementRepository
import io.sentry.Sentry

/**
 * This is the [ViewModel] for the [CapturingFragment].
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
 * @since 3.2.0
 */
class CapturingViewModel(
    private val repository: MeasurementRepository,
    eventRepository: EventRepository,
    private val isReportingEnabled: Boolean
) : ViewModel() {

    /**
     * The data state is exposed via [capturing] state to the UI layer.
     */
    private val _capturing = MutableLiveData<MeasurementStatus?>()

    /**
     * The cached capturing status or `null` until the status is retrieved asynchronously.
     */
    val capturing: LiveData<MeasurementStatus?> = _capturing

    private val _measurementId = MutableLiveData<Long?>()

    /**
     * The cached id of the currently active measurement or `null` if capturing is inactive.
     */
    val measurementId: LiveData<Long?> = _measurementId

    /**
     * The cached measurement from the `Repository` `Flow` data to separate the `Repository` from the UI.
     *
     * Additionally, [LiveData] is lifecycle-aware and only observes changes while the UI is active.
     */
    var measurement: LiveData<Measurement?> = measurementId.switchMap { id ->
        if (id != null) {
            repository.observeById(id).asLiveData()
        } else {
            repository.observeById(-1).asLiveData()
        }
    }

    private val _location = MutableLiveData<ParcelableGeoLocation?>()

    /**
     * The cached, latest location or `null` if capturing is inactive.
     */
    val location: LiveData<ParcelableGeoLocation?> = _location

    private val _tracks = MutableLiveData<ArrayList<Track>?>()

    /**
     * The cached [Track]s of the current [Measurement], so we do not need to ask the database each time
     * the updated track is requested. This is `null` if there is no unfinished measurement.
     */
    val tracks: LiveData<ArrayList<Track>?> = _tracks

    /**
     * @param status The cached capturing status or `null` until the status is retrieved asynchronously.
     */
    fun setCapturing(status: MeasurementStatus?) {
        _capturing.postValue(status)
    }

    /**
     * @param id The cached id of the currently active measurement or `null` if capturing is inactive.
     */
    fun setMeasurementId(id: Long?) {
        _measurementId.postValue(id)
    }

    /**
     * Launch a new coroutine to update the data in a non-blocking way. [RFR-341]
     *
     * Encapsulates the `Repository` interface from the UI.
     */
    /*fun update(measurement: Measurement) = viewModelScope.launch {
        repository.update(measurement)
    }*/

    /**
     * @param location The cached, latest location or `null` if capturing is inactive.
     */
    fun setLocation(location: ParcelableGeoLocation?) {
        _location.postValue(location)
    }

    /**
     * @param tracks The cached [Track]s of the current [Measurement], so we do not need to ask the database each time
     * the updated track is requested. This is `null` if there is no unfinished measurement.
     */
    fun setTracks(tracks: List<Track>?) {
        _tracks.postValue(
            if (tracks == null) null else if (tracks.isEmpty()) arrayListOf() else tracks as ArrayList<Track>
        )
    }

    /**
     * @param track Adds a sub-track to the cached [Track]s of the current [Measurement].
     */
    fun addTrack(track: Track) {
        _tracks.value!!.add(track)
    }

    /**
     * @param location Adds a location to the latest sub-track of the [Track]s of the current [Measurement].
     */
    fun addToTrack(location: ParcelableGeoLocation) {
        if (_tracks.value == null) {
            Log.i(TAG, "addToTrack: ignoring location, tracking is inactive")
            // Collect metrics about this in Sentry, to see if this happens a lot
            if (isReportingEnabled) {
                Sentry.captureMessage("addToTrack: ignoring location, tracking is inactive")
            }
            return
        }
        if (!location.isValid) {
            Log.d(TAG, "addToTrack: ignoring invalid point")
            return
        }
        if (_tracks.value!!.isEmpty()) {
            Log.d(TAG, "addToTrack: Loaded track is empty, adding empty sub track")
            _tracks.postValue(
                arrayListOf(
                    Track(
                        mutableListOf(GeoLocation(location, measurementId.value!!)),
                        mutableListOf()
                    )
                )
            )
        } else {
            val tracks = _tracks.value
            tracks!![_tracks.value!!.size - 1].addLocation(
                GeoLocation(
                    location,
                    measurementId.value!!
                )
            )
            _tracks.postValue(tracks)
        }
    }

    /*
    var events: LiveData<List<Event>?> = measurementId.switchMap { measurementId ->
        if (measurementId != null) {
            eventRepository.observeAllByMeasurementId(measurementId).asLiveData()
        } else {
            eventRepository.observeAllByMeasurementId(-1).asLiveData()
        }
    }

    val tracksWithEvents = tracks.combineWith(events) { tracks, events ->
        "${tracks} ${events}"

    fun <T, K, R> LiveData<T>.combineWith(
    liveData: LiveData<K>,
    block: (T?, K?) -> R
    ): LiveData<R> {
    val result = MediatorLiveData<R>()
    result.addSource(this) {
    result.value = block(this.value, liveData.value)
    }
    result.addSource(liveData) {
    result.value = block(this.value, liveData.value)
    }
    return result
    }*/
}

/**
 * Factory which creates the [ViewModel] with the required dependencies.
 *
 * Survives configuration changes and returns the right instance after Activity recreation.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.2.0
 */
class CapturingViewModelFactory(
    private val repository: MeasurementRepository,
    private val eventRepository: EventRepository,
    private val isReportingEnabled: Boolean
) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CapturingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CapturingViewModel(repository, eventRepository, isReportingEnabled) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}