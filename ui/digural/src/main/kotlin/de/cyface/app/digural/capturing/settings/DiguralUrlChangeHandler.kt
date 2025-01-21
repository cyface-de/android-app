/*
 * Copyright 2023-2025 Cyface GmbH
 *
 * This file is part of the Cyface App for Android.
 *
 * The Cyface App for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface App for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface App for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.app.digural.capturing.settings

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.google.android.material.textfield.TextInputEditText
import de.cyface.app.digural.R
import kotlinx.coroutines.launch
import java.net.MalformedURLException
import java.net.URL

/**
 * Handles when the user changes the Digural Server Url.
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 3.2.0
 */
class DiguralUrlChangeHandler(
    private val viewModel: SettingsViewModel,
    private val fragment: SettingsFragment,
    private val valueHolder: TextInputEditText
) : View.OnClickListener {

    override fun onClick(v: View?) {
        val newValue = valueHolder.text.toString()
        val previousValue = viewModel.diguralServerUrl.value!!.toExternalForm()
        val context = fragment.requireContext()
        if (previousValue == newValue) {
            clearFocusAndKeyboard(context, v)
            return
        }

        // Invalid url format
        try {
            // Expected format:
            // - starts with `http://` or `https://`
            // - followed by either an IP v4 address or a hostname
            // - may be followed by a port number `:port`
            // - should end with `/`
            // For simplicity, all IP addresses like 999.999.999.999 are accepted.
            // For simplicity, only hostnames of the format [a-ZA-Z0-9\-\.]+ are accepted.
            val regex =
                """^(http://|https://)(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}|[a-zA-Z0-9\-\.]+)(:\d+)?/$""".toRegex()
            if (!newValue.matches(regex)) {
                throw MalformedURLException("Unexpected URL format: $newValue")
            }
            viewModel.viewModelScope.launch {
                viewModel.setDiguralServerUrl(URL(newValue))
            }
        } catch (e: MalformedURLException) {
            Toast.makeText(context, R.string.url_malformed_toast, Toast.LENGTH_LONG)
                .show()
            valueHolder.setText(previousValue)
        }

        clearFocusAndKeyboard(context, v)
    }

    private fun clearFocusAndKeyboard(context: Context, v: View?) {
        // Clear focus
        valueHolder.clearFocus()

        // Hide soft keyboard
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(v!!.windowToken, 0)
    }
}