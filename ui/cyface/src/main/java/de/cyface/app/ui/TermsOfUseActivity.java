/*
 * Copyright 2017-2023 Cyface GmbH
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
package de.cyface.app.ui;

import static de.cyface.app.utils.SharedConstants.ACCEPTED_REPORTING_KEY;
import static de.cyface.app.utils.SharedConstants.ACCEPTED_TERMS_KEY;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

import de.cyface.app.BuildConfig;
import de.cyface.app.R;

/**
 * The TermsOfUserActivity is the first {@link Activity} started on app launch.
 * <p>
 * It's responsible for informing the user about the terms of use (and data privacy conditions).
 * <p>
 * When the current terms are accepted or have been before, the {@link MainActivity} is launched.
 *
 * @author Armin Schnabel
 * @version 1.1.1
 * @since 1.0.0
 */
public class TermsOfUseActivity extends Activity implements View.OnClickListener {

    /**
     * Intent for switching to the main activity after this activity has been finished.
     */
    private Intent callMainActivityIntent;
    /**
     * The button to click to accept the terms of use and data privacy conditions.
     */
    private Button acceptTermsButton;
    /**
     * {@code True} if the user opted-in to error reporting.
     */
    private boolean isReportingEnabled;
    /**
     * To check whether the user accepted the terms and opted-in to error reporting.
     */
    private SharedPreferences preferences;
    /**
     * Allows the user to opt-in to error reporting.
     */
    private CheckBox acceptReportsCheckbox;
    /**
     * To ask the user to accept the terms.
     */
    private CheckBox acceptTermsCheckbox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        callMainActivityIntent = new Intent(this, MainActivity.class);

        if (currentTermsHadBeenAccepted()) {
            startActivity(callMainActivityIntent);
            finish();
            return;
        }

        setContentView(R.layout.activity_terms_of_use);
    }

    /**
     * @return {@code True} if the latest privacy policy was accepted by the user.
     */
    private boolean currentTermsHadBeenAccepted() {
        final int acceptedTermsVersion = preferences.getInt(ACCEPTED_TERMS_KEY, 0);
        return acceptedTermsVersion == BuildConfig.currentTerms;
    }

    /**
     * Registers the handlers for user interaction.
     */
    private void registerOnClickListeners() {
        acceptTermsButton = findViewById(R.id.accept_terms_button);
        acceptTermsButton.setOnClickListener(this);
        acceptTermsCheckbox = findViewById(R.id.accept_terms_checkbox);
        acceptReportsCheckbox = findViewById(R.id.accept_reports_checkbox);
        acceptTermsCheckbox
                .setOnCheckedChangeListener((buttonView, isChecked) -> acceptTermsButton.setEnabled(isChecked));
        acceptReportsCheckbox
                .setOnCheckedChangeListener((buttonView, isChecked) -> isReportingEnabled = isChecked);
    }

    /**
     * Unregisters the handlers for user interaction.
     */
    private void unregisterOnClickListeners() {
        acceptTermsButton.setOnClickListener(null);
        acceptTermsCheckbox.setOnCheckedChangeListener(null);
        acceptReportsCheckbox.setOnCheckedChangeListener(null);
    }

    @Override
    public void onClick(View view) {
        final SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(ACCEPTED_TERMS_KEY, BuildConfig.currentTerms);
        editor.putBoolean(ACCEPTED_REPORTING_KEY, isReportingEnabled);
        editor.apply();
        this.startActivity(callMainActivityIntent);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterOnClickListeners();
        acceptTermsButton.setOnClickListener(null);
    }

    @Override
    public void onResume() {
        registerOnClickListeners();
        super.onResume();
    }
}
