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
package de.cyface.app.r4r.capturing.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import de.cyface.app.r4r.Application
import de.cyface.app.r4r.databinding.FragmentSettingsBinding
import de.cyface.app.utils.ServiceProvider
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.utils.settings.AppSettings

/**
 * The [Fragment] which shows the settings to the user.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 3.2.0
 */
class SettingsFragment : Fragment() {

    /**
     * This property is only valid between onCreateView and onDestroyView.
     */
    private var _binding: FragmentSettingsBinding? = null

    /**
     * The generated class which holds all bindings from the layout file.
     */
    private val binding get() = _binding!!

    /**
     * The capturing service object which controls data capturing and synchronization.
     */
    private lateinit var capturing: CyfaceDataCapturingService

    /**
     * The settings used by both, UIs and libraries.
     */
    private lateinit var appSettings: AppSettings

    /**
     * The [SettingsViewModel] for this fragment.
     */
    private lateinit var viewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get dependencies
        if (activity is ServiceProvider) {
            capturing = (activity as ServiceProvider).capturing
        } else {
            throw RuntimeException("Context does not support the Fragment, implement ServiceProvider")
        }

        // Initialize ViewModel
        viewModel = ViewModelProvider(
            this,
            SettingsViewModelFactory(Application.appSettings)
        )[SettingsViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        // Observe UI changes
        /** app settings **/
        binding.centerMapSwitch.setOnCheckedChangeListener(
            CenterMapSwitchHandler(
                viewModel,
                context
            )
        )
        binding.uploadSwitch.setOnCheckedChangeListener(
            UploadSwitchHandler(
                viewModel,
                context,
                capturing
            )
        )

        // Observe view model and update UI
        viewModel.centerMap.observe(viewLifecycleOwner) { centerMapValue ->
            run {
                binding.centerMapSwitch.isChecked = centerMapValue!!
            }
        }
        viewModel.upload.observe(viewLifecycleOwner) { uploadValue ->
            run {
                binding.uploadSwitch.isChecked = uploadValue!!
            }
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

