package de.cyface.app.digural.capturing.settings

import android.text.Editable
import android.text.TextWatcher
import java.net.URL

class DiGuRaLServerAddress(
    private val viewModel: SettingsViewModel
)
    : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        // Nothing to do here!
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        // Nothing to do here!
    }

    override fun afterTextChanged(s: Editable?) {
        if(s != null) {
            viewModel.setDiGuRaLServerAddressValue(URL(s.toString()))
        }
    }

}