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
package de.cyface.app.ui.nav.view;

import com.google.android.gms.maps.model.Marker;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.fragment.app.FragmentActivity;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;

import org.apache.commons.lang3.Validate;

import de.cyface.app.R;
import de.cyface.app.ui.Map;
import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.EventTable;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Event;

/**
 * A selectable list which is bound to a {@code Event} {@code android.widget.Adapter}.
 *
 * @author Armin Schnabel
 * @version 1.0.3
 * @since 2.4.0
 */
class EventDataList implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener,
        LoaderManager.LoaderCallbacks<Cursor> {

    /**
     * A view showing all the {@code Event}s of a {@code Measurement}.
     */
    private ListView listView;
    /**
     * Observes changes on the {@code Event}s.
     */
    private CursorAdapter cursorAdapter;
    /**
     * The {@code FragmentActivity} required to create a new {@link CursorEventAdapter}
     */
    private final FragmentActivity activity;
    /**
     * The {@code PersistenceLayer} required to link the {@link CursorEventAdapter} to the data stored persistently.
     */
    private final PersistenceLayer<DefaultPersistenceBehaviour> persistenceLayer;
    /**
     * The id of the {@code Measurement} linked to the {@code Events} handled by this {@code EventDataList}.
     * <p>
     * This can be null as the {@code EventDataList} is initialized in
     * {@link MeasurementOverviewFragment#onCreateView(LayoutInflater, ViewGroup, Bundle)} where we don't know the
     * {@code Measurement} of which we want to visualize Events next.
     */
    @Nullable
    private Long measurementId;
    /**
     * The {@code Map} required to find the map {@code Marker} of a selected {@code Event} so that this Marker can be
     * focused.
     */
    private final Map map;

    /**
     * @param activity The {@code FragmentActivity} required to create a new {@link CursorEventAdapter}
     * @param persistenceLayer The {@code PersistenceLayer} required to link the {@link CursorEventAdapter} to the data
     *            stored persistently.
     * @param measurementId The id of the {@code Measurement} linked to the {@code Events} handled by this
     *            {@code EventDataList}.
     *            <p>
     *            This can be null as the {@code EventDataList} is initialized in
     *            {@link MeasurementOverviewFragment#onCreateView(LayoutInflater, ViewGroup, Bundle)} where don't know
     *            the {@code Measurement} of which we want to visualize Events next.
     * @param map The {@code Map} required to find the map {@code Marker} of a selected {@code Event} so that this
     *            Marker can be focused.
     */
    EventDataList(FragmentActivity activity,
            PersistenceLayer<DefaultPersistenceBehaviour> persistenceLayer, @Nullable final Long measurementId,
            @NonNull final Map map) {
        this.activity = activity;
        this.persistenceLayer = persistenceLayer;
        this.measurementId = measurementId;
        this.map = map;
    }

    /**
     * Must be called in the parent {@code FragmentActivity#onCreateView()} to link the view.
     *
     * @param parentView The {@code View} of the parent element, e.g. a {@code FragmentActivity}.
     */
    void onCreateView(@NonNull final View parentView) {
        listView = parentView.findViewById(R.id.measurements_list_view);
        listView.clearChoices(); // Fixes the wrong selected item count after changing the cursor VIC-104
        // The cursor is not yet available so the adapter is reassigned in {@link #onLoadFinished()}
        cursorAdapter = new CursorEventAdapter(activity.getBaseContext(), null, R.layout.data_row);
        listView.setAdapter(cursorAdapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {

        // Enable multi selection mode (via long press)
        final boolean isMultiSelectionModeEnabled = listView.getChoiceMode() == ListView.CHOICE_MODE_MULTIPLE;
        if (!isMultiSelectionModeEnabled) {
            listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
            Toast.makeText(activity.getBaseContext(), R.string.toast_selection_mode_enabled, Toast.LENGTH_SHORT).show();
        }

        return false;
    }

    @Override
    public void onItemClick(final AdapterView<?> adapterView, final View view, final int position, final long id) {

        // Disable multi selection mode (back to single selection)
        final int checkedItems = listView.getCheckedItemCount();
        final boolean isMultiSelectionModeEnabled = listView.getChoiceMode() == ListView.CHOICE_MODE_MULTIPLE;
        if (isMultiSelectionModeEnabled && checkedItems == 0) {
            listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            Toast.makeText(activity.getBaseContext(), R.string.toast_selection_mode_disabled, Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        // Move map camera to marker and focus it
        final Cursor cursor = cursorAdapter.getCursor();
        cursor.moveToPosition(position);
        final long eventId = cursor.getInt(cursor.getColumnIndex(BaseColumns._ID));
        final Marker marker = map.getEventMarker().get(eventId);
        // Can be null when no positions are available and, thus, no events can be positioned
        if (marker != null) {
            map.focusMarker(marker);
        }

        // The Setting options "mark all" and "delete marked" handle the marked items
    }

    @NonNull
    @Override
    public CursorLoader onCreateLoader(final int id, final Bundle args) {
        final FragmentActivity fragmentActivity = activity;
        Validate.notNull(fragmentActivity);
        final Uri eventUri = persistenceLayer.getEventUri();
        return new CursorLoader(fragmentActivity, eventUri, null,
                EventTable.COLUMN_MEASUREMENT_FK + " = ? AND " + EventTable.COLUMN_TYPE + " = ?",
                new String[] {String.valueOf(measurementId), String.valueOf(Event.EventType.MODALITY_TYPE_CHANGE)},
                EventTable.COLUMN_TIMESTAMP + " ASC");
    }

    @Override
    public void onLoadFinished(@NonNull androidx.loader.content.Loader<Cursor> loader, Cursor cursor) {
        if (activity == null || cursor.isClosed()) {
            return;
        }

        cursor.moveToFirst();
        cursorAdapter = new CursorEventAdapter(activity.getBaseContext(), cursor, R.layout.data_row);
        listView.setAdapter(cursorAdapter);
        listView.setOnItemClickListener(this);
        listView.setOnItemLongClickListener(this);
    }

    @Override
    public void onLoaderReset(@NonNull androidx.loader.content.Loader<Cursor> loader) {
        if (cursorAdapter != null) {
            cursorAdapter.changeCursor(null);
        }
    }

    void setMeasurementId(final long measurementId) {
        this.measurementId = measurementId;
    }

    @Nullable
    Long getMeasurementId() {
        return measurementId;
    }

    ListView getListView() {
        return listView;
    }
}
