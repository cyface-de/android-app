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

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.cursoradapter.widget.CursorAdapter;

import de.cyface.app.R;
import de.cyface.persistence.content.BaseColumns;
import de.cyface.persistence.content.EventTable;
import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.Modality;

/**
 * Abstract base class for a {@code CursorAdapter} creating a mapping between a table row with one {@link Event}
 * and a row as part of a {@code ListView} displaying such an element.
 *
 * @author Armin Schnabel
 * @version 2.0.1
 * @since 2.4.0
 */
public class CursorEventAdapter extends CursorAdapter {

    /**
     * The identifier for the list view row which is mapped to a {@link Event} by this adapter.
     */
    private final int rowLayoutIdentifier;
    /**
     * a {@code WeakReference} to the {@code Activity} which is required to retrieve translation strings from the
     * context/resources.
     */
    private final WeakReference<Context> contextWeakReference;

    CursorEventAdapter(final Context context, final Cursor cursor, final int rowLayoutIdentifier) {
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

        // Event time
        final var timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns.TIMESTAMP));
        final Date date = new Date(timestamp);
        final String dateText = new SimpleDateFormat("dd.MM.yy HH:mm", Locale.GERMANY).format(date);

        // Modality type
        final var modality = Modality.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(EventTable.COLUMN_VALUE)));

        // Set List Item Text
        /*final String label = dateText + " (" + CursorMeasureAdapter.getTranslation(contextWeakReference, modality)
                + ")";*/

        // Checkable
        itemView.setEnabled(true);
        itemView.setClickable(false); // Does not make sense but works -.- same in the inverted scenario

        //itemView.setText(label);
    }
}
