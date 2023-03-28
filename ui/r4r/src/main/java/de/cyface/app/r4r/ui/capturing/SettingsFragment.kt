package de.cyface.app.r4r.ui.capturing

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import de.cyface.app.r4r.ServiceProvider
import de.cyface.app.r4r.databinding.FragmentSettingsBinding
import de.cyface.app.utils.SharedConstants.PREFERENCES_CENTER_MAP_KEY
import de.cyface.app.utils.SharedConstants.PREFERENCES_SYNCHRONIZATION_KEY
import de.cyface.datacapturing.CyfaceDataCapturingService

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private lateinit var capturingService: CyfaceDataCapturingService

    /**
     * The preferences used to store the user's preferred settings.
     */
    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context)

        // Auto center map
        binding.centerMapSwitch.setOnCheckedChangeListener(
            CenterMapSwitchHandler(
                preferences,
                context
            )
        )
        binding.uploadSwitch.setOnCheckedChangeListener(
            UploadSwitchHandler(
                preferences,
                context,
                capturingService
            )
        )

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        binding.centerMapSwitch.isChecked =
            preferences.getBoolean(PREFERENCES_CENTER_MAP_KEY, false)
        binding.uploadSwitch.isChecked =
            preferences.getBoolean(PREFERENCES_SYNCHRONIZATION_KEY, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class UploadSwitchHandler(
    private val preferences: SharedPreferences,
    private val context: Context?,
    private val capturingService: CyfaceDataCapturingService
) : CompoundButton.OnCheckedChangeListener {
    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        val current = preferences.getBoolean(PREFERENCES_SYNCHRONIZATION_KEY, false)
        if (current != isChecked) {
            // Update both, the preferences and WifiSurveyor's synchronizationEnabled status
            capturingService.wiFiSurveyor.isSyncEnabled = isChecked
            preferences.edit().putBoolean(PREFERENCES_SYNCHRONIZATION_KEY, isChecked).apply()

            // Show warning to user (storage gets filled)
            if (!isChecked) {
                Toast.makeText(
                    context,
                    de.cyface.app.utils.R.string.sync_disabled_toast,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

class CenterMapSwitchHandler(
    private val preferences: SharedPreferences,
    private val context: Context?
) : CompoundButton.OnCheckedChangeListener {
    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        val current = preferences.getBoolean(PREFERENCES_CENTER_MAP_KEY, false)
        if (current != isChecked) {
            preferences.edit().putBoolean(PREFERENCES_CENTER_MAP_KEY, isChecked).apply()
            if (isChecked) {
                Toast.makeText(
                    context,
                    de.cyface.app.utils.R.string.zoom_to_location_enabled_toast,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
