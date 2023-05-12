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
package de.cyface.app.r4r.capturing.marker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import de.cyface.app.r4r.databinding.FragmentMarkerBinding
import de.cyface.app.utils.Map
import java.util.Calendar

/**
 * The [Fragment] which shows a map with markers to the user.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 3.2.1
 */
class MarkerFragment : Fragment() {

    /**
     * This property is only valid between onCreateView and onDestroyView.
     */
    private var _binding: FragmentMarkerBinding? = null

    /**
     * The generated class which holds all bindings from the layout file.
     */
    private val binding get() = _binding!!

    /**
     * The `Map` used to visualize data.
     */
    private var map: Map? = null

    /**
     * Can be launched to request permissions.
     *
     * The launcher ensures `map.onMapReady` is called after permissions are newly granted.
     */
    private var permissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result.isNotEmpty()) {
                val allGranted = result.values.none { !it }
                if (allGranted) {
                    // Only if the map already called it's onMapReady
                    if (map!!.googleMap != null) {
                        map!!.onMapReady()
                    }
                } else {
                    Toast.makeText(
                        context,
                        "Location permission repeatedly denied",
                        Toast.LENGTH_LONG
                    ).show()
                    // Close Cyface if permission has not been granted.
                    // When the user repeatedly denies the location permission, the app won't start
                    // and only starts again if the permissions are granted manually.
                    // It was always like this, but if this is a problem we need to add a screen
                    // which explains the user that this can happen.
                    requireActivity().finish()
                }
            }
        }

    /**
     * The `Runnable` triggered when the `Map` is loaded and ready.
     */
    private val onMapReadyRunnable = Runnable {
        map!!.renderMarkers(markers)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Using the new map renderer, which must be called before MapView is initialized:
        // https://developers.google.com/maps/documentation/android-sdk/renderer
        MapsInitializer.initialize(requireContext(), MapsInitializer.Renderer.LATEST) {}

        _binding = FragmentMarkerBinding.inflate(inflater, container, false)
        val root: View = binding.root

        map = Map(binding.mapView, savedInstanceState, onMapReadyRunnable, permissionLauncher)

        return root
    }

    override fun onResume() {
        super.onResume()
        map!!.onResume()
    }

    override fun onPause() {
        super.onPause()
        map!!.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun eventPassed(): Boolean {
            val now = Calendar.getInstance()
            val endDate = Calendar.getInstance()
            endDate.set(2023, 5 /* June = 5! */, 26)
            return now.after(endDate)
        }

        val markers = if (eventPassed()) emptyList() else arrayListOf(
            // Schkeuditz
            MarkerOptions()
                .position(LatLng(51.39877, 12.20104)).title("Station: Nähe Globana"),
            MarkerOptions()
                .position(LatLng(51.38995, 12.22101)).title("Station: Stadtmuseum"),
            MarkerOptions()
                .position(LatLng(51.38848, 12.23337)).title("Station: Elsterbrücke"),
            MarkerOptions()
                .position(LatLng(51.40696, 12.22106)).title("Station: Fußballfeld")
            // Köthen
        )
    }
}