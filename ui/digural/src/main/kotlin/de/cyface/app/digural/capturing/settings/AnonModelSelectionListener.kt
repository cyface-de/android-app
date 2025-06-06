package de.cyface.app.digural.capturing.settings

import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.Spinner
import androidx.lifecycle.viewModelScope
import de.cyface.camera_service.settings.EfficientDetLiteV2
import de.cyface.camera_service.settings.FileSelection
import de.cyface.camera_service.settings.InvalidSelection
import de.cyface.camera_service.settings.NoAnonymization
import de.cyface.camera_service.settings.OriginalDigural
import kotlinx.coroutines.launch

class AnonModelSelectionListener(private val viewModel: SettingsViewModel) : OnItemSelectedListener {
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        viewModel.viewModelScope.launch {
            if (view != null) {
                viewModel.setAnonModel(
                    when(position) {
                        0 -> NoAnonymization()
                        1 -> EfficientDetLiteV2()
                        2 -> OriginalDigural()
                        3 -> FileSelection("")
                        else -> throw InvalidSelection()
                    }
                )
            }
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        // Default: No Anonymization, see resources.string.anon_models BackendService
        (parent as Spinner).setSelection(0)
    }
}