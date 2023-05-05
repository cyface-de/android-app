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
package de.cyface.app.ui.dialog;

import static de.cyface.app.capturing.SettingsFragment.DIALOG_EXPOSURE_TIME_SELECTION_REQUEST_CODE;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_KEY;
import static de.cyface.camera_service.Constants.TAG;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import de.cyface.app.R;
import de.cyface.utils.Validate;

/**
 * To allow the user to select the exposure time from a set of common values (1/125s, 1/250s, ...)
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 2.9.0
 */
public class ExposureTimeDialog extends DialogFragment {

    private final static int DIALOG_EXPOSURE_TIME_SELECTION_RESULT_CODE = 202002171;

    private final static CharSequence[] itemsDescriptions = new CharSequence[] {"1/125 s", "1/250 s", "1/500 s",
            "1/1.000 s", "1/2.000 s", "1/4.000 s", "1/8.000 s", "1/18.367 s"};

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.static_camera_exposure_time).setItems(itemsDescriptions,
                (dialog, which) -> {

                    final FragmentActivity fragmentActivity = getActivity();
                    Validate.notNull(fragmentActivity);
                    final SharedPreferences.Editor editor = PreferenceManager
                            .getDefaultSharedPreferences(fragmentActivity.getApplicationContext()).edit();

                    final long exposureTimeNanos;
                    switch (which) {
                        case 0:
                            // Pixel 3a reference device: EV100 10, f/1.8, 1/125s (2ms) => iso 40 (55 used, minimum)
                            exposureTimeNanos = Math.round(1_000_000_000.0 / 125);
                            break;
                        case 1:
                            exposureTimeNanos = Math.round(1_000_000_000.0 / 250);
                            break;
                        case 2:
                            exposureTimeNanos = Math.round(1_000_000_000.0 / 500);
                            break;
                        case 3:
                            exposureTimeNanos = Math.round(1_000_000_000.0 / 1_000);
                            break;
                        case 4:
                            exposureTimeNanos = Math.round(1_000_000_000.0 / 2_000);
                            break;
                        case 5:
                            exposureTimeNanos = Math.round(1_000_000_000.0 / 4_000);
                            break;
                        case 6:
                            exposureTimeNanos = Math.round(1_000_000_000.0 / 8_000);
                            break;
                        case 7:
                            exposureTimeNanos = 54_444; // shortest exposure time on Pixel 3a
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown exposure time selected: " + which);
                    }
                    Log.d(TAG, "Update preference to exposure time -> " + exposureTimeNanos + " ns");
                    editor.putLong(PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_KEY, exposureTimeNanos).apply();

                    final int requestCode = getTargetRequestCode();
                    final int resultCode;
                    final Intent intent = new Intent();
                    if (requestCode == DIALOG_EXPOSURE_TIME_SELECTION_REQUEST_CODE) {
                        resultCode = DIALOG_EXPOSURE_TIME_SELECTION_RESULT_CODE;
                        intent.putExtra(PREFERENCES_CAMERA_STATIC_EXPOSURE_TIME_KEY, exposureTimeNanos);
                    } else {
                        throw new IllegalArgumentException("Unknown request code: " + requestCode);
                    }

                    final Fragment targetFragment = getTargetFragment();
                    Validate.notNull(targetFragment);
                    targetFragment.onActivityResult(requestCode, resultCode, intent);
                });
        return builder.create();
    }
}
