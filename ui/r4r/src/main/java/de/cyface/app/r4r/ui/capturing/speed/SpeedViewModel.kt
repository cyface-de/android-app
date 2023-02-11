package de.cyface.app.r4r.ui.capturing.speed

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SpeedViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is speed Fragment"
    }
    val text: LiveData<String> = _text
}