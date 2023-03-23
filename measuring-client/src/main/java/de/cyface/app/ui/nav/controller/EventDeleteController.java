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
package de.cyface.app.ui.nav.controller;

import static de.cyface.app.utils.Constants.AUTHORITY;
import static de.cyface.app.utils.Constants.TAG;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import de.cyface.app.R;
import de.cyface.app.utils.Map;
import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.DefaultPersistenceLayer;
import de.cyface.persistence.content.BaseColumns;
import de.cyface.persistence.model.Event;
import de.cyface.utils.Validate;

/**
 * {@code AsyncTask} to delete {@code Event}s with all their data.
 * <p>
 * We use an {@code AsyncTask} because this is blocking but should only run for a short time.
 *
 * @author Armin Schnabel
 * @version 2.0.1
 * @since 2.4.0
 */
public final class EventDeleteController
        extends AsyncTask<EventDeleteController.EventDeleteControllerParameters, Void, ListView> {

    private final WeakReference<Context> contextReference;
    /**
     * The data persistenceLayer used by this controller.
     */
    private final DefaultPersistenceLayer<DefaultPersistenceBehaviour> persistenceLayer;
    private Map map;
    private final List<Long> eventIds = new ArrayList<>();

    /**
     * Creates a new completely initialized object of this class with the current context.
     */
    public EventDeleteController(@NonNull final Context context) {
        this.contextReference = new WeakReference<>(context);
        this.persistenceLayer = new DefaultPersistenceLayer<>(context, AUTHORITY, new DefaultPersistenceBehaviour());
    }

    @Override
    protected ListView doInBackground(@NonNull final EventDeleteControllerParameters... params) {

        final ListView view = params[0].listView;
        this.map = params[0].map;

        final List<Event> selectedItems = getSelectedItems(view);
        for (final Event item : selectedItems) {
            final long eventId = item.getId();
            // use getContext only on high APIs
            final Context context = contextReference.get();
            Validate.notNull(context);
            persistenceLayer.deleteEvent(eventId);
            eventIds.add(eventId);
        }
        return view;
    }

    @Override
    protected void onPostExecute(@NonNull final ListView listView) {
        super.onPostExecute(listView);
        Toast.makeText(contextReference.get(), R.string.toast_item_deletion_success, Toast.LENGTH_LONG).show();
        for (final long eventId : eventIds) {
            map.removeEventMarker(eventId);
        }

        // Fixes bug where multi selection mode can't be left after deleting multiple entries
        Log.d(TAG, "onPostExecute -> clearChoices & setChoiceMode to single");
        listView.clearChoices();
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
    }

    /**
     * Finds the selected {@code Event}s inside the view.
     *
     * @param view The view showing the {@code Event}s to delete.
     * @return A list with all currently selected {@code Event}s in the {@code ListView}.
     */
    @NonNull
    private List<Event> getSelectedItems(@NonNull final ListView view) {
        final SparseBooleanArray checkedItemPositions = view.getCheckedItemPositions();
        final List<Event> ret = new ArrayList<>();

        // Check if items are selected
        if (checkedItemPositions.indexOfValue(true) == -1) {
            return ret;
        }

        for (int itemPosition = 0; itemPosition < checkedItemPositions.size(); itemPosition++) {
            if (!checkedItemPositions.valueAt(itemPosition)) {
                Log.w(TAG, "No value for selected item, ignoring");
                continue;
            }
            final int checkedRowNumber = checkedItemPositions.keyAt(itemPosition);
            final Cursor cursor = (Cursor)view.getItemAtPosition(checkedRowNumber);
            final long selectedEventId = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns.ID));

            final Event event;
            event = persistenceLayer.loadEvent(selectedEventId);
            ret.add(event);
        }
        return ret;
    }

    public static class EventDeleteControllerParameters {
        final ListView listView;
        final Map map;

        public EventDeleteControllerParameters(ListView listView, Map map) {
            this.listView = listView;
            this.map = map;
        }
    }
}
