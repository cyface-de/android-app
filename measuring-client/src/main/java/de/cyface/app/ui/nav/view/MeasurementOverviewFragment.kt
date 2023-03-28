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
package de.cyface.app.ui.nav.view

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.loader.app.LoaderManager
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.floatingactionbutton.FloatingActionButton
import de.cyface.app.R
import de.cyface.app.ui.dialog.ModalityDialog
import de.cyface.app.ui.nav.controller.EventDeleteController
import de.cyface.app.ui.nav.controller.EventDeleteController.EventDeleteControllerParameters
import de.cyface.app.ui.nav.controller.ExportTask
import de.cyface.app.ui.nav.controller.MeasurementDeleteController
import de.cyface.app.utils.Constants
import de.cyface.app.utils.Map
import de.cyface.app.utils.SharedConstants.PERMISSION_REQUEST_EXTERNAL_STORAGE_FOR_EXPORT
import de.cyface.app.utils.SharedConstants.PREFERENCES_MODALITY_KEY
import de.cyface.persistence.DefaultPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.model.EventType
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.Modality
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.persistence.strategy.DefaultLocationCleaning
import de.cyface.synchronization.BundlesExtrasCodes
import de.cyface.utils.Validate

/**
 * This [Fragment] is used to show all stored measurements in an AdapterView.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 4.5.6
 * @since 1.0.0
 */
class MeasurementOverviewFragment : Fragment() {
    /**
     * The [MeasurementDataList] containing the [Measurement]s.
     */
    private var measurementDataList: MeasurementDataList? = null

    /**
     * A [Map] fragment which is used to visualize [Measurement]s.
     */
    private var map: Map? = null

    /**
     * The [EventDataList] containing the [EventType.MODALITY_TYPE_CHANGE]s of a [Measurement].
     */
    private var eventDataList: EventDataList? = null

    /**
     * `True` if the `ListView` below the `Map` currently shows the `Event`s of a
     * `Measurement`.
     */
    private var isEventsListShown = false

    /**
     * The [DefaultPersistenceLayer] required to retrieve the [Measurement] date from the
     * [ParcelableGeoLocation]s
     */
    private var persistenceLayer: DefaultPersistenceLayer<DefaultPersistenceBehaviour>? = null

    /**
     * The `View` required to find layout elements.
     */
    private var view: View? = null

    /**
     * The `ListView` used to display `Measurement`s and their `Event`s.
     */
    private var listView: ListView? = null

    /**
     * The `FloatingActionButton` which allows the user to add `EventType#MODALITY_TYPE_CHANGE`s
     */
    private var addButton: FloatingActionButton? = null

    /**
     * `True` is the user hit the "Add Event" button. This is used e.g. to listen to clicks on the map for
     * selection the marker position.
     */
    private var isAddEventActionActive = false

    /**
     * A `View.OnClickListener` for the addButton action button used to add `Event`s.
     */
    private val addButtonClickListener = View.OnClickListener {
        val googleMap = map!!.googleMap

        // If the addEventAction is already active this means the user confirms the location
        if (isAddEventActionActive) {

            // Only accept the location if there is already a temporary marker
            val temporaryMarker = map!!.eventMarker[Map.TEMPORARY_EVENT_MARKER_ID]
            if (temporaryMarker != null) {

                // Show ModalityDialog
                val fragmentManager = fragmentManager
                Validate.notNull(fragmentManager)
                val dialog = ModalityDialog()
                dialog.setTargetFragment(
                    this@MeasurementOverviewFragment,
                    DIALOG_ADD_EVENT_MODALITY_SELECTION_REQUEST_CODE
                )
                val measurementId = eventDataList!!.measurementId
                Validate.notNull(measurementId)
                dialog.setMeasurementId(measurementId!!)
                dialog.isCancelable = true
                dialog.show(fragmentManager!!, "MODALITY_DIALOG")
                return@OnClickListener
            }
        }
        Toast.makeText(
            context,
            de.cyface.app.utils.R.string.toast_select_location,
            Toast.LENGTH_SHORT
        ).show()
        isAddEventActionActive = true
        // Choose location on map
        googleMap!!.setOnMapClickListener { latLng: LatLng? ->
            // Check if there is already a temporary Marker and remove it
            val temporaryMarker = map!!.eventMarker[Map.TEMPORARY_EVENT_MARKER_ID]
            temporaryMarker?.remove()

            // This is a temporary marker as the event is added when the modality is known
            map!!.addMarker(Map.TEMPORARY_EVENT_MARKER_ID, latLng!!, Modality.UNKNOWN, false)

            // Change addButton to "accept" icon to visualize the user that he can continue
            addButton!!.hide() // Workaround for VIC-65 (https://stackoverflow.com/a/52158081/5815054)
            addButton!!.setImageResource(R.drawable.ic_check)
            addButton!!.show()
        }
    }

