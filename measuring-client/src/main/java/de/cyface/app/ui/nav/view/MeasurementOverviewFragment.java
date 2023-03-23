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

import static de.cyface.app.ui.Map.TEMPORARY_EVENT_MARKER_ID;
import static de.cyface.app.utils.Constants.AUTHORITY;
import static de.cyface.app.utils.Constants.PACKAGE;
import static de.cyface.app.utils.SharedConstants.PREFERENCES_MODALITY_KEY;
import static de.cyface.app.utils.SharedConstants.PERMISSION_REQUEST_EXTERNAL_STORAGE_FOR_EXPORT;
import static de.cyface.persistence.model.Modality.UNKNOWN;
import static de.cyface.synchronization.BundlesExtrasCodes.MEASUREMENT_ID;

import java.util.ArrayList;
import java.util.List;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.loader.app.LoaderManager;

import de.cyface.app.R;
import de.cyface.app.ui.Map;
import de.cyface.app.ui.dialog.ModalityDialog;
import de.cyface.app.ui.nav.controller.EventDeleteController;
import de.cyface.app.ui.nav.controller.ExportTask;
import de.cyface.app.ui.nav.controller.MeasurementDeleteController;
import de.cyface.app.utils.Constants;
import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.DefaultPersistenceLayer;
import de.cyface.persistence.model.EventType;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.Modality;
import de.cyface.persistence.model.ParcelableGeoLocation;
import de.cyface.persistence.strategy.DefaultLocationCleaning;
import de.cyface.utils.Validate;

/**
 * This {@link Fragment} is used to show all stored measurements in an AdapterView.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 4.5.6
 * @since 1.0.0
 */
public class MeasurementOverviewFragment extends Fragment {

    /**
     * The tag used to identify logging from this class.
     */
    private final static String TAG = PACKAGE + ".mof";
    /**
     * The {@link MeasurementDataList} containing the {@link Measurement}s.
     */
    private MeasurementDataList measurementDataList;
    /**
     * A {@link Map} fragment which is used to visualize {@link Measurement}s.
     */
    private Map map;
    /**
     * The {@link EventDataList} containing the {@link EventType#MODALITY_TYPE_CHANGE}s of a {@link Measurement}.
     */
    private EventDataList eventDataList;
    /**
     * {@code True} if the {@code ListView} below the {@code Map} currently shows the {@code Event}s of a
     * {@code Measurement}.
     */
    private boolean isEventsListShown = false;
    /**
     * The {@link DefaultPersistenceLayer} required to retrieve the {@link Measurement} date from the
     * {@link ParcelableGeoLocation}s
     */
    private DefaultPersistenceLayer<DefaultPersistenceBehaviour> persistenceLayer;
    /**
     * The id used to identify the {@code Loader} responsible for the {@code Measurement}s.
     */
    private final static int MEASUREMENT_LOADER_ID = 0;
    /**
     * The {@code View} required to find layout elements.
     */
    private View view;
    /**
     * The {@code ListView} used to display {@code Measurement}s and their {@code Event}s.
     */
    private ListView listView;
    /**
     * The {@code FloatingActionButton} which allows the user to add {@code EventType#MODALITY_TYPE_CHANGE}s
     */
    private FloatingActionButton addButton;
    /**
     * {@code True} is the user hit the "Add Event" button. This is used e.g. to listen to clicks on the map for
     * selection the marker position.
     */
    private boolean isAddEventActionActive = false;
    /**
     * The identifier for the {@link ModalityDialog} request which asks the user to select a {@link Modality} when he
     * adds a new {@link EventType#MODALITY_TYPE_CHANGE} via UI.
     */
    public final static int DIALOG_ADD_EVENT_MODALITY_SELECTION_REQUEST_CODE = 201909193;
    /**
     * A {@code View.OnClickListener} for the addButton action button used to add {@code Event}s.
     */
    private final View.OnClickListener addButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            final GoogleMap googleMap = map.getGoogleMap();

