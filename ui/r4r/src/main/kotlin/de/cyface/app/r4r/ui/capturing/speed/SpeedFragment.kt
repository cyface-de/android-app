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
package de.cyface.app.r4r.ui.capturing.speed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import de.cyface.app.r4r.R
import de.cyface.app.utils.ServiceProvider
import de.cyface.app.r4r.databinding.FragmentSpeedBinding
import de.cyface.app.r4r.ui.capturing.CapturingViewModel
import de.cyface.app.r4r.ui.capturing.CapturingViewModelFactory
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.persistence.DefaultPersistenceLayer

/**
 * The [Fragment] which shows the live speed of the currently captured measurement.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.2.0
 */
class SpeedFragment : Fragment() {

    /**
     * This property is only valid between onCreateView and onDestroyView.
     */
    private var _binding: FragmentSpeedBinding? = null

    /**
     * The generated class which holds all bindings from the layout file.
     */
    private val binding get() = _binding!!

    /**
     * The capturing service object which controls data capturing and synchronization.
     */
    private lateinit var capturing: CyfaceDataCapturingService

    /**
     * An implementation of the persistence layer which caches some data during capturing.
     */
    private lateinit var persistence: DefaultPersistenceLayer<CapturingPersistenceBehaviour>

    /**
     * Shared instance of the [CapturingViewModel] which is used by multiple `Fragments.
     */
    private val capturingViewModel: CapturingViewModel by activityViewModels {
        CapturingViewModelFactory(
            persistence.measurementRepository!!,
            persistence.eventRepository!!
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (activity is ServiceProvider) {
            capturing = (activity as ServiceProvider).capturing
            persistence = capturing.persistenceLayer
        } else {
            throw RuntimeException("Context doesn't support the Fragment, implement `ServiceProvider`")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpeedBinding.inflate(inflater, container, false)
        val root: View = binding.root

        capturingViewModel.location.observe(viewLifecycleOwner) {
            val speedKmPh = it?.speed?.times(3.6)
            val speedText = if (speedKmPh == null) null else getString(de.cyface.app.utils.R.string.speedKph, speedKmPh)
            binding.liveSpeedView.text = speedText ?: getString(R.string.capturing_inactive)
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}