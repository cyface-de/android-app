/*
 * Copyright 2017-2025 Cyface GmbH
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
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
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
import de.cyface.app.utils.SharedConstants.TAG
import de.cyface.persistence.model.Event
import de.cyface.persistence.model.GeoLocation
import de.cyface.persistence.model.Modality
import de.cyface.persistence.model.Track
import de.cyface.utils.settings.AppSettings
import io.sentry.Sentry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * The Map class handles everything around the GoogleMap view.
 *
 * @author Armin Schnabel
 * @version 4.1.2
 * @since 1.0.0
 * @property view The `MapView` element of the `GoogleMap`.
 * @property onMapReadyRunnable The `Runnable` triggered when the `GoogleMap` is loaded and ready.
 * @property ignoreAutoZoom `true` if the map should ignore the user preferences and never activate
 * auto-zoom. `false` if auto-zoom should be enabled depending on the user preferences.
 */
class Map(
    private val view: MapView,
    savedInstanceState: Bundle?,
    onMapReadyRunnable: Runnable,
    private val lifecycleOwner: LifecycleOwner,
    private var permissionLauncher: ActivityResultLauncher<Array<String>>?,
    private val ignoreAutoZoom: Boolean = false
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

    /**
     * Handles location updates.
     */
    private lateinit var locationCallback: LocationCallback

    /**
     * The newest available position.
     */
    private var currentLocation: LatLng? = null

    /**
     * The settings used by both, UIs and libraries.
     */
    private lateinit var appSettings: AppSettings

    /**
     * The `Runnable` triggered when the `GoogleMap` is loaded and ready.
     */
    private val onMapReadyRunnable: Runnable

    /**
     * All currently shown `Event` `Marker`s with a reference between both required to find the Marker of a
     * Event.
     */
    private val eventMarker = HashMap<Long, Marker?>()

    init {
        view.onCreate(savedInstanceState)
        val activity = view.context as Activity
        applicationContext = activity.applicationContext
        this.onMapReadyRunnable = onMapReadyRunnable
        if (currentLocation == null) {
            currentLocation = LatLng(51.027852, 13.720864)
        }
        view.getMapAsync(this)
        if (activity is ServiceProvider) {
            val serviceProvider = activity as ServiceProvider
            appSettings = serviceProvider.appSettings
        }
    }

    /**
     * Subscribes to location updates to show the location on the map.
     */
    private fun requestLocationUpdates() {
        val activity = view.context as Activity
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity)
        val locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000).build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                lifecycleOwner.lifecycleScope.launch {
                    // The GMap location does not work on emulator, see bug report: https://issuetracker.google.com/issues/242438611
                    val centerMap = appSettings.centerMapFlow.first()
                    if (locationResult.locations.size > 0 && centerMap && !ignoreAutoZoom) {
                        moveToLocation(true, locationResult.locations[locationResult.locations.size - 1])
                    }
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
            Log.d(TAG, "requestLocationUpdates: Skip requestLocationUpdates (missing permissions)")
        } else {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
            if (googleMap != null) {
                googleMap!!.isMyLocationEnabled = true
            }
        }
    }

    /**
     * Unsubscribes from the location updates.
     */
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

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
        onMapReady()
    }

    /**
     * Initializes the Map when all permissions are granted and the map is loaded.
     *
     * This means e.g. that the map in zoomed to the current location, location updates are requested
     * and the `onMapReadyRunnable` is executed which was provided by the parent of this class.
     */
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

    fun renderMarkers(markers: List<MarkerOptions>) {
        markers.forEach { googleMap!!.addMarker(it) }
    }

    /**
     * Renders the provided {@param tracks} and {@param events} onto this classes map.
     *
     * @param tracks a list of [Track]s which can be rendered to a map
     * @param events a list of [Event]s which can be rendered to a map
     * @param moveCameraToBounds `True` if the camera of the map should be moved to the track boundaries
     * @param markers a list of [MarkerOptions] to render on the map
     */
    fun render(
        tracks: List<Track>, events: List<Event>,
        moveCameraToBounds: Boolean,
        markers: List<MarkerOptions>
    ) {
        googleMap!!.clear()
        renderMarkers(markers)

        // Calculate geo boundaries
        val builder = LatLngBounds.Builder()
        var positions = 0

        // Iterate through the sub tracks and their points
        val allLocations = mutableListOf<GeoLocation?>()
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
        renderEvents(allLocations, events)
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
        allLocations: List<GeoLocation?>,
        events: List<Event>
    ) {
        // Iterate through the events and select GeoLocations for each event to be rendered on the map
        val locationIterator = allLocations.iterator()
        var previousLocation: GeoLocation? = null
        var nextLocation: GeoLocation? = null
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
            require(!locationIterator.hasNext())

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
    @Suppress("SameParameterValue")
    private fun showAndMoveToCurrentLocation(permissionWereJustGranted: Boolean) {
        try {
            if (this::fusedLocationClient.isInitialized) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        moveToLocation(false, location)
                    }
                }
            } else {
                Log.w(TAG, "showAndMoveToCurrentLocation ignored, fusedLocationClient is null")
            }
            // Happens when Google Play Services on phone is out of date
            if (googleMap != null) {
                googleMap!!.isMyLocationEnabled = true
            }
        } catch (e: SecurityException) {
            lifecycleOwner.lifecycleScope.launch {
                if (permissionWereJustGranted) {
                    Log.w(TAG, "showAndMoveToCurrentLocation: Location permission are missing")
                    val reportErrors = appSettings.reportErrorsFlow.first()
                    if (reportErrors) {
                        Sentry.captureException(e)
                    }
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
                lifecycleOwner.lifecycleScope.launch {
                    val reportErrors = appSettings.reportErrorsFlow.first()
                    if (!highFrequentRequest && reportErrors) {
                        Sentry.captureMessage("Map.moveToLocation: GoogleMap is null")
                    }
                }
                return
            }
            googleMap!!.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted or Google play service out of date?")
            lifecycleOwner.lifecycleScope.launch {
                val reportErrors = appSettings.reportErrorsFlow.first()
                if (!highFrequentRequest && reportErrors) {
                    Sentry.captureException(e)
                }
            }
        }
    }

    fun onResume() {
        view.onResume()
        if (googleMap == null) {
            return
        }
        requestLocationUpdates()
    }

    fun onPause() {
        stopLocationUpdates()
    }

    fun onDestroy() {
        // Ensure launcher is not called after parent fragment was destroyed [RFR-630]
        permissionLauncher = null
    }

    override fun onLocationChanged(location: Location) {
        // This is used by `ui/cyface`, the `ui/r4r` uses `onLocationResult`
        lifecycleOwner.lifecycleScope.launch {
            val centerMap = appSettings.centerMapFlow.first()
            if (centerMap && !ignoreAutoZoom) {
                moveToLocation(true, location)
            }
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
     * Adds a [de.cyface.persistence.model.EventType.MODALITY_TYPE_CHANGE] `Event` `Marker` to the `Map`.
     *
     * @ param eventId the identifier of the `Event` or [.TEMPORARY_EVENT_MARKER_ID] if this is only a
     * temporary marker
     * until the {@param eventId} is known.
     * @ param latLng the `LatLng` of the marker to be added
     * @ param modality the new `Modality` of the `MODALITY_TYPE_CHANGE` marker to be added
     * @ param isMarkerToBeFocused `True` if the newly added `Marker` is to be focused after creation
     * /
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
     * @ param marker The `Marker` which is to be focused.
    */
    fun focusMarker(marker: Marker) {
    marker.showInfoWindow()
    val markerLatLng = marker.position
    val googleMap = googleMap
    val cameraUpdate = CameraUpdateFactory.newLatLng(markerLatLng)
    googleMap!!.moveCamera(cameraUpdate)
    }*/

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
        //const val TEMPORARY_EVENT_MARKER_ID = -1L
    }
}
