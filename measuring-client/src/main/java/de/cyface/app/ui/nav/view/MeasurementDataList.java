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

import static de.cyface.app.utils.Constants.ACCEPTED_REPORTING_KEY;
import static de.cyface.app.utils.Constants.TAG;

import java.util.List;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.fragment.app.FragmentActivity;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;

import de.cyface.app.R;
import de.cyface.app.ui.Map;
import de.cyface.persistence.DefaultLocationCleaningStrategy;
import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.Track;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;
import io.sentry.Sentry;

/**
 * A selectable list which is bound to a {@code Measurement} {@code android.widget.Adapter}.
 *
 * @author Armin Schnabel
 * @version 2.0.4
 * @since 2.0.0
 */
class MeasurementDataList implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener,
        LoaderManager.LoaderCallbacks<Cursor> {

    /**
     * A view showing all the {@code Measurement}s currently stored on the device.
     */
    private ListView listView;
    /**
     * Observes changes on the currently stored {@code Measurement}s.
     */
    private CursorAdapter cursorAdapter;
    /**
     * The {@code FragmentActivity} required to create a new {@link CursorEventAdapter} and {@code CursorLoader}.
     */
    private final FragmentActivity activity;
    /**
     * The {@code PersistenceLayer} required to link the data stored persistently with adapters and cursors.
     */
    private final PersistenceLayer<DefaultPersistenceBehaviour> persistenceLayer;
    /**
     * The {@code MeasurementOverviewFragment} required to {@link MeasurementOverviewFragment#showEvents(long)}
     * when a {@code Measurement} is selected.
     */
    private final MeasurementOverviewFragment measurementOverviewFragment;
    /**
     * The {@code Map} on which the {@code Measurement}s are shown.
     */
    private final Map map;
    /**
     * {@code True} if the user opted-in to error reporting.
     */
    private final boolean isReportingEnabled;

    /**
     * @param activity The {@code FragmentActivity} required to create a new {@link CursorEventAdapter} and
     *            {@code CursorLoader}.
     * @param persistenceLayer The {@code PersistenceLayer} required to link the data stored persistently with adapters
     *            and cursors.
     * @param measurementOverviewFragment The {@code MeasurementOverviewFragment} required to
     *            {@link MeasurementOverviewFragment#showEvents(long)}
     *            when a {@code Measurement} is selected.
     * @param map The {@code Map} on which the {@code Measurement}s are shown.
     */
    MeasurementDataList(FragmentActivity activity,
            PersistenceLayer<DefaultPersistenceBehaviour> persistenceLayer,
            MeasurementOverviewFragment measurementOverviewFragment, Map map) {
        this.activity = activity;
        this.persistenceLayer = persistenceLayer;
        this.measurementOverviewFragment = measurementOverviewFragment;
        this.map = map;
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        isReportingEnabled = preferences.getBoolean(ACCEPTED_REPORTING_KEY, false);
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
        cursorAdapter = new CursorMeasureAdapter(activity, null, R.layout.data_row);
        listView.setAdapter(cursorAdapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        Log.d(TAG, "onCreateView");
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {

        // Enable multi selection mode (via long press)
        final boolean isMultiSelectionModeEnabled = listView.getChoiceMode() == ListView.CHOICE_MODE_MULTIPLE;
        Log.d(TAG, "onItemLongClick: isMultiSelectionModeEnabled: " + isMultiSelectionModeEnabled);
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
        Log.d(TAG, "onItemClick: " + checkedItems + " isMultiSelectionModeEnabled: " + isMultiSelectionModeEnabled);
        if (isMultiSelectionModeEnabled && checkedItems == 0) {
            listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            Toast.makeText(activity.getBaseContext(), R.string.toast_selection_mode_disabled, Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        // Show track on map
        if (!isMultiSelectionModeEnabled && listView.isItemChecked(position)) {

            // Load track
            final Cursor cursor = cursorAdapter.getCursor();
            cursor.moveToPosition(position);
            final long measurementId = cursor.getInt(cursor.getColumnIndex(BaseColumns._ID));
            final List<Track> tracks;
            final List<Event> events;
            try {
                tracks = persistenceLayer.loadTracks(measurementId, new DefaultLocationCleaningStrategy());
                events = persistenceLayer.loadEvents(measurementId, Event.EventType.MODALITY_TYPE_CHANGE);
            } catch (final CursorIsNullException e) {
                Log.w(TAG, "Ignored onItemClick.loadTracks() because of null Cursor.");
                if (isReportingEnabled) {
                    Sentry.captureException(e);
                }
                return;
            }
            map.renderMeasurement(tracks, events, true);

            // Show Modality type changes in the ListView
            measurementOverviewFragment.showEvents(measurementId);
        }
    }

    @NonNull
    @Override
    public CursorLoader onCreateLoader(final int id, final Bundle args) {
        final FragmentActivity fragmentActivity = activity;
        Validate.notNull(fragmentActivity);
        final Uri measurementUri = persistenceLayer.getMeasurementUri();
        return new CursorLoader(fragmentActivity, measurementUri, null, null, null, BaseColumns._ID + " DESC");
    }

    @Override
    public void onLoadFinished(@NonNull androidx.loader.content.Loader<Cursor> loader, Cursor cursor) {
        if (activity == null || cursor.isClosed()) {
            return;
        }

        cursor.moveToFirst();
        cursorAdapter = new CursorMeasureAdapter(activity, cursor, R.layout.data_row);
        listView.setAdapter(cursorAdapter);
        listView.setOnItemClickListener(this);
        listView.setOnItemLongClickListener(this);
    }

    @Override
    public void onLoaderReset(@NonNull androidx.loader.content.Loader<Cursor> loader) {
        if (cursorAdapter != null) {
            cursorAdapter.changeCursor(null);
        }
        Log.d(TAG, "onLoaderReset");
    }

    ListView getListView() {
        return listView;
    }
}
