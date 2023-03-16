package de.cyface.app.r4r.ui.capturing

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.persistence.model.Track
import de.cyface.persistence.repository.MeasurementRepository
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
class CapturingViewModel(private val repository: MeasurementRepository) : ViewModel() {

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
    //private val _measurement = MutableLiveData<Measurement?>()
    var measurement: LiveData<Measurement?> = Transformations.switchMap(measurementId) { id ->
        if (id != null) {
            observeMeasurementById(id)
        } else {
            observeMeasurementById(-1)
        }
    }

    /**
     * The cached, latest location or `null` if capturing is inactive.
     */
    private val _location = MutableLiveData<ParcelableGeoLocation?>()

    // Expose the data state to the UI layer
    val location: LiveData<ParcelableGeoLocation?> = _location

    /**
     * Caching the [Track]s of the current [Measurement], so we do not need to ask the database each time
     * the updated track is requested. This is `null` if there is no unfinished measurement.
     */
    var currentMeasurementsTracks: ArrayList<Track>? = null

    fun setMeasurementId(id: Long?) {
        _measurementId.postValue(id)
    }

    private fun observeMeasurementById(id: Long): LiveData<Measurement?> {
        return repository.observeById(id).asLiveData()
    }

    /**
     * Launch a new coroutine to update the data in a non-blocking way.
     *
     * Encapsulates the `Repository` interface from the UI.
     */
    fun update(measurement: Measurement) = viewModelScope.launch {
        repository.update(measurement)
    }

    fun setLocation(location: ParcelableGeoLocation?) {
        _location.value = location
    }

    fun initializeCurrentMeasurementsTracks() {
        currentMeasurementsTracks = ArrayList()
    }

    fun resetCurrentMeasurementsTracks() {
        currentMeasurementsTracks = null
    }

    fun addToCurrentMeasurementsTracks(track: Track) {
        (currentMeasurementsTracks as ArrayList<Track>).add(track)
    }
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
class CapturingViewModelFactory(private val repository: MeasurementRepository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CapturingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CapturingViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}