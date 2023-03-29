package de.cyface.app.r4r.ui.capturing

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.cyface.app.r4r.utils.Constants.TAG
import de.cyface.persistence.model.Event
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.persistence.model.Track
import de.cyface.persistence.repository.EventRepository
import de.cyface.persistence.repository.MeasurementRepository
import de.cyface.utils.Validate
import kotlinx.coroutines.launch

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
    eventRepository: EventRepository
) : ViewModel() {

    /**
     * The cached capturing status or `null` until the status is retrieved asynchronously.
     */
    private val _capturing = MutableLiveData<MeasurementStatus?>()

    val capturing: LiveData<MeasurementStatus?> = _capturing

    /**
     * The cached id of the currently active measurement or `null` if capturing is inactive.
     */
    private val _measurementId = MutableLiveData<Long?>()

    val measurementId: LiveData<Long?> = _measurementId

    /**
     * Caching the `Repository`'s `Flow` data as [LiveData] to separate the `Repository` from the UI.
     *
     * Additionally, [LiveData] is lifecycle-aware and only observes changes while the UI is active.
     */
    var measurement: LiveData<Measurement?> = Transformations.switchMap(measurementId) { id ->
        if (id != null) {
            repository.observeById(id).asLiveData()
        } else {
            repository.observeById(-1).asLiveData()
        }
    }

    var events: LiveData<List<Event>?> = Transformations.switchMap(measurementId) { measurementId ->
        if (measurementId != null) {
            eventRepository.observeAllByMeasurementId(measurementId).asLiveData()
        } else {
            eventRepository.observeAllByMeasurementId(-1).asLiveData()
        }
    }

    /**
     * The cached, latest location or `null` if capturing is inactive.
     */
    private val _location = MutableLiveData<ParcelableGeoLocation?>()

    // Expose the data state to the UI layer
    val location: LiveData<ParcelableGeoLocation?> = _location

    private val _tracks = MutableLiveData<ArrayList<Track>?>()

    /**
     * Caching the [Track]s of the current [Measurement], so we do not need to ask the database each time
     * the updated track is requested. This is `null` if there is no unfinished measurement.
     */
    val tracks: LiveData<ArrayList<Track>?> = _tracks

    /*val title = tracks.combineWith(events) { tracks, events ->
        "${tracks.job} ${events.name}"
    }*/

    fun setCapturing(status: MeasurementStatus?) {
        _capturing.postValue(status)
    }

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

    fun setLocation(location: ParcelableGeoLocation?) {
        _location.postValue(location)
    }

    fun setTracks(tracks: ArrayList<Track>?) {
        _tracks.postValue(tracks)
    }

    fun addTrack(track: Track) {
        _tracks.value!!.add(track)
    }

    fun addToTrack(location: ParcelableGeoLocation) {
        Validate.notNull(_tracks.value, "onNewGeoLocation - cached track is null")
        if (!location.isValid) {
            Log.d(TAG, "addToTrack: ignoring invalid point")
            return
        }
        if (_tracks.value!!.isEmpty()) {
            Log.d(TAG, "addToTrack: Loaded track is empty, adding empty sub track")
            _tracks.postValue(arrayListOf(Track(mutableListOf(location), mutableListOf())))
        } else {
            val tracks = _tracks.value
            tracks!![_tracks.value!!.size - 1].addLocation(location)
            _tracks.postValue(tracks)
        }
    }

    /**
     * TODO
     * /
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
    private val eventRepository: EventRepository
) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CapturingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CapturingViewModel(repository, eventRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}