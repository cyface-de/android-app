package de.cyface.app.digural.capturing.settings

import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.Spinner
import androidx.lifecycle.viewModelScope
import de.cyface.camera_service.background.TriggerMode
import kotlinx.coroutines.launch

class TriggerModeSelectionListener(private val viewModel: SettingsViewModel) : OnItemSelectedListener {
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        viewModel.viewModelScope.launch {
            viewModel.setTriggerMode(TriggerMode.fromOrdinal(position))
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        // Default: TriggerMode.STATIC_DISTANCE, see resources.string.trigger_mode BackendService
        (parent as Spinner).setSelection(0)
    }
}