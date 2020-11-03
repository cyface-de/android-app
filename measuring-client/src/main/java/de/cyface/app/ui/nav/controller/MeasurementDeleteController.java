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
package de.cyface.app.ui.nav.controller;

import static de.cyface.app.utils.Constants.ACCEPTED_REPORTING_KEY;
import static de.cyface.app.utils.Constants.AUTHORITY;
import static de.cyface.app.utils.Constants.TAG;
import static de.cyface.camera_service.Constants.externalCyfaceFolderPath;
import static de.cyface.utils.Utils.informMediaScanner;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.cyface.app.R;
import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.Measurement;
import de.cyface.utils.CursorIsNullException;
import de.cyface.utils.Validate;
import io.sentry.Sentry;

/**
 * Async task to delete measurements with all their data.
 * We use an AsyncTask because this is blocking but should only run for a short time.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.0.2
 * @since 1.0.0
 */
public final class MeasurementDeleteController extends AsyncTask<ListView, Void, ListView> {

    private final WeakReference<Context> contextReference;
    /**
     * The data persistenceLayer used by this controller.
     */
    private final PersistenceLayer<DefaultPersistenceBehaviour> persistenceLayer;
    /**
     * {@code True} if the user opted-in to error reporting.
     */
    private final boolean isReportingEnabled;

    /**
     * Creates a new completely initialized object of this class with the current context.
     */
    public MeasurementDeleteController(@NonNull final Context context) {
        this.contextReference = new WeakReference<>(context);
        this.persistenceLayer = new PersistenceLayer<>(context, context.getContentResolver(), AUTHORITY,
                new DefaultPersistenceBehaviour());
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        isReportingEnabled = preferences.getBoolean(ACCEPTED_REPORTING_KEY, false);
    }

    @Override
    protected ListView doInBackground(@NonNull final ListView... params) {

        final ListView view = params[0];

        final List<Measurement> selectedMeasurements = getSelectedMeasurements(view);
        // also delete the folder with the measurement pictures if it exists
        // use getContext only on high APIs
        for (final Measurement measurement : selectedMeasurements) {
            final Context context = contextReference.get();
            Validate.notNull(context);
            final File attachmentsFolder = findMeasurementAttachmentsFolder(measurement);
            if (attachmentsFolder != null) {
                deleteRecursively(context, attachmentsFolder);
            }
            persistenceLayer.delete(measurement.getIdentifier());
        }
        return view;
    }

    @Override
    protected void onPostExecute(@NonNull final ListView listView) {
        super.onPostExecute(listView);
        // Fixes bug where multi selection mode can't left after deleting 2/3 entries
        Log.d(TAG, "onPostExecute -> clearChoices & setChoiceMode to single");
        listView.clearChoices();
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        Toast.makeText(contextReference.get(), R.string.toast_measurement_deletion_success, Toast.LENGTH_LONG).show();
    }

    /**
     * Delete all pictures captured by the Cyface SDK in Pro mode.
     *
     * @param context The current Android context used to access the file system.
     * @param fileOrFolder The picture storage directory.
     */
    private void deleteRecursively(@NonNull final Context context, @NonNull final File fileOrFolder) {
        Log.d(TAG, "deleteRecursively: " + fileOrFolder.getPath());
        if (fileOrFolder.isDirectory()) {
            final File[] files = fileOrFolder.listFiles();
            Validate.notNull(files);
            for (final File child : files) {
                deleteRecursively(context, child);
            }
        }
        final boolean deleteSuccessful = fileOrFolder.delete();
        if (!deleteSuccessful) {
            Log.w(TAG, "Delete was not successful: " + fileOrFolder.getAbsolutePath());
            return;
        }
        informMediaScanner(context, fileOrFolder);
    }

    /**
     * Searches for the folder containing pictures captured during the provided measurement, if any.
     *
     * @param measurement The measurement to search pictures for.
     * @return Either the path to the request folder or <code>null</code> if there are no pictures.
     */
    @Nullable
    private File findMeasurementAttachmentsFolder(@NonNull final Measurement measurement) {
        // If the app was reinstalled the pictures of the old installation were automatically deleted
        final File[] results = new File(externalCyfaceFolderPath(contextReference.get()))
                .listFiles(pathname -> pathname.getName().endsWith("_" + measurement.getIdentifier()));
        if (results != null && results.length > 0) {
            Arrays.sort(results);
            return results[results.length - 1];
        }
        return null;
    }

    /**
     * Finds the selected measurements inside the view. Ignores the ongoing measurement.
     *
     * @param view The view showing the measurements to delete.
     * @return A list with all currently selected measurements in the <code>ListView</code>.
     */
    @NonNull
    private List<Measurement> getSelectedMeasurements(@NonNull final ListView view) {
        final SparseBooleanArray checkedItemPositions = view.getCheckedItemPositions();
        final List<Measurement> ret = new ArrayList<>();

        // Check if measurements are selected
        if (checkedItemPositions.indexOfValue(true) == -1) {
            return ret;
        }

        // Load unfinished measurement
        Measurement unFinishedMeasurement;
        try {
            unFinishedMeasurement = persistenceLayer.loadCurrentlyCapturedMeasurement();
        } catch (final NoSuchMeasurementException e) {
            unFinishedMeasurement = null;
            if (isReportingEnabled) {
                Sentry.captureException(e);
            }
        } catch (final CursorIsNullException e) {
            throw new IllegalStateException(e);
        }

        for (int itemPosition = 0; itemPosition < checkedItemPositions.size(); itemPosition++) {
            if (!checkedItemPositions.valueAt(itemPosition)) {
                Log.w(TAG, "No value for selected item, ignoring");
                continue;
            }
            final int checkedRowNumber = checkedItemPositions.keyAt(itemPosition);
            // TODO [CY-4572]: final Measurement measurement = (Measurement)view.getItemAtPosition(checkedRowNumber);
            final Cursor cursor = (Cursor)view.getItemAtPosition(checkedRowNumber);
            final int identifierColumnIndex = cursor.getColumnIndex(BaseColumns._ID);
            final long selectedMeasurementId = cursor.getLong(identifierColumnIndex);

            // Ignoring the ongoing measurement
            if (unFinishedMeasurement == null || selectedMeasurementId != unFinishedMeasurement.getIdentifier()) {
                final Measurement measurement;
                try {
                    measurement = persistenceLayer.loadMeasurement(selectedMeasurementId);
                } catch (final CursorIsNullException e) {
                    throw new IllegalStateException(e);
                }
                ret.add(measurement);
            }
        }
        return ret;
    }
}
