/*
 * Copyright 2023 Cyface GmbH
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
package de.cyface.app.ui.button;

import static de.cyface.app.utils.Constants.TAG;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;

import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.utils.CursorIsNullException;

/**
 * {@code AsyncTask} to {@link PersistenceLayer#loadAscend(long)}.
 * <p>
 * We use an {@code AsyncTask} because this is blocking and cannot run on the main thread.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.3.0
 */
public final class AscendLoader extends AsyncTask<AscendLoader.InputParameters, Void, AscendLoader.OutputParameters> {

    /**
     * The {@link PersistenceLayer} to use to load the data
     */
    private final PersistenceLayer<DefaultPersistenceBehaviour> persistenceLayer;

    /**
     * Creates a new completely initialized object of this class with the current context.
     *
     * @param persistenceLayer The {@link PersistenceLayer} to use to load the data.
     */
    public AscendLoader(@NonNull final PersistenceLayer<DefaultPersistenceBehaviour> persistenceLayer) {
        super(); // Added
        this.persistenceLayer = persistenceLayer;
    }

    @Override
    protected OutputParameters doInBackground(@NonNull final AscendLoader.InputParameters... params) {
        try {
            final long measurementId = params[0].measurementId;
            final Double ascend = persistenceLayer.loadAscend(measurementId);
            final Double ascendGnss = persistenceLayer.loadAscend(measurementId, true);
            return new OutputParameters(params[0].textView, ascend, ascendGnss, params[0].prefix);
        } catch (CursorIsNullException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    protected void onPostExecute(@NonNull final OutputParameters params) {
        super.onPostExecute(params);

        final Double ascend = params.ascend;
        final Double ascendGnss = params.ascendGnss;
        String ascendText = null;
        if (ascend != null) {
            ascendText = "+" + Math.round(params.ascend) + " m";
            if (ascendGnss != null) {
                ascendText += " (" + Math.round(params.ascendGnss) + " m)";
            }
        }
        if (ascendText != null) {
            Log.d(TAG, "Ascend update: " + ascendText);
            params.textView.setText(String.format("%s %s",params.prefix, ascendText));
        }
    }

    /**
     * The parameters required to execute this task.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 6.3.0
     */
    public static class InputParameters {
        /**
         * The id of the measurement to load the ascend for.
         */
        final long measurementId;
        /**
         * The view to update after the ascend was loaded.
         */
        final TextView textView;
        /**
         * Prefix to add when setting {@link #textView}.
         */
        final String prefix;

        /**
         * Creates a fully initialized instance of this class.
         *
         * @param measurementId The id of the measurement to load the ascend for.
         * @param textView The view to update after the ascend was loaded.
         * @param prefix Prefix to add when setting {@link #textView}.
         */
        public InputParameters(final long measurementId, final TextView textView, final String prefix) {
            this.measurementId = measurementId;
            this.textView = textView;
            this.prefix = prefix;
        }

        /**
         * Creates a fully initialized instance of this class.
         *
         * @param measurementId The id of the measurement to load the ascend for.
         * @param textView The view to update after the ascend was loaded.
         */
        public InputParameters(final long measurementId, final TextView textView) {
            this.measurementId = measurementId;
            this.textView = textView;
            this.prefix = "";
        }
    }

    /**
     * The parameters {@code #doInBackground(InputParameters...)} passes to {@code #onPostExecute(OutputParameters)}
     * where the UI is changed after execution.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 6.3.0
     */
    public static class OutputParameters {
        /**
         * The view to update after the ascend was loaded.
         */
        final TextView textView;
        /**
         * The calculated ascend in meters.
         */
        final Double ascend;
        /**
         * The calculated ascend in meters based on GNSS data.
         */
        final Double ascendGnss;
        /**
         * Prefix to add when setting {@link #textView}.
         */
        final String prefix;

        /**
         * Creates a fully initialized instance of this class.
         *
         * @param textView The view to update after the ascend was loaded.
         * @param ascend The calculated ascend in meters.
         * @param ascendGnss The calculated ascend in meters based on GNSS data
         * @param prefix Prefix to add when setting {@link #textView}.
         */
        public OutputParameters(final TextView textView, final Double ascend, final Double ascendGnss,
                final String prefix) {
            this.textView = textView;
            this.ascend = ascend;
            this.ascendGnss = ascendGnss;
            this.prefix = prefix;
        }
    }
}