    // Ensure onMapReadyRunnable is called after permissions are newly granted
    // This launcher must be launched to request permissions
    private var permissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.isNotEmpty()) {
                val allGranted = result.values.none { !it }
                if (allGranted) {
                    // Only if the map already called it's onMapReady
                    if (map!!.googleMap != null) {
                        map!!.onMapReady()
                    }
                } else {
                    Toast.makeText(context, "Location permission repeatedly denies", Toast.LENGTH_LONG).show()
                    // Close Cyface if permission has not been granted.
                    // When the user repeatedly denies the location permission, the app won't start
                    // and only starts again if the permissions are granted manually.
                    // It was always like this, but if this is a problem we need to add a screen
                    // which explains the user that this can happen.
                    requireActivity().finish()
                }
            }
        }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val fragmentActivity = activity
        Validate.notNull(fragmentActivity)
        val loaderManager = LoaderManager.getInstance(this)
        loaderManager.initLoader(MEASUREMENT_LOADER_ID, null, measurementDataList!!)
        Log.d(TAG, "onActivityCreated() done")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        view = inflater.inflate(R.layout.fragment_measurements, container, false)
        persistenceLayer = DefaultPersistenceLayer(
            inflater.context,
            Constants.AUTHORITY,
            DefaultPersistenceBehaviour()
        )
        map = Map(
            requireView().findViewById(R.id.mapView),
            savedInstanceState,
            {},
            permissionLauncher
        )
        measurementDataList = MeasurementDataList(activity, persistenceLayer, this, map)
        measurementDataList!!.onCreateView(requireView())
        eventDataList = EventDataList(activity, persistenceLayer, null, map!!)
        eventDataList!!.onCreateView(requireView())
        listView = requireView().findViewById(R.id.measurements_list_view)
        addButton = requireView().findViewById(R.id.add_button)
        addButton!!.setOnClickListener(addButtonClickListener)
        return view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onResume() {
        super.onResume()
        map!!.onResume()
    }

    override fun onPause() {
        map!!.onPause()
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.measurement_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val fragmentActivity = activity
        Validate.notNull(fragmentActivity)
        return if (item.itemId == R.id.export_menu_item) {
            // Permission requirements: https://developer.android.com/training/data-storage
            val requiresWritePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
            val missingPermissions = (ContextCompat.checkSelfPermission(
                fragmentActivity!!,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(
                fragmentActivity,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED)
            if (requiresWritePermission && missingPermissions) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(
                        arrayOf<String>(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ),
                        PERMISSION_REQUEST_EXTERNAL_STORAGE_FOR_EXPORT
                    )
                } else {
                    Toast.makeText(
                        fragmentActivity,
                        fragmentActivity.getString(de.cyface.app.utils.R.string.export_data_no_permission),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                ExportTask(fragmentActivity).execute()
            }
            true
        } else if (item.itemId == R.id.select_all_item) {
            selectAllItems()
            true
        } else if (item.itemId == R.id.delete_measurement_item) {
            if (isEventsListShown) {
                deleteSelectedEvents(fragmentActivity!!, eventDataList!!.listView)
            } else {
                deleteSelectedMeasurements(fragmentActivity!!, measurementDataList!!.listView)
            }
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private fun deleteSelectedEvents(
        fragmentActivity: FragmentActivity,
        eventsView: ListView
    ) {
        if (eventsView.checkedItemCount < 1) {
            Toast.makeText(
                activity,
                fragmentActivity.getString(de.cyface.app.utils.R.string.delete_data_non_selected),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        EventDeleteController(fragmentActivity)
            .execute(EventDeleteControllerParameters(eventsView, map))
    }

    private fun deleteSelectedMeasurements(
        fragmentActivity: FragmentActivity,
        measurementsView: ListView
    ) {
        if (measurementsView.checkedItemCount < 1) {
            Toast.makeText(
                activity,
                fragmentActivity.getString(de.cyface.app.utils.R.string.delete_data_non_selected),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        // TODO [CY-4572]: Validate.isTrue((measurementListView.getAdapter() instanceof MeasurementAdapter));
        MeasurementDeleteController(fragmentActivity).execute(measurementsView)
        listView!!.choiceMode = ListView.CHOICE_MODE_SINGLE
    }

    /**
     * Sets all list items in [MeasurementDataList.getListView] or the [EventDataList.getListView] to
     * selected.
     */
    private fun selectAllItems() {
        val listView =
            if (isEventsListShown) eventDataList!!.listView else measurementDataList!!.listView
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        val adapter = listView.adapter
        if (adapter != null) {
            for (i in 0 until adapter.count) {
                listView.setItemChecked(i, true)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        val fragmentActivity = activity
        Validate.notNull(fragmentActivity)
        when (requestCode) {
            PERMISSION_REQUEST_EXTERNAL_STORAGE_FOR_EXPORT -> if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                    fragmentActivity,
                    fragmentActivity!!.getString(de.cyface.app.utils.R.string.export_data),
                    Toast.LENGTH_LONG
                ).show()
                ExportTask(fragmentActivity).execute()
            } else {
                Toast.makeText(
                    fragmentActivity,
                    fragmentActivity!!.getString(de.cyface.app.utils.R.string.export_data_no_permission),
                    Toast.LENGTH_LONG
                ).show()
            }
            else -> {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
        }
    }

    /**
     * @return `True` if the onBackPressed was handled, `False` is it still needs to be handled by the
     * calling method.
     */
    fun onBackPressed(): Boolean {
        if (isEventsListShown) {
            // Just cancel the "add event" action if it's active
            if (isAddEventActionActive) {
                cancelAddEventAction()
                return true
            }
            isEventsListShown = false
            addButton!!.hide()
            map!!.clearMap()

            // Without this the listView.getCheckedItemCount() shows too many (i.e. is not updated)
            measurementDataList = MeasurementDataList(activity, persistenceLayer, this, map)
            measurementDataList!!.onCreateView(requireView())
            listView!!.choiceMode = ListView.CHOICE_MODE_SINGLE
            val activity = activity as AppCompatActivity?
            Validate.notNull(activity)
            val actionBar = activity!!.supportActionBar
            Validate.notNull(actionBar)
            actionBar!!.title = getString(R.string.drawer_title_measurements)
            LoaderManager.getInstance(this)
                .restartLoader(MEASUREMENT_LOADER_ID, null, measurementDataList!!)
            return true
        }
        return false
    }

    private fun cancelAddEventAction() {
        isAddEventActionActive = false
        map!!.googleMap!!.setOnMapClickListener(null)
        val temporaryMarker = map!!.eventMarker[Map.TEMPORARY_EVENT_MARKER_ID]
        temporaryMarker?.remove()
        addButton!!.hide() // Workaround for VIC-65 (https://stackoverflow.com/a/52158081/5815054)
        addButton!!.setImageResource(R.drawable.ic_add)
        addButton!!.show()
    }

    fun showEvents(measurementId: Long) {
        check(!isEventsListShown) { "isEventsListShown is already true." }
        isEventsListShown = true
        addButton!!.show()
        Log.d(TAG, "showEvents() of mid $measurementId")
        eventDataList!!.setMeasurementId(measurementId)

        // Without this the listView.getCheckedItemCount() shows too many (i.e. is not updated)
        eventDataList = EventDataList(activity, persistenceLayer, measurementId, map!!)
        eventDataList!!.onCreateView(requireView())
        val activity = activity as AppCompatActivity?
        Validate.notNull(activity)
        val actionBar = activity!!.supportActionBar
        Validate.notNull(actionBar)
        actionBar!!.title =
            getString(de.cyface.app.utils.R.string.measurement) + " " + measurementId
        LoaderManager.getInstance(this).restartLoader(MEASUREMENT_LOADER_ID, null, eventDataList!!)
    }

    /**
     * Called when a [Modality] was selected in the [ModalityDialog]. This happens when the user adds a new
     * [EventType.MODALITY_TYPE_CHANGE] via the UI, see [.addButtonClickListener].
     *
     * @param data an intent which may contain result data
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DIALOG_ADD_EVENT_MODALITY_SELECTION_REQUEST_CODE) {
            val measurementId = data!!.getLongExtra(BundlesExtrasCodes.MEASUREMENT_ID, -1L)
            Validate.isTrue(measurementId != -1L)
            val modalityId = data.getStringExtra(PREFERENCES_MODALITY_KEY)
            val modality = Modality.valueOf(
                modalityId!!
            )
            Validate.notNull(modality)
            val temporaryMarker = map!!.eventMarker[Map.TEMPORARY_EVENT_MARKER_ID]
            Validate.notNull(temporaryMarker)
            val markerPosition = temporaryMarker!!.position
            val markerLocation = Location("marker")
            markerLocation.latitude = markerPosition.latitude
            markerLocation.longitude = markerPosition.longitude

            // Load GeoLocations
            val geoLocations: MutableList<ParcelableGeoLocation?> = ArrayList()
            val tracks = persistenceLayer!!.loadTracks(measurementId, DefaultLocationCleaning())
            for ((geoLocations1) in tracks) {
                geoLocations.addAll(geoLocations1)
            }

            // Search for the nearest GeoLocation
            var minDistance: Double? = null
            var nearestGeoLocation: ParcelableGeoLocation? = null
            for (geoLocation in geoLocations) {
                val location = Location("geoLocation")
                location.latitude = geoLocation!!.lat
                location.longitude = geoLocation.lon
                val distance = markerLocation.distanceTo(location).toDouble()
                if (minDistance == null || distance < minDistance) {
                    minDistance = distance
                    nearestGeoLocation = geoLocation
                }
            }

            // Do nothing if there is no location to reference the marker to
            if (nearestGeoLocation == null) {
                Toast.makeText(
                    context,
                    de.cyface.app.utils.R.string.toast_no_locations_found,
                    Toast.LENGTH_LONG
                ).show()
                cancelAddEventAction()
                return
            }

            // Add new Event to database
            val measurement: Measurement?
            val eventId: Long
            measurement = persistenceLayer!!.loadMeasurement(measurementId)
            eventId = persistenceLayer!!.logEvent(
                EventType.MODALITY_TYPE_CHANGE, measurement!!,
                nearestGeoLocation.timestamp, modality.databaseIdentifier
            )
            Log.d(
                TAG,
                "Event added, id: " + eventId + " timestamp: " + nearestGeoLocation.timestamp
            )

            // Add new Marker to map
            val latLng = LatLng(nearestGeoLocation.lat, nearestGeoLocation.lon)
            map!!.addMarker(eventId, latLng, modality, true)
            Toast.makeText(
                context,
                de.cyface.app.utils.R.string.toast_item_created_on_tracks_nearest_position,
                Toast.LENGTH_LONG
            )
                .show()
            cancelAddEventAction()
        }
    }

    companion object {
        /**
         * The tag used to identify logging from this class.
         */
        private const val TAG = Constants.PACKAGE + ".mof"

        /**
         * The id used to identify the `Loader` responsible for the `Measurement`s.
         */
        private const val MEASUREMENT_LOADER_ID = 0

        /**
         * The identifier for the [ModalityDialog] request which asks the user to select a [Modality] when he
         * adds a new [EventType.MODALITY_TYPE_CHANGE] via UI.
         */
        const val DIALOG_ADD_EVENT_MODALITY_SELECTION_REQUEST_CODE = 201909193
    }
}