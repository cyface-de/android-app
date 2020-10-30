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
/*package de.cyface.app.ui.nav.view;

import java.util.ArrayList;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import de.cyface.app.R;
import de.cyface.persistence.model.Measurement;

/**
 * Abstract base class for an array adapter creating a mapping between a list element (one Cyface data measurement)
 * and a row as part of a list view displaying this measurement.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 3.0.0
 * /
public final class MeasurementAdapter extends ArrayAdapter<Measurement> {

    /**
     * The R file identifier of the row layout user interface to load per row.
     * /
    private final int rowLayoutIdentifier;

    /**
     * Creates a new completely initialized object of this class.
     *
     * @param context The current Android <code>Context</code>.
     * @param rowLayoutIdentifier The R file identifier of the row layout user interface to load per row.
     * /
    MeasurementAdapter(@NonNull Activity context, int rowLayoutIdentifier) {
        // Using 0 as resource, since we overwrite getView below and provide our own view.
        super(context, 0, new ArrayList<Measurement>());
        this.rowLayoutIdentifier = rowLayoutIdentifier;
    }

    @NonNull
    @Override
    public View getView(final int position, View convertView, final @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(rowLayoutIdentifier, parent, false);
        }

        // Get the data item for this position
        Measurement measurement = getItem(position);

        // Convert data to view
        mapToView(measurement, convertView);

        // Return the view to render on screen
        return convertView;
    }

    /**
     * Maps the provided {@link Measurement} to the provided view, which should be a view corresponding to
     * {@link #rowLayoutIdentifier}.
     *
     * @param measurement The <code>Measurement</code> to map.
     * @param view The <code>View</code> to map to.
     * /
    private void mapToView(final @NonNull Measurement measurement, final @NonNull View view) {
        final String measurementLabel = view.getContext().getString(R.string.measurement) + " "
                + measurement.getIdentifier();
        ((TextView)view.findViewById(R.id.data_row_text)).setText(measurementLabel);
    }
}*/
