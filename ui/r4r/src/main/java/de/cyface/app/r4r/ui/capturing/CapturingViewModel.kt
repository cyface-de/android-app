package de.cyface.app.r4r.ui.capturing

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class CapturingViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is capturing Fragment"
    }
    val text: LiveData<String> = _text
}