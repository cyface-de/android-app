/*
 * Copyright 2017 Cyface GmbH
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

import static de.cyface.app.utils.Constants.ACCEPTED_TERMS_KEY;
import static de.cyface.app.utils.Constants.TAG;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

import de.cyface.app.BuildConfig;
import de.cyface.app.R;

/**
 * The TermsOfUserActivity is the first {@link Activity} started on app launch.
 * It's responsible for informing the user about the terms of use (and data privacy conditions).
 * When the current terms are accepted or have been before, the {@link MainActivity} is launched.
 *
 * @author Armin Schnabel
 * @version 1.0.7
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
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        callMainActivityIntent = new Intent(this, MainActivity.class);

        if (currentTermsHadBeenAccepted()) {
            startActivity(callMainActivityIntent);
            finish();
        } else {
            showTermsToUser();
        }
    }

    private boolean currentTermsHadBeenAccepted() {
        int accepted_terms = preferences.getInt(ACCEPTED_TERMS_KEY, 0);
        boolean alreadyAccepted = accepted_terms == BuildConfig.currentTerms;
        if (!alreadyAccepted) {
            Log.d(TAG, "current terms (" + BuildConfig.currentTerms + ") different from accepted terms ("
                    + accepted_terms + ") -> showing terms of use.");
        }
        return alreadyAccepted;
    }

    private void showTermsToUser() {
        setContentView(R.layout.activity_terms_of_use);
        acceptTermsButton = findViewById(R.id.accept_terms_button);
        acceptTermsButton.setOnClickListener(this);
        CheckBox acceptTermsCheckbox = findViewById(R.id.accept_terms_checkbox);
        acceptTermsCheckbox
                .setOnCheckedChangeListener((buttonView, isChecked) -> acceptTermsButton.setEnabled(isChecked));
    }

    @Override
    public void onClick(View view) {
        final SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(ACCEPTED_TERMS_KEY, BuildConfig.currentTerms);
        editor.apply();
        this.startActivity(callMainActivityIntent);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        acceptTermsButton.setOnClickListener(null);
    }

    @Override
    public void onResume() {
        acceptTermsButton.setOnClickListener(this);
        super.onResume();
    }
}
