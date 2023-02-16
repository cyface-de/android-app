package de.cyface.app.r4r.ui.capturing

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * This is the [ViewModel] for the capturing fragment.
 *
 * It holds the [LiveData] instances for the data types shown in the capturing fragment.
 * The capturing fragment is responsible for displaying the data from this [ViewModel].
 *
 * [LiveData] is not designed to handle async streams of data. Use Kotlin Flows with `asLiveData`:
 * https://developer.android.com/topic/libraries/architecture/livedata#livedata-in-architecture
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.2.0
 */
class CapturingViewModel : ViewModel() {

    // Create LiveData with a String
    private val _text = MutableLiveData<String>().apply {
        value = "This is capturing Fragment"
    }

    // Expose the data state to the UI layer
    val text: LiveData<String> = _text
}