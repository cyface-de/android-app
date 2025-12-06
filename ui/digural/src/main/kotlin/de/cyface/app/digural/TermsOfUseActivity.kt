/*
 * Copyright 2017-2025 Cyface GmbH
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
package de.cyface.app.digural

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import de.cyface.utils.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The TermsOfUserActivity is the first [Activity] started on app launch.
 *
 * It's responsible for allow the user to opt-in for Sentry.
 *
 * When the current terms are accepted or have been before, the [MainActivity] is launched.
 *
 * @author Armin Schnabel
 * @version 2.0.1
 * @since 1.0.0
 */
class TermsOfUseActivity : AppCompatActivity(), View.OnClickListener {
    /**
     * Intent for switching to the main activity after this activity has been finished.
     */
    private var callMainActivityIntent: Intent? = null

    /**
     * The button to click to accept the terms of use and data privacy conditions.
     */
    private var acceptTermsButton: Button? = null

    /**
     * `True` if the user opted-in to error reporting.
     */
    private var isReportingEnabled = false

    /**
     * The settings used by both, UIs and libraries.
     */
    private lateinit var appSettings: AppSettings

    /**
     * Allows the user to opt-in to error reporting.
     */
    private var acceptReportsCheckbox: CheckBox? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appSettings = Application.appSettings
        callMainActivityIntent = Intent(this, MainActivity::class.java)

        // Check synchronously, or else the terms will pop up randomly until checked.
        lifecycleScope.launch {
            if (withContext(Dispatchers.IO) { currentTermsHadBeenAccepted() }) {
                startActivity(callMainActivityIntent)
                finish()
                return@launch
            }
        }

        setContentView(R.layout.activity_terms_of_use)
        // Fix for edge-to-edge introduced in targetSdkVersion 35
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { view, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemInsets.top, 0, systemInsets.bottom)
            insets
        }
        // Set status bar appearance based on theme (light icons for dark mode, dark icons for light mode)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val isLightTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO
        WindowInsetsControllerCompat(window, findViewById(R.id.root)).isAppearanceLightStatusBars = isLightTheme
    }

    /**
     * @return `True` if the latest privacy policy was accepted by the user.
     */
    private suspend fun currentTermsHadBeenAccepted(): Boolean {
        return appSettings.acceptedTermsFlow.first() == BuildConfig.currentTerms
    }

    /**
     * Registers the handlers for user interaction.
     */
    private fun registerOnClickListeners() {
        acceptTermsButton = findViewById(R.id.accept_terms_button)
        acceptTermsButton!!.setOnClickListener(this)
        acceptReportsCheckbox = findViewById(R.id.accept_reports_checkbox)
        acceptReportsCheckbox!!
            .setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                isReportingEnabled = isChecked
            }
    }

    /**
     * Unregisters the handlers for user interaction.
     */
    private fun unregisterOnClickListeners() {
        acceptTermsButton!!.setOnClickListener(null)
        acceptReportsCheckbox!!.setOnCheckedChangeListener(null)
    }

    override fun onClick(view: View) {
        lifecycleScope.launch {
            appSettings.setAcceptedTerms(BuildConfig.currentTerms)
            appSettings.setReportErrors(isReportingEnabled)
        }
        this.startActivity(callMainActivityIntent)
        finish()
    }

    override fun onPause() {
        super.onPause()
        unregisterOnClickListeners()
        acceptTermsButton!!.setOnClickListener(null)
    }

    public override fun onResume() {
        registerOnClickListeners()
        super.onResume()
    }
}