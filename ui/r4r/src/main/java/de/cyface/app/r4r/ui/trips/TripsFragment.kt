package de.cyface.app.r4r.ui.trips

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.MenuHost
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.selection.MutableSelection
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StableIdKeyProvider
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import de.cyface.app.r4r.R
import de.cyface.app.r4r.ServiceProvider
import de.cyface.app.r4r.databinding.FragmentTripsBinding
import de.cyface.app.r4r.utils.Constants.AUTHORITY
import de.cyface.app.utils.SharedConstants.PREFERENCES_SYNCHRONIZATION_KEY
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.persistence.DefaultPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.synchronization.WiFiSurveyor
import de.cyface.utils.Validate
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class TripsFragment : Fragment() {

    private var _binding: FragmentTripsBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private lateinit var capturingService: CyfaceDataCapturingService

    /**
     * The preferences used to store the user's preferred settings.
     */
    private lateinit var preferences: SharedPreferences

    private var tracker: SelectionTracker<Long>? = null

    private val tripsViewModel: TripsViewModel by viewModels {
        TripsViewModelFactory(capturingService.persistenceLayer.measurementRepository!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) tracker?.onRestoreInstanceState(savedInstanceState)

        if (activity is ServiceProvider) {
            capturingService = (activity as ServiceProvider).capturingService
        } else {
            throw RuntimeException("Context does not support the Fragment, implement ServiceProvider")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTripsBinding.inflate(inflater, container, false)
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context)

        // Bind the UI element to the adapter
        val tripsList = binding.tripsList
        val adapter = TripListAdapter()
        tripsList.adapter = adapter
        tripsList.layoutManager = LinearLayoutManager(context)

        // Support list selection
        val tracker = SelectionTracker.Builder(
            "tripsListSelection",
            tripsList,
            StableIdKeyProvider(tripsList),
            TripListAdapter.ItemsDetailsLookup(tripsList),
            StorageStrategy.createLongStorage()
        ).withSelectionPredicate(
            SelectionPredicates.createSelectAnything() // allows multiple choice
        ).build()
        adapter.tracker = tracker

        // Add divider between list items
        val divider = DividerItemDecoration(
            context,
            (tripsList.layoutManager as LinearLayoutManager).orientation
        )
        tripsList.addItemDecoration(divider)

        // Update adapters with the updates from the ViewModel
        tripsViewModel.measurements.observe(viewLifecycleOwner) { measurements ->
            measurements?.let { adapter.submitList(it) }
        }

        // Add items to menu (top right)
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            MenuProvider(
                capturingService,
                preferences,
                adapter,
                WeakReference<Context>(requireContext().applicationContext)
            ), viewLifecycleOwner, Lifecycle.State.RESUMED
        )

        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        tracker?.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class MenuProvider(
        private val capturingService: CyfaceDataCapturingService,
        private val preferences: SharedPreferences,
        private val adapter: TripListAdapter,
        private val context: WeakReference<Context>
    ) : androidx.core.view.MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.trips, menu)
        }

        override fun onMenuItemSelected(item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_sync -> {
                    syncNow()
                    true
                }
                R.id.select_all_item -> {
                    adapter.selectAll()
                    true
                }
                R.id.delete_measurement_item -> {
                    deleteSelectedMeasurements()
                    true
                }
                else -> {
                    false
                }
            }
        }

        private fun syncNow() {
            // Check if syncable network is available
            val isConnected = capturingService.wiFiSurveyor.isConnected
            Log.v(
                WiFiSurveyor.TAG,
                (if (isConnected) "" else "Not ") + "connected to syncable network"
            )
            if (!isConnected) {
                Toast.makeText(
                    context.get(),
                    context.get()!!
                        .getString(de.cyface.app.utils.R.string.error_message_sync_canceled_no_wifi),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            // Check is sync is disabled via frontend
            val syncEnabled = capturingService.wiFiSurveyor.isSyncEnabled
            val syncPreferenceEnabled = preferences.getBoolean(PREFERENCES_SYNCHRONIZATION_KEY, true)
            Validate.isTrue(
                syncEnabled == syncPreferenceEnabled,
                "sync " + (if (syncEnabled) "enabled" else "disabled")
                        + " but syncPreference " + if (syncPreferenceEnabled) "enabled" else "disabled"
            )
            if (!syncEnabled) {
                Toast.makeText(
                    context.get(),
                    context.get()!!.getString(de.cyface.app.utils.R.string.error_message_sync_canceled_disabled),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            // Request instant Synchronization
            capturingService.scheduleSyncNow()
        }

        private fun deleteSelectedMeasurements() {
            if (adapter.tracker!!.selection.isEmpty) {
                Toast.makeText(
                    context.get(),
                    context.get()!!
                        .getString(de.cyface.app.utils.R.string.delete_data_non_selected),
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            // FIXME: see MeasurementDeleteController (e.g. picture removal)
            GlobalScope.launch {
                val persistence = DefaultPersistenceLayer(
                    context.get()!!,
                    AUTHORITY,
                    DefaultPersistenceBehaviour()
                )
                val mutableSelection = MutableSelection<Long>()
                adapter.tracker!!.copySelection(mutableSelection)
                mutableSelection.forEach { position ->
                    run {
                        val measurementId = adapter.getItem(position.toInt()).id
                        persistence.delete(measurementId)
                        adapter.tracker!!.deselect(position)
                    }
                }
            }
        }
    }
}