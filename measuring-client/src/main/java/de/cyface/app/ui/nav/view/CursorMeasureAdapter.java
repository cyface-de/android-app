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
package de.cyface.app.ui.nav.view;

import static de.cyface.app.ui.MainActivity.TAG;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cursoradapter.widget.CursorAdapter;

import de.cyface.app.R;
import de.cyface.persistence.content.BaseColumns;
import de.cyface.persistence.content.MeasurementTable;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.Modality;

/**
 * Abstract base class for a {@code CursorAdapter} creating a mapping between a table row with one {@link Measurement}
 * and a row as part of a {@code ListView} displaying such an element.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.0.3
 * @since 1.0.0
 */
public class CursorMeasureAdapter extends CursorAdapter {

    /**
     * The identifier for the list view row which is mapped to a {@link Measurement} by this adapter.
     */
    private final int rowLayoutIdentifier;
    /**
     * a {@code WeakReference} to the {@code Context} which is required to retrieve translation strings from the
     * context/resources.
     */
    private final WeakReference<Context> contextWeakReference;

    CursorMeasureAdapter(final Context context, final Cursor cursor, final int rowLayoutIdentifier) {
        super(context, cursor, false);
        this.contextWeakReference = new WeakReference<>(context);
        this.rowLayoutIdentifier = rowLayoutIdentifier;
    }

    @Override
    public void bindView(final View view, final Context context, final Cursor cursor) {
        mapCursorToView(cursor, view);
    }

    @Override
    public View newView(final Context context, final Cursor cursor, final ViewGroup parent) {
        final View newView = LayoutInflater.from(context).inflate(rowLayoutIdentifier, parent, false);
        mapCursorToView(cursor, newView);
        return newView;
    }

    private void mapCursorToView(final Cursor cursor, final View view) {

        final TextView itemView = view.findViewById(R.id.data_row_text);
        final var measurementId = cursor.getInt(cursor.getColumnIndexOrThrow(BaseColumns.ID));

        // Measurement time
        final var measurementTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns.TIMESTAMP));
        final Date date = new Date(measurementTimestamp);
        final String dateText = new SimpleDateFormat("dd.MM.yy HH:mm", Locale.GERMANY).format(date);

        // Retrieve distance data
        final var distance = cursor.getDouble(cursor.getColumnIndexOrThrow(MeasurementTable.COLUMN_DISTANCE));
        final int distanceMeter = (int)Math.round(distance);
        final double distanceKm = distanceMeter == 0 ? 0.0 : distanceMeter / 1000.0;
        final String distanceText = distanceKm + " km";

        // Retrieve modality
        final var modality = Modality
                .valueOf(cursor.getString(cursor.getColumnIndexOrThrow(MeasurementTable.COLUMN_MODALITY)));

        // Set List Item Text
        String label = "(" + measurementId + ") " + dateText + " (" + distanceText + ") - "
                + getTranslation(contextWeakReference, modality);

        // Disable synced and non-finished items to disallow deletion
        final var status = MeasurementStatus
                .valueOf(cursor.getString(cursor.getColumnIndexOrThrow(MeasurementTable.COLUMN_STATUS)));
        if (status == MeasurementStatus.OPEN || status == MeasurementStatus.PAUSED
                || status == MeasurementStatus.SYNCED || status == MeasurementStatus.SKIPPED
                || status == MeasurementStatus.DEPRECATED) {
            label += " - " + status.toString().toLowerCase(Locale.ENGLISH);
        }
        // Checkable
        itemView.setEnabled(true);
        itemView.setClickable(false); // Does not make sense but works. same in the inverted scenario

        itemView.setText(label);
    }

    /**
     * Returns the translation for the {@link Modality}.
     *
     * @param contextWeakReference A {@code WeakReference} to a {@code Context} required to access the translations
     * @param modality the Modality to translate
     * @return the translation string
     */
    @NonNull
    public static String getTranslation(@Nullable final WeakReference<Context> contextWeakReference,
            @NonNull final Modality modality) {
        if (contextWeakReference == null) {
            Log.w(TAG, "WeakReference is null, displaying database identifier instead of translation for modality.");
            return modality.getDatabaseIdentifier().toLowerCase(Locale.ENGLISH);
        }

        final Context context = contextWeakReference.get();
        switch (modality) {
            case TRAIN:
                return context.getString(de.cyface.app.utils.R.string.modality_train);
            case BUS:
                return context.getString(de.cyface.app.utils.R.string.modality_bus);
            case WALKING:
                return context.getString(de.cyface.app.utils.R.string.modality_walking);
            case CAR:
                return context.getString(de.cyface.app.utils.R.string.modality_car);
            case BICYCLE:
                return context.getString(de.cyface.app.utils.R.string.modality_bicycle);
            case MOTORBIKE:
                return context.getString(de.cyface.app.utils.R.string.modality_motorbike);
            case UNKNOWN:
                return modality.getDatabaseIdentifier().toLowerCase(Locale.ENGLISH);
            default:
                throw new IllegalArgumentException("Unknown modality type: " + modality);
        }
    }
}
