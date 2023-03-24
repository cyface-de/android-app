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
package de.cyface.app.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import de.cyface.app.utils.SharedConstants.ACCEPTED_REPORTING_KEY
import de.cyface.app.utils.SharedConstants.TAG
import de.cyface.persistence.model.Event
import de.cyface.persistence.model.Modality
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.persistence.model.Track
import de.cyface.utils.Validate
import io.sentry.Sentry
import java.lang.ref.WeakReference


/**
 * The Map class handles everything around the GoogleMap view.
 *
 * @author Armin Schnabel
 * @version 4.0.0
 * @since 1.0.0
 * @property view The `MapView` element of the `GoogleMap`.
 * @property onMapReadyRunnable The `Runnable` triggered when the `GoogleMap` is loaded and ready.
 */
class Map(
    private val view: MapView,
    savedInstanceState: Bundle?,
    onMapReadyRunnable: Runnable
) : OnMapReadyCallback, LocationListener {
    /**
     * The visualization library in use.
     */
    var googleMap: GoogleMap? = null

    /**
     * The `Context` of this application.
     */
    private val applicationContext: Context

    /**
     * The client to retrieve the last location from to center the map.
     */
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    /**
     * The newest available position.
     */
    private var currentLocation: LatLng? = null

    /**
     * `True` if the "auto-center map" feature is currently enabled.
     */
    var isAutoCenterMapEnabled: Boolean

    /**
     * The `SharedPreferences` used to store the user's preferences.
     */
    private val preferences: SharedPreferences

    /**
     * The `Runnable` triggered when the `GoogleMap` is loaded and ready.
     */
    private val onMapReadyRunnable: Runnable

    /**
     * All currently shown `Event` `Marker`s with a reference between both required to find the Marker of a
     * Event.
     */
    val eventMarker = HashMap<Long, Marker?>()

    /**
     * `True` if the user opted-in to error reporting.
     */
    private val isReportingEnabled: Boolean

    init {
        view.onCreate(savedInstanceState)
        val activity = view.context as Activity
        applicationContext = activity.applicationContext
        this.onMapReadyRunnable = onMapReadyRunnable
        if (currentLocation == null) {
            currentLocation = LatLng(51.027852, 13.720864)
        }
        view.getMapAsync(this)
        preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        isReportingEnabled = preferences.getBoolean(ACCEPTED_REPORTING_KEY, false)
        //FIXME: preferences.getBoolean(Constants.PREFERENCES_MOVE_TO_LOCATION_KEY, false)
        isAutoCenterMapEnabled = true
    }

    private fun requestLocationUpdates() {
        val activity = view.context as Activity
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity)
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000).build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                // The GMap location does not work on emulator, see bug report: https://issuetracker.google.com/issues/242438611
                if (locationResult.locations.size > 0) {
                    moveToLocation(true,
                        locationResult.locations[locationResult.locations.size - 1]
                    )
                }
            }
        }
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw java.lang.IllegalStateException("Missing permissions")
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        if (googleMap != null) {
            googleMap!!.isMyLocationEnabled = true
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        Validate.notNull(googleMap)
        this.googleMap = googleMap
        onMapReady()
    }

    fun onMapReady() {
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap!!.setMaxZoomPreference(2.0f)
            googleMap!!.setMaxZoomPreference(20.0f)
            requestLocationUpdates()
            showAndMoveToCurrentLocation(false)
            onMapReadyRunnable.run()
        } else {
            Log.d(TAG, "onMapReady: permissions not granted, skipping")
        }
    }

    /**
     * Renders the provided {@param tracks} and {@param events} onto this classes map.
     *
     * @param tracks a list of [Track]s which can be rendered to a map
     * @param events a list of [Event]s which can be rendered to a map
     * @param moveCameraToBounds `True` if the camera of the map should be moved to the track boundaries
     */
    fun renderMeasurement(
        tracks: List<Track>, events: List<Event>,
        moveCameraToBounds: Boolean
    ) {
        googleMap!!.clear()

        // Calculate geo boundaries
        val builder = LatLngBounds.Builder()
        var positions = 0

        // Iterate through the sub tracks and their points
        val allLocations = ArrayList<ParcelableGeoLocation?>()
        for ((geoLocations) in tracks) {
            val subTrack = PolylineOptions()
            for (location in geoLocations) {
                allLocations.add(location)
                val position = LatLng(location!!.lat, location.lon)
                subTrack.add(position)
                builder.include(position)
                positions++
            }

            // Add sub-tracks to map
            if (subTrack.points.size > 1) {
                googleMap!!.addPolyline(subTrack)
            }
        }
        // FIXME: renderEvents(allLocations, events)
        if (moveCameraToBounds && positions <= 1) {
            Toast.makeText(
                applicationContext,
                R.string.toast_no_locations_found,
                Toast.LENGTH_SHORT
            ).show()
        }
        if (moveCameraToBounds && positions > 0) {
            // Move map view to track boundaries
            val bounds = builder.build()
            val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 40)
            googleMap!!.moveCamera(cameraUpdate)
        }
    }

    private fun renderEvents(
        allLocations: List<ParcelableGeoLocation?>,
        events: List<Event>
    ) {

        // Iterate through the events and select GeoLocations for each event to be rendered on the map
        val locationIterator = allLocations.iterator()
        var previousLocation: ParcelableGeoLocation? = null
        var nextLocation: ParcelableGeoLocation? = null
        for ((id, timestamp, _, value) in events) {
            var markerOptions: MarkerOptions? = null
            val modalityKey = applicationContext.getString(R.string.modality_type)
            val modality =
                getTranslation(WeakReference(applicationContext), Modality.valueOf(value!!))
            val markerTitle = "$modalityKey : $modality"

            // Check if last Event's nextLocation can be used for this Event
            if (nextLocation != null && nextLocation.timestamp >= timestamp) {
                markerOptions = MarkerOptions()
                    .position(LatLng(nextLocation.lat, nextLocation.lon)).title(markerTitle)
            } else {
                // Iterate until Event's nextLocation is reached
                while (locationIterator.hasNext()) {
                    val location = locationIterator.next()
                    if (location!!.timestamp < timestamp) {
                        previousLocation = location
                        continue
                    }

                    // Event's nextLocation reached
                    nextLocation = location
                    markerOptions = MarkerOptions()
                        .position(LatLng(nextLocation.lat, nextLocation.lon)).title(markerTitle)
                    break // from location loop
                }
            }

            // Add Event to map
            if (markerOptions != null) {
                val marker = googleMap!!.addMarker(markerOptions)
                eventMarker[id] = marker
                continue  // with next event
            }

            // No nextLocation for Event found
            Validate.isTrue(!locationIterator.hasNext())

            // Use previousLocation to show last
            if (previousLocation != null) {
                markerOptions = MarkerOptions()
                    .position(LatLng(previousLocation.lat, previousLocation.lon)).title(markerTitle)
                val marker = googleMap!!.addMarker(markerOptions)
                eventMarker[id] = marker
                continue  // with next event
            }
            Log.d(
                TAG,
                "renderMeasurement(): Ignoring event as there are no GeoLocations to choose a position."
            )
        }
    }

    @Suppress("unused")
    fun removeEventMarker(eventId: Long) {
        val marker = eventMarker[eventId]
        marker?.remove()
        eventMarker.remove(eventId) // from cache
    }

    fun clearMap() {
        googleMap!!.clear()
        eventMarker.clear()
    }

    /**
     * Shows the user's location on the map and moves the camera to that position.
     *
     * @param permissionWereJustGranted `True` if the permissions were just granted. In this case we expect the
     * permissions to be available and track if otherwise.
     */
    fun showAndMoveToCurrentLocation(permissionWereJustGranted: Boolean) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    moveToLocation(false, location)
                }
            }
            // Happens when Google Play Services on phone is out of date
            if (googleMap != null) {
                googleMap!!.isMyLocationEnabled = true
            }
        } catch (e: SecurityException) {
            if (permissionWereJustGranted) {
                Log.w(TAG, "showAndMoveToCurrentLocation: Location permission are missing")
                if (isReportingEnabled) {
                    Sentry.captureException(e)
                }
            }
        }
    }

    /**
     * Moves the GoogleMap camera to the last location.
     *
     * @param highFrequentRequest `True` if this is called very frequently, e.g. on each location update. In this
     * case no Exception tracking event is sent to Sentry to limit Sentry quota.
     */
    private fun moveToLocation(highFrequentRequest: Boolean, location: Location) {
        try {
            /*val location = LocationServices.FusedLocationApi.getLastLocation(
                googleApiClient!!
            )
                ?: // This happens as the Map is shown in background when asking for location permission
                // There is no need to track this event
                return
             */
            currentLocation = LatLng(location.latitude, location.longitude)
            val builder = CameraPosition.Builder().target(currentLocation!!)
            val builder1 = builder.zoom(17f)
            val cameraPosition = builder1.build()

            // Occurred on Huawei CY-3456
            if (googleMap == null) {
                Log.w(TAG, "GoogleMap is null, unable to animate camera")
                if (!highFrequentRequest && isReportingEnabled) {
                    Sentry.captureMessage("Map.moveToLocation: GoogleMap is null")
                }
                return
            }
            googleMap!!.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted or Google play service out of date?")
            if (!highFrequentRequest && isReportingEnabled) {
                Sentry.captureException(e)
            }
        }
    }

    fun onResume() {
        view.onResume()
        if (googleMap == null) {
            return
        }
        requestLocationUpdates()
        //FIXME: preferences.getBoolean(Constants.PREFERENCES_MOVE_TO_LOCATION_KEY, false)
        isAutoCenterMapEnabled = true
    }

    fun onPause() {
        stopLocationUpdates()
    }

    private fun stopLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (this::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        if (googleMap != null) {
            googleMap!!.isMyLocationEnabled = false
        }
    }

    override fun onLocationChanged(location: Location) {
        if (isAutoCenterMapEnabled) {
            moveToLocation(true, location)
        }
    }

    @Deprecated("This callback will never be invoked on Android Q and above.")
    override fun onStatusChanged(s: String, i: Int, bundle: Bundle) {
        // Nothing to do here
    }

    override fun onProviderEnabled(s: String) {
        // Nothing to do here
    }

    override fun onProviderDisabled(s: String) {
        // Nothing to do here
    }

    /**
     * Adds a [EventType.MODALITY_TYPE_CHANGE] `Event` `Marker` to the `Map`.
     *
     * @param eventId the identifier of the `Event` or [.TEMPORARY_EVENT_MARKER_ID] if this is only a
     * temporary marker
     * until the {@param eventId} is known.
     * @param latLng the `LatLng` of the marker to be added
     * @param modality the new `Modality` of the [EventType.MODALITY_TYPE_CHANGE] marker to be added
     * @param isMarkerToBeFocused `True` if the newly added `Marker` is to be focused after creation
     */
    fun addMarker(
        eventId: Long, latLng: LatLng, modality: Modality,
        isMarkerToBeFocused: Boolean
    ) {
        val modalityKey = applicationContext.getString(R.string.modality_type)
        val modalityValue = getTranslation(WeakReference(applicationContext), modality)
        val markerTitle = "$modalityKey : $modalityValue"
        val markerOptions =
            MarkerOptions().position(LatLng(latLng.latitude, latLng.longitude)).title(markerTitle)
        val marker = googleMap!!.addMarker(markerOptions)
        eventMarker[eventId] = marker
        if (isMarkerToBeFocused) {
            focusMarker(marker!!)
        }
    }

    /**
     * Moves the camera of the map to the {@param marker} position and shows the Marker title.
     *
     * @param marker The `Marker` which is to be focused.
     */
    fun focusMarker(marker: Marker) {
        marker.showInfoWindow()
        val markerLatLng = marker.position
        val googleMap = googleMap
        val cameraUpdate = CameraUpdateFactory.newLatLng(markerLatLng)
        googleMap!!.moveCamera(cameraUpdate)
    }

    /**
     * Returns the translation for the [Modality].
     *
     * @param contextWeakReference A `WeakReference` to a `Context` required to access the translations
     * @param modality the Modality to translate
     * @return the translation string
     */
    private fun getTranslation(
        contextWeakReference: WeakReference<Context?>?,
        modality: Modality
    ): String {
        if (contextWeakReference == null) {
            Log.w(
                TAG,
                "WeakReference is null, displaying database identifier instead of translation for modality."
            )
            return modality.databaseIdentifier.lowercase()
        }
        val context = contextWeakReference.get()
        return when (modality) {
            Modality.TRAIN -> context!!.getString(R.string.modality_train)
            Modality.BUS -> context!!.getString(R.string.modality_bus)
            Modality.WALKING -> context!!.getString(R.string.modality_walking)
            Modality.CAR -> context!!.getString(R.string.modality_car)
            Modality.BICYCLE -> context!!.getString(R.string.modality_bicycle)
            Modality.MOTORBIKE -> context!!.getString(R.string.modality_motorbike)
            Modality.UNKNOWN -> modality.databaseIdentifier.lowercase()
            else -> throw IllegalArgumentException("Unknown modality type: $modality")
        }
    }

    companion object {
        /**
         * The id of the `Marker` used when the user adds a new `Marker` to the map which can still
         * be replaced or removed.
         */
        const val TEMPORARY_EVENT_MARKER_ID = -1L
    }
}