            // If the addEventAction is already active this means the user confirms the location
            if (isAddEventActionActive) {

                // Only accept the location if there is already a temporary marker
                final Marker temporaryMarker = map.getEventMarker().get(TEMPORARY_EVENT_MARKER_ID);
                if (temporaryMarker != null) {

                    // Show ModalityDialog
                    final FragmentManager fragmentManager = getFragmentManager();
                    Validate.notNull(fragmentManager);
                    final ModalityDialog dialog = new ModalityDialog();
                    dialog.setTargetFragment(MeasurementOverviewFragment.this,
                            DIALOG_ADD_EVENT_MODALITY_SELECTION_REQUEST_CODE);

                    final Long measurementId = eventDataList.getMeasurementId();
                    Validate.notNull(measurementId);
                    dialog.setMeasurementId(measurementId);
                    dialog.setCancelable(true);
                    dialog.show(fragmentManager, "MODALITY_DIALOG");
                    return;
                }
            }

            Toast.makeText(getContext(), R.string.toast_select_location, Toast.LENGTH_SHORT).show();
            isAddEventActionActive = true;
            // Choose location on map
            googleMap.setOnMapClickListener(latLng -> {
                // Check if there is already a temporary Marker and remove it
                final Marker temporaryMarker = map.getEventMarker().get(TEMPORARY_EVENT_MARKER_ID);
                if (temporaryMarker != null) {
                    temporaryMarker.remove();
                }

                // This is a temporary marker as the event is added when the modality is known
                map.addMarker(TEMPORARY_EVENT_MARKER_ID, latLng, UNKNOWN, false);

                // Change addButton to "accept" icon to visualize the user that he can continue
                addButton.hide(); // Workaround for VIC-65 (https://stackoverflow.com/a/52158081/5815054)
                addButton.setImageResource(R.drawable.ic_check);
                addButton.show();
            });
        }
    };

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final FragmentActivity fragmentActivity = getActivity();
        Validate.notNull(fragmentActivity);

        final LoaderManager loaderManager = LoaderManager.getInstance(this);
        loaderManager.initLoader(MEASUREMENT_LOADER_ID, null, measurementDataList);
        Log.d(TAG, "onActivityCreated() done");
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {

        view = inflater.inflate(R.layout.fragment_measurements, container, false);
        persistenceLayer = new DefaultPersistenceLayer<>(inflater.getContext(), AUTHORITY, new DefaultPersistenceBehaviour());

        map = new Map(view.findViewById(R.id.mapView), savedInstanceState, () -> {
            // Nothing to do
        });

        measurementDataList = new MeasurementDataList(getActivity(), persistenceLayer, this, map);
        measurementDataList.onCreateView(view);

        eventDataList = new EventDataList(getActivity(), persistenceLayer, null, map);
        eventDataList.onCreateView(view);

        listView = view.findViewById(R.id.measurements_list_view);
        addButton = view.findViewById(R.id.add_button);
        addButton.setOnClickListener(addButtonClickListener);

        return view;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    public void onPause() {
        map.onPause();
        super.onPause();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.measurement_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final FragmentActivity fragmentActivity = getActivity();
        Validate.notNull(fragmentActivity);
        if (item.getItemId() == R.id.export_menu_item) {
            // Permission requirements: https://developer.android.com/training/data-storage
            final boolean requiresWritePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
            final boolean missingPermissions = ContextCompat.checkSelfPermission(fragmentActivity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(fragmentActivity,
                            Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
            if (requiresWritePermission && missingPermissions) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(
                            new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    Manifest.permission.READ_EXTERNAL_STORAGE},
                            PERMISSION_REQUEST_EXTERNAL_STORAGE_FOR_EXPORT);
                } else {
                    Toast.makeText(fragmentActivity, fragmentActivity.getString(R.string.export_data_no_permission),
                            Toast.LENGTH_LONG).show();
                }
            } else {
                new ExportTask(fragmentActivity).execute();
            }
            return true;
        } else if (item.getItemId() == R.id.select_all_item) {
            selectAllItems();
            return true;
        } else if (item.getItemId() == R.id.delete_measurement_item) {
            if (isEventsListShown) {
                deleteSelectedEvents(fragmentActivity, eventDataList.getListView());
            } else {
                deleteSelectedMeasurements(fragmentActivity, measurementDataList.getListView());
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void deleteSelectedEvents(@NonNull final FragmentActivity fragmentActivity,
            @NonNull final ListView eventsView) {

        if (eventsView.getCheckedItemCount() < 1) {
            Toast.makeText(getActivity(), fragmentActivity.getString(R.string.delete_data_non_selected),
                    Toast.LENGTH_LONG).show();
            return;
        }

        new EventDeleteController(fragmentActivity)
                .execute(new EventDeleteController.EventDeleteControllerParameters(eventsView, map));
    }

    private void deleteSelectedMeasurements(@NonNull final FragmentActivity fragmentActivity,
            @NonNull final ListView measurementsView) {

        if (measurementsView.getCheckedItemCount() < 1) {
            Toast.makeText(getActivity(), fragmentActivity.getString(R.string.delete_data_non_selected),
                    Toast.LENGTH_LONG).show();
            return;
        }
        // TODO [CY-4572]: Validate.isTrue((measurementListView.getAdapter() instanceof MeasurementAdapter));

        new MeasurementDeleteController(fragmentActivity).execute(measurementsView);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
    }

    /**
     * Sets all list items in {@link MeasurementDataList#getListView()} or the {@link EventDataList#getListView()} to
     * selected.
     */
    private void selectAllItems() {

        final ListView listView = isEventsListShown ? eventDataList.getListView()
                : measurementDataList.getListView();
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        final ListAdapter adapter = listView.getAdapter();
        if (adapter != null) {
            for (int i = 0; i < adapter.getCount(); i++) {
                listView.setItemChecked(i, true);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions,
            @NonNull final int[] grantResults) {
        final FragmentActivity fragmentActivity = getActivity();
        Validate.notNull(fragmentActivity);

        // noinspection SwitchStatementWithTooFewBranches - for readability
        switch (requestCode) {
            case PERMISSION_REQUEST_EXTERNAL_STORAGE_FOR_EXPORT:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(fragmentActivity, fragmentActivity.getString(R.string.export_data),
                            Toast.LENGTH_LONG).show();
                    new ExportTask(fragmentActivity).execute();
                } else {
                    Toast.makeText(fragmentActivity, fragmentActivity.getString(R.string.export_data_no_permission),
                            Toast.LENGTH_LONG).show();
                }
                break;
            default: {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }
    }

    /**
     * @return {@code True} if the onBackPressed was handled, {@code False} is it still needs to be handled by the
     *         calling method.
     */
    public boolean onBackPressed() {
        if (isEventsListShown) {
            // Just cancel the "add event" action if it's active
            if (isAddEventActionActive) {
                cancelAddEventAction();
                return true;
            }

            isEventsListShown = false;
            addButton.hide();
            map.clearMap();

            // Without this the listView.getCheckedItemCount() shows too many (i.e. is not updated)
            measurementDataList = new MeasurementDataList(getActivity(), persistenceLayer, this, map);
            measurementDataList.onCreateView(view);
            listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

            final AppCompatActivity activity = ((AppCompatActivity)getActivity());
            Validate.notNull(activity);
            final ActionBar actionBar = activity.getSupportActionBar();
            Validate.notNull(actionBar);
            actionBar.setTitle(getString(R.string.drawer_title_measurements));
            LoaderManager.getInstance(this).restartLoader(MEASUREMENT_LOADER_ID, null, measurementDataList);
            return true;
        }
        return false;
    }

    private void cancelAddEventAction() {
        isAddEventActionActive = false;
        map.getGoogleMap().setOnMapClickListener(null);
        final Marker temporaryMarker = map.getEventMarker().get(TEMPORARY_EVENT_MARKER_ID);
        if (temporaryMarker != null) {
            temporaryMarker.remove();
        }
        addButton.hide(); // Workaround for VIC-65 (https://stackoverflow.com/a/52158081/5815054)
        addButton.setImageResource(R.drawable.ic_add);
        addButton.show();
    }

    void showEvents(final long measurementId) {
        if (isEventsListShown) {
            throw new IllegalStateException("isEventsListShown is already true.");
        }
        isEventsListShown = true;
        addButton.show();

        Log.d(TAG, "showEvents() of mid " + measurementId);
        eventDataList.setMeasurementId(measurementId);

        // Without this the listView.getCheckedItemCount() shows too many (i.e. is not updated)
        eventDataList = new EventDataList(getActivity(), persistenceLayer, measurementId, map);
        eventDataList.onCreateView(view);

        final AppCompatActivity activity = ((AppCompatActivity)getActivity());
        Validate.notNull(activity);
        final ActionBar actionBar = activity.getSupportActionBar();
        Validate.notNull(actionBar);
        actionBar.setTitle(getString(R.string.measurement) + " " + measurementId);
        LoaderManager.getInstance(this).restartLoader(MEASUREMENT_LOADER_ID, null, eventDataList);
    }

    /**
     * Called when a {@link Modality} was selected in the {@link ModalityDialog}. This happens when the user adds a new
     * {@link EventType#MODALITY_TYPE_CHANGE} via the UI, see {@link #addButtonClickListener}.
     *
     * @param data an intent which may contain result data
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == DIALOG_ADD_EVENT_MODALITY_SELECTION_REQUEST_CODE) {

            final long measurementId = data.getLongExtra(MEASUREMENT_ID, -1L);
            Validate.isTrue(measurementId != -1L);
            final String modalityId = data.getStringExtra(PREFERENCES_MODALITY_KEY);
            final Modality modality = Modality.valueOf(modalityId);
            Validate.notNull(modality);

            final Marker temporaryMarker = map.getEventMarker().get(TEMPORARY_EVENT_MARKER_ID);
            Validate.notNull(temporaryMarker);
            final LatLng markerPosition = temporaryMarker.getPosition();
            final Location markerLocation = new Location("marker");
            markerLocation.setLatitude(markerPosition.latitude);
            markerLocation.setLongitude(markerPosition.longitude);

            // Load GeoLocations
            final List<ParcelableGeoLocation> geoLocations = new ArrayList<>();
            var tracks = persistenceLayer.loadTracks(measurementId, new DefaultLocationCleaning());
            for (final var track : tracks) {
                geoLocations.addAll(track.getGeoLocations());
            }

            // Search for the nearest GeoLocation
            Double minDistance = null;
            ParcelableGeoLocation nearestGeoLocation = null;
            for (final ParcelableGeoLocation geoLocation : geoLocations) {
                final Location location = new Location("geoLocation");
                location.setLatitude(geoLocation.getLat());
                location.setLongitude(geoLocation.getLon());
                final double distance = markerLocation.distanceTo(location);

                if (minDistance == null || distance < minDistance) {
                    minDistance = distance;
                    nearestGeoLocation = geoLocation;
                }
            }

            // Do nothing if there is no location to reference the marker to
            if (nearestGeoLocation == null) {
                Toast.makeText(getContext(), R.string.toast_no_locations_found, Toast.LENGTH_LONG).show();
                cancelAddEventAction();
                return;
            }

            // Add new Event to database
            final Measurement measurement;
            final long eventId;
            measurement = persistenceLayer.loadMeasurement(measurementId);
            eventId = persistenceLayer.logEvent(
                    EventType.MODALITY_TYPE_CHANGE, measurement,
                    nearestGeoLocation.getTimestamp(), modality.getDatabaseIdentifier());
            Log.d(TAG, "Event added, id: " + eventId + " timestamp: " + nearestGeoLocation.getTimestamp());

            // Add new Marker to map
            final LatLng latLng = new LatLng(nearestGeoLocation.getLat(), nearestGeoLocation.getLon());
            map.addMarker(eventId, latLng, modality, true);
            Toast.makeText(getContext(), R.string.toast_item_created_on_tracks_nearest_position, Toast.LENGTH_LONG)
                    .show();

            cancelAddEventAction();
        }
    }
}
