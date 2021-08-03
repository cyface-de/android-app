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
package de.cyface.app.ui;

import static de.cyface.app.utils.Constants.ACCEPTED_REPORTING_KEY;
import static de.cyface.app.utils.Constants.TAG;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import org.apache.commons.lang3.Validate;

import de.cyface.app.R;
import de.cyface.app.ui.nav.view.CursorMeasureAdapter;
import de.cyface.app.utils.Constants;
import de.cyface.persistence.model.Event;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Modality;
import de.cyface.persistence.model.Track;
import io.sentry.Sentry;

/**
 * The Map class handles everything around the GoogleMap view of {@link MainFragment}.
 *
 * @author Armin Schnabel
 * @version 3.0.2
 * @since 1.0.0
 */
public class Map implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {

    /**
     * The visualization library in use.
     */
    private GoogleMap googleMap;
    /**
     * The {@code Context} of this application.
     */
    private final Context applicationContext;
    /**
     * The {@code GoogleApiClient} required to retrieve the last location which is used to as the center of the map when
     * it's loaded.
     */
    private GoogleApiClient googleApiClient;
    /**
     * The newest available position.
     */
    private LatLng currentLocation;
    /**
     * The {@code MapView} element of the {@code GoogleMap}.
     */
    private final MapView view;
    /**
     * {@code True} if the "auto-center map" feature is currently enabled.
     */
    private boolean isAutoCenterMapEnabled;
    /**
     * The {@code SharedPreferences} used to store the user's preferences.
     */
    private final SharedPreferences preferences;
    /**
     * The {@code Runnable} triggered when the {@code GoogleMap} is loaded and ready.
     */
    private final Runnable onMapReadyRunnable;
    /**
     * All currently shown {@code Event} {@code Marker}s with a reference between both required to find the Marker of a
     * Event.
     */
    private final HashMap<Long, Marker> eventMarker = new HashMap<>();
    /**
     * The id of the {@code Marker} used when the user adds a new {@code Marker} to the map which can still
     * be replaced or removed.
     */
    public final static long TEMPORARY_EVENT_MARKER_ID = -1L;
    /**
     * {@code True} if the user opted-in to error reporting.
     */
    private final boolean isReportingEnabled;

    /**
     * @param view The {@code MapView} element of the {@code GoogleMap}.
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous saved state as given
     *            here
     * @param onMapReadyRunnable The {@code Runnable} triggered when the {@code GoogleMap} is loaded and ready.
     */
    public Map(final MapView view, final Bundle savedInstanceState, @NonNull final Runnable onMapReadyRunnable) {
        this.view = view;
        view.onCreate(savedInstanceState);
        final Activity activity = (Activity)view.getContext();
        this.applicationContext = activity.getApplicationContext();
        this.onMapReadyRunnable = onMapReadyRunnable;
        if (currentLocation == null) {
            this.currentLocation = new LatLng(51.027852, 13.720864);
        }
        MapsInitializer.initialize(applicationContext);
        view.getMapAsync(this);

        preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        isReportingEnabled = preferences.getBoolean(ACCEPTED_REPORTING_KEY, false);
        isAutoCenterMapEnabled = preferences.getBoolean(Constants.PREFERENCES_MOVE_TO_LOCATION_KEY, false);
    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {
        Validate.notNull(googleMap);
        this.googleMap = googleMap;
        googleMap.setMaxZoomPreference(2.0f);
        googleMap.setMaxZoomPreference(20.0f);
        showAndMoveToCurrentLocation(false);
        onMapReadyRunnable.run();
    }

    /**
     * Renders the provided {@param tracks} and {@param events} onto this classes map.
     *
     * @param tracks a list of {@link Track}s which can be rendered to a map
     * @param events a list of {@link Event}s which can be rendered to a map
     * @param moveCameraToBounds {@code True} if the camera of the map should be moved to the track boundaries
     */
    public void renderMeasurement(@NonNull final List<Track> tracks, @NonNull final List<Event> events,
            final boolean moveCameraToBounds) {
        googleMap.clear();

        // Calculate geo boundaries
        final LatLngBounds.Builder builder = new LatLngBounds.Builder();
        int positions = 0;

        // Iterate through the sub tracks and their points
        final List<GeoLocation> allLocations = new ArrayList<>();
        for (final Track track : tracks) {
            final PolylineOptions subTrack = new PolylineOptions();
            for (final GeoLocation location : track.getGeoLocations()) {
                allLocations.add(location);
                final LatLng position = new LatLng(location.getLat(), location.getLon());
                subTrack.add(position);
                builder.include(position);
                positions++;
            }

            // Add sub-tracks to map
            if (subTrack.getPoints().size() > 1) {
                googleMap.addPolyline(subTrack);
            }
        }

        renderEvents(allLocations, events);

        if (moveCameraToBounds && positions <= 1) {
            Toast.makeText(applicationContext, R.string.toast_no_locations_found, Toast.LENGTH_SHORT).show();
        }

        if (moveCameraToBounds && positions > 0) {
            // Move map view to track boundaries
            final LatLngBounds bounds = builder.build();
            final CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 40);
            googleMap.moveCamera(cameraUpdate);
        }
    }

