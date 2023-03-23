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

import static de.cyface.app.ui.MainFragment.DIALOG_INITIAL_MODALITY_SELECTION_REQUEST_CODE;
import static de.cyface.app.ui.nav.view.MeasurementOverviewFragment.DIALOG_ADD_EVENT_MODALITY_SELECTION_REQUEST_CODE;
import static de.cyface.app.utils.SharedConstants.PREFERENCES_MODALITY_KEY;
import static de.cyface.persistence.model.Modality.BICYCLE;
import static de.cyface.persistence.model.Modality.BUS;
import static de.cyface.persistence.model.Modality.CAR;
import static de.cyface.persistence.model.Modality.TRAIN;
import static de.cyface.persistence.model.Modality.WALKING;
import static de.cyface.synchronization.BundlesExtrasCodes.MEASUREMENT_ID;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import de.cyface.app.R;
import de.cyface.persistence.model.Modality;
import de.cyface.utils.Validate;

/**
 * After the user installed the app for the first time, after accepting the terms, this dialog is shown
 * to force the user to select his {@link Modality} type before being able to use the app. This is necessary
 * as it is not possible to have NO tab (here: {@code Modality} type) selected visually so we make sure the correct
 * one is selected by default. Else, the user might forget (oversee) to select a {@code Modality} at all.
 *
 * Make sure the order (0, 1, 2 from left(start) to right(end)) in the TabLayout is consistent in here.
 *
 * @author Armin Schnabel
 * @version 2.1.0
 * @since 1.0.0
 */
public class ModalityDialog extends DialogFragment {

    private final static int DIALOG_INITIAL_MODALITY_SELECTION_RESULT_CODE = 201909192;
    private final static int DIALOG_ADD_EVENT_MODALITY_SELECTION_RESULT_CODE = 201909194;
    /**
     * The id if the {@code Measurement} if this dialog is called for a specific Measurement.
     */
    private Long measurementId;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.dialog_modality_title).setItems(R.array.dialog_modality,
                (dialog, which) -> {

                    final FragmentActivity fragmentActivity = getActivity();
                    Validate.notNull(fragmentActivity);
                    final SharedPreferences.Editor editor = PreferenceManager
                            .getDefaultSharedPreferences(fragmentActivity.getApplicationContext()).edit();

                    final Modality modality;
                    switch (which) {
                        case 0:
                            modality = Modality.valueOf(CAR.name());
                            break;
                        case 1:
                            modality = Modality.valueOf(BICYCLE.name());
                            break;
                        case 2:
                            modality = Modality.valueOf(WALKING.name());
                            break;
                        case 3:
                            modality = Modality.valueOf(BUS.name());
                            break;
                        case 4:
                            modality = Modality.valueOf(TRAIN.name());
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown modality selected: " + which);
                    }
                    editor.putString(PREFERENCES_MODALITY_KEY, modality.getDatabaseIdentifier()).apply();

                    final int requestCode = getTargetRequestCode();
                    final int resultCode;
                    final Intent intent = new Intent();
                    switch (requestCode) {
                        case DIALOG_INITIAL_MODALITY_SELECTION_REQUEST_CODE:
                            resultCode = DIALOG_INITIAL_MODALITY_SELECTION_RESULT_CODE;
                            break;
                        case DIALOG_ADD_EVENT_MODALITY_SELECTION_REQUEST_CODE:
                            resultCode = DIALOG_ADD_EVENT_MODALITY_SELECTION_RESULT_CODE;
                            intent.putExtra(PREFERENCES_MODALITY_KEY, modality.getDatabaseIdentifier());
                            Validate.notNull(measurementId);
                            intent.putExtra(MEASUREMENT_ID, measurementId);
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown request code: " + requestCode);
                    }

                    final Fragment targetFragment = getTargetFragment();
                    Validate.notNull(targetFragment);
                    targetFragment.onActivityResult(requestCode, resultCode, intent);
                });
        return builder.create();
    }

    public void setMeasurementId(long measurementId) {
        this.measurementId = measurementId;
    }
}
