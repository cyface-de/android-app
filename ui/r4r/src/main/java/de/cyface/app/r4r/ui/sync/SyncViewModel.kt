package de.cyface.app.r4r.ui.sync

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SyncViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is sync Fragment"
    }
    val text: LiveData<String> = _text
}