    private void renderEvents(@NonNull final List<GeoLocation> allLocations, @NonNull final List<Event> events) {

        // Iterate through the events and select GeoLocations for each event to be rendered on the map
        final Iterator<GeoLocation> locationIterator = allLocations.iterator();
        GeoLocation previousLocation = null;
        GeoLocation nextLocation = null;
        for (final Event event : events) {
            MarkerOptions markerOptions = null;
            final String markerTitle = applicationContext.getString(R.string.modality_type) + ": "
                    + CursorMeasureAdapter.getTranslation(new WeakReference<>(applicationContext),
                            Modality.valueOf(event.getValue()));

            // Check if last Event's nextLocation can be used for this Event
            if (nextLocation != null && nextLocation.getTimestamp() >= event.getTimestamp()) {
                markerOptions = new MarkerOptions()
                        .position(new LatLng(nextLocation.getLat(), nextLocation.getLon())).title(markerTitle);
            } else {
                // Iterate until Event's nextLocation is reached
                while (locationIterator.hasNext()) {
                    final GeoLocation location = locationIterator.next();
                    if (location.getTimestamp() < event.getTimestamp()) {
                        previousLocation = location;
                        continue;
                    }

                    // Event's nextLocation reached
                    nextLocation = location;
                    markerOptions = new MarkerOptions()
                            .position(new LatLng(nextLocation.getLat(), nextLocation.getLon())).title(markerTitle);
                    break; // from location loop
                }
            }

            // Add Event to map
            if (markerOptions != null) {
                final Marker marker = googleMap.addMarker(markerOptions);
                eventMarker.put(event.getIdentifier(), marker);
                continue; // with next event
            }

            // No nextLocation for Event found
            Validate.isTrue(!locationIterator.hasNext());

            // Use previousLocation to show last
            if (previousLocation != null) {
                markerOptions = new MarkerOptions()
                        .position(new LatLng(previousLocation.getLat(), previousLocation.getLon())).title(markerTitle);
                final Marker marker = googleMap.addMarker(markerOptions);
                eventMarker.put(event.getIdentifier(), marker);
                continue; // with next event
            }

            Log.d(TAG, "renderMeasurement(): Ignoring event as there are no GeoLocations to choose a position.");
        }
    }

    public HashMap<Long, Marker> getEventMarker() {
        return eventMarker;
    }

    public GoogleMap getGoogleMap() {
        return googleMap;
    }

    public void removeEventMarker(final long eventId) {
        final Marker marker = eventMarker.get(eventId);
        if (marker != null) {
            marker.remove(); // from map
        }
        eventMarker.remove(eventId); // from cache

    }

    public void clearMap() {
        googleMap.clear();
        eventMarker.clear();
    }

