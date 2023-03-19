package de.cyface.app.r4r.ui.trips

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.repository.MeasurementRepository

/**
 * This is the [ViewModel] for the [TripsFragment].
 *
 * @see de.cyface.app.r4r.ui.capturing.CapturingViewModel
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.2.0
 */
class TripsViewModel(private val repository: MeasurementRepository) : ViewModel() {

    /**
     * Caching the `Repository`'s `Flow` data as [LiveData] to separate the `Repository` from the UI.
     *
     * Additionally, [LiveData] is lifecycle-aware and only observes changes while the UI is active.
     */
    val measurements: LiveData<List<Measurement>> = repository.observeAllCompleted().asLiveData()

    private val _text = MutableLiveData<String>().apply {
        value = "${if (measurements.value == null) "N/A" else measurements.value!!.size} trips found"
    }

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
class TripsViewModelFactory(private val repository: MeasurementRepository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TripsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TripsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}