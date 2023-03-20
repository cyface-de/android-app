package de.cyface.app.r4r.ui.trips

import android.content.Context
import android.os.Bundle
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
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.persistence.DefaultPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class TripsFragment : Fragment() {

    private var _binding: FragmentTripsBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private lateinit var capturingService: CyfaceDataCapturingService

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
            throw RuntimeException("Context does not support the Fragment, implement MyDependencies")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTripsBinding.inflate(inflater, container, false)
        val root: View = binding.root

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
                adapter,
                WeakReference<Context>(requireContext().applicationContext)
            ), viewLifecycleOwner, Lifecycle.State.RESUMED
        )

        return root
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
        private val adapter: TripListAdapter,
        private val context: WeakReference<Context>
    ) : androidx.core.view.MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.trips, menu)
        }

        override fun onMenuItemSelected(item: MenuItem): Boolean {
            return when (item.itemId) {
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

        private fun deleteSelectedMeasurements() {
            if (adapter.tracker!!.selection.isEmpty) {
                Toast.makeText(
                    context.get(), context.get()!!.getString(R.string.delete_data_non_selected),
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