    /**
     * Shows the user's location on the map and moves the camera to that position.
     *
     * @param permissionWereJustGranted {@code True} if the permissions were just granted. In this case we expect the
     *            permissions to be available and track if otherwise.
     */
    void showAndMoveToCurrentLocation(final boolean permissionWereJustGranted) {
        try {
            // Create an instance of GoogleAPIClient
            if (googleApiClient == null) {
                googleApiClient = new GoogleApiClient.Builder(applicationContext).addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this).addApi(LocationServices.API).build();
            }
            googleApiClient.connect();
            // Happens when Google Play Services on phone is out of date
            if (googleMap != null) {
                googleMap.setMyLocationEnabled(true);
            }
        } catch (final SecurityException e) {
            if (permissionWereJustGranted) {
                Log.w(TAG, "showAndMoveToCurrentLocation: Location permission are missing");
                if (isReportingEnabled) {
                    Sentry.captureException(e);
                }
            }
            // This happens as the Map is shown in background when asking for location permission
            // There is no need to track this event as long as the permissions were not just granted
        }
    }

    /**
     * Moves the GoogleMap camera to the last location.
     *
     * @param highFrequentRequest {@code True} if this is called very frequently, e.g. on each location update. In this
     *            case no Exception tracking event is sent to Sentry to limit Sentry quota.
     */
    private void moveToLocation(final boolean highFrequentRequest) {
        try {
            final Location location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
            if (location == null) {
                // This happens as the Map is shown in background when asking for location permission
                // There is no need to track this event
                return;
            }

            currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
            final CameraPosition.Builder builder = new CameraPosition.Builder().target(currentLocation);
            CameraPosition.Builder builder1 = builder.zoom(17);
            CameraPosition cameraPosition = builder1.build();

            // Occurred on Huawei CY-3456
            if (googleMap == null) {
                Log.w(TAG, "GoogleMap is null, unable to animate camera");
                if (!highFrequentRequest && isReportingEnabled) {
                    Sentry.captureMessage("Map.moveToLocation: GoogleMap is null");
                }
                return;
            }

            googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        } catch (final SecurityException e) {
            Log.e(TAG, "Location permission not granted or Google play service out of date?");
            if (!highFrequentRequest && isReportingEnabled) {
                Sentry.captureException(e);
            }
        }
    }

    void setAutoCenterMapEnabled(final boolean autoCenterMapEnabled) {
        this.isAutoCenterMapEnabled = autoCenterMapEnabled;
    }

    @Override
    public void onConnected(@Nullable final Bundle bundle) {
        moveToLocation(false);
    }

    @Override
    public void onConnectionSuspended(final int i) {
        // Nothing to do here
    }

    @Override
    public void onConnectionFailed(@NonNull final ConnectionResult connectionResult) {
        // Nothing to do here
    }

    public void onResume() {
        view.onResume();
        if (googleMap == null) {
            return;
        }

        if (ActivityCompat.checkSelfPermission(applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(applicationContext,
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        isAutoCenterMapEnabled = preferences.getBoolean(Constants.PREFERENCES_MOVE_TO_LOCATION_KEY, false);
        googleMap.setMyLocationEnabled(true);
    }

    public void onPause() {
        if (ActivityCompat.checkSelfPermission(applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(applicationContext,
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (googleMap != null) {
            googleMap.setMyLocationEnabled(false);
        }
    }

    @Override
    public void onLocationChanged(final Location location) {
        if (isAutoCenterMapEnabled) {
            moveToLocation(true);
        }
    }

    @Override
    public void onStatusChanged(final String s, final int i, final Bundle bundle) {
        // Nothing to do here
    }

    @Override
    public void onProviderEnabled(final String s) {
        // Nothing to do here
    }

    @Override
    public void onProviderDisabled(final String s) {
        // Nothing to do here
    }

    /**
     * Adds a {@link Event.EventType#MODALITY_TYPE_CHANGE} {@code Event} {@code Marker} to the {@code Map}.
     *
     * @param eventId the identifier of the {@code Event} or {@link #TEMPORARY_EVENT_MARKER_ID} if this is only a
     *            temporary marker
     *            until the {@param eventId} is known.
     * @param latLng the {@code LatLng} of the marker to be added
     * @param modality the new {@code Modality} of the {@link Event.EventType#MODALITY_TYPE_CHANGE} marker to be added
     * @param isMarkerToBeFocused {@code True} if the newly added {@code Marker} is to be focused after creation
     */
    public void addMarker(final long eventId, @NonNull final LatLng latLng, @NonNull final Modality modality,
            final boolean isMarkerToBeFocused) {
        final String markerTitle = applicationContext.getString(R.string.modality_type) + ": "
                + CursorMeasureAdapter.getTranslation(new WeakReference<>(applicationContext), modality);
        final MarkerOptions markerOptions = new MarkerOptions()
                .position(new LatLng(latLng.latitude, latLng.longitude)).title(markerTitle);
        final Marker marker = googleMap.addMarker(markerOptions);
        eventMarker.put(eventId, marker);
        if (isMarkerToBeFocused) {
            focusMarker(marker);
        }
    }

    /**
     * Moves the camera of the map to the {@param marker} position and shows the Marker title.
     *
     * @param marker The {@code Marker} which is to be focused.
     */
    public void focusMarker(@NonNull final Marker marker) {
        marker.showInfoWindow();
        final LatLng markerLatLng = marker.getPosition();
        final GoogleMap googleMap = getGoogleMap();
        final CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLng(markerLatLng);
        googleMap.moveCamera(cameraUpdate);
    }
}
