package de.cyface.app.r4r.ui.capturing

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.cyface.persistence.model.Measurement
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
     * Caching the `Repository`'s `Flow` data as [LiveData] to separate the `Repository` from the UI.
     *
     * Additionally, [LiveData] is lifecycle-aware and only observes changes while the UI is active.
     */
    val measurement: LiveData<Measurement?> = repository.observeById(43L /* FIXME*/).asLiveData()

    /**
     * Launch a new coroutine to update the data in a non-blocking way.
     *
     * Encapsulates the `Repository` interface from the UI.
     */
    fun update(measurement: Measurement) = viewModelScope.launch {
        repository.update(measurement)
    }

    /**
     * The observed, current state of ***.
     */
    private val _text = MutableLiveData<String>().apply {
        value = "{live} ${if (measurement.value == null) "N/A" else measurement.value!!.distance} km"
    }

    // Expose the data state to the UI layer
    val text: LiveData<String> = _text
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