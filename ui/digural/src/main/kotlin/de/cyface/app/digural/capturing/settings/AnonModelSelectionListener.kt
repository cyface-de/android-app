package de.cyface.app.digural.capturing.settings

import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.Spinner
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.net.URL

class AnonModelSelectionListener(private val viewModel: SettingsViewModel) : OnItemSelectedListener {
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        viewModel.viewModelScope.launch {
            viewModel.setAnonModel(position)
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        // Default: No Anonymization, see resources.string.anon_models BackendService
        (parent as Spinner).setSelection(0)
    }
}