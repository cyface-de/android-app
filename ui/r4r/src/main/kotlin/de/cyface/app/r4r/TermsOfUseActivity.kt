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
package de.cyface.app.r4r

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import de.cyface.utils.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The TermsOfUserActivity is the first [Activity] started on app launch.
 *
 * It's responsible for allow the user to opt-in for Sentry.
 *
 * When the current terms are accepted or have been before, the [MainActivity] is launched.
 *
 * @author Armin Schnabel
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
        if (currentTermsHadBeenAccepted()) {
            startActivity(callMainActivityIntent)
            finish()
            return
        }
        setContentView(R.layout.activity_terms_of_use)
    }

    /**
     * @return `True` if the latest privacy policy was accepted by the user.
     */
    private fun currentTermsHadBeenAccepted(): Boolean {
        val acceptedTerms = runBlocking { appSettings.acceptedTermsFlow.first() }
        return acceptedTerms == BuildConfig.currentTerms
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