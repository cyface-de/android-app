/*
 * Copyright 2023 Cyface GmbH
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
import com.google.android.material.textfield.TextInputEditText
import de.cyface.app.digural.R
import java.net.MalformedURLException
import java.net.URL

/**
 * Handles when the user changes the Digural Server Url.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.2.0
 */
class DiguralUrlChangeHandler(
    private val viewModel: SettingsViewModel,
    private val fragment: SettingsFragment,
    private val valueHolder: TextInputEditText
) : View.OnClickListener {

    override fun onClick(v: View?) {
        val previousValue = viewModel.diguralServerUrl.value
        val newValue = valueHolder.text
        val context = fragment.requireContext()
        if (newValue != null && newValue.toString() != previousValue?.toExternalForm()) {
            try {
                viewModel.setDiguralServerUrl(URL(newValue.toString()))
            } catch (e: MalformedURLException) {
                Toast.makeText(context, R.string.url_malformed_toast, Toast.LENGTH_LONG)
                    .show()
                viewModel.setDiguralServerUrl(previousValue!!)
            }
        }

        // Clear focus
        valueHolder.clearFocus()

        // Hide soft keyboard
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(v!!.windowToken, 0)
    }

}