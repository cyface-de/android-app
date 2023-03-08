package de.cyface.app.r4r.ui.trips

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import de.cyface.app.r4r.R
import de.cyface.app.r4r.ServiceProvider
import de.cyface.app.r4r.databinding.FragmentTripsBinding
import de.cyface.app.r4r.ui.capturing.CapturingViewModel
import de.cyface.app.r4r.ui.capturing.CapturingViewModelFactory
import de.cyface.datacapturing.CyfaceDataCapturingService

class TripsFragment : Fragment() {

    private var _binding: FragmentTripsBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private lateinit var capturingService: CyfaceDataCapturingService

    private val tripsViewModel: TripsViewModel by viewModels {
        TripsViewModelFactory(capturingService.persistenceLayer.measurementRepository!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        // Update adapter with the updates from the ViewModel
        tripsViewModel.measurements.observe(viewLifecycleOwner) { measurements ->
            measurements?.let { adapter.submitList(it) }
        }

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.trips, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                TODO("Not yet implemented")
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}