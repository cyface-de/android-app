/*
 * Copyright 2023-2024 Cyface GmbH
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
package de.cyface.app.capturing.settings

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import de.cyface.app.BuildConfig
import de.cyface.app.Application
import de.cyface.app.R
import de.cyface.app.databinding.FragmentSettingsBinding
import de.cyface.app.utils.ServiceProvider
import de.cyface.app.utils.SharedConstants
import de.cyface.app.utils.trips.incentives.AuthExceptionListener
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.synchronization.Auth
import io.sentry.Sentry
import net.openid.appauth.AuthorizationException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * The [Fragment] which shows the settings to the user.
 *
 * @author Armin Schnabel
 */
class SettingsFragment : Fragment() {

    /**
     * The authenticator to get the auth token from.
     */
    private lateinit var auth: Auth

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
     * The [SettingsViewModel] for this fragment.
     */
    private lateinit var viewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get dependencies
        if (activity is ServiceProvider) {
            val serviceProvider = activity as ServiceProvider
            capturing = serviceProvider.capturing
            auth = serviceProvider.auth
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
        binding.deleteAccountButton.setOnClickListener {
            showDeleteAccountConfirmationDialog()
        }

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

    private fun showDeleteAccountConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.confirm_delete_account_title)
            .setMessage(R.string.confirm_delete_account_message)
            .setPositiveButton(R.string.delete_account) { _, _ ->
                deleteAccount(
                    object : Callback {
                        override fun onResponse(call: Call, response: Response) {
                            if (response.code == 202) {
                                requireActivity().runOnUiThread {
                                    Toast.makeText(
                                        context,
                                        R.string.delete_account_success,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }

                                // This inform the auth server that the user wants to end its session
                                auth.endSession(requireActivity())
                            } else {
                                Sentry.captureMessage("Account deletion failed: ${response.code}")
                                requireActivity().runOnUiThread {
                                    Toast.makeText(
                                        context,
                                        R.string.delete_account_failed,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }

                        override fun onFailure(call: Call, e: IOException) {
                            Sentry.captureException(e)
                            requireActivity().runOnUiThread {
                                Toast.makeText(
                                    context,
                                    R.string.delete_account_error,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    object : AuthExceptionListener {
                        override fun onException(e: AuthorizationException) {
                            Sentry.captureException(e)
                            requireActivity().runOnUiThread {
                                Toast.makeText(context, R.string.auth_error, Toast.LENGTH_LONG)
                                    .show()
                            }
                        }
                    }
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Requests the account and user data deletion for the currently logged in user.
     *
     * @param handler the handler which receives the response in case of success
     * @param authErrorHandler the handler which receives the auth errors
     */
    private fun deleteAccount(
        handler: Callback,
        authErrorHandler: AuthExceptionListener
    ) {
        val client = OkHttpClient()
        auth.performActionWithFreshTokens { accessToken, _, ex ->
            if (ex != null) {
                authErrorHandler.onException(ex as AuthorizationException)
                return@performActionWithFreshTokens
            }

            val userId = auth.userId()!!

            // Try to send the request
            val url = BuildConfig.providerServer + "/users/$userId"
            Log.d(SharedConstants.TAG, "Account deletion request to $url")
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .delete()
                .build()
            client.newCall(request).enqueue(handler)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// final SwitchCompat connectToExternalSpeedSensorToggle = (SwitchCompat)view.getMenu()
// .findItem(R.id.drawer_setting_speed_sensor).getActionView();
/*
final boolean bluetoothIsConfigured = preferences.getString(BLUETOOTHLE_DEVICE_MAC_KEY, null) != null
&& preferences.getFloat(BLUETOOTHLE_WHEEL_CIRCUMFERENCE, 0.0F) > 0.0F;
connectToExternalSpeedSensorToggle.setChecked(bluetoothIsConfigured);
// connectToExternalSpeedSensorToggle.setOnClickListener(new ConnectToExternalSpeedSensorToggleListener());
/*
 * A listener which is called when the external bluetooth sensor toggle in the {@link NavDrawer} is clicked.
 * /
 * private class ConnectToExternalSpeedSensorToggleListener implements CompoundButton.OnCheckedChangeListener {
 *
 * @Override
 * public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
 * final CompoundButton compoundButton = (CompoundButton)view;
 * final Context applicationContext = view.getContext().getApplicationContext();
 * if (compoundButton.isChecked()) {
 * final BluetoothLeSetup bluetoothLeSetup = new BluetoothLeSetup(new BluetoothLeSetupListener() {
 *
 * @Override
 * public void onDeviceSelected(final BluetoothDevice device, final double wheelCircumference) {
 * final SharedPreferences.Editor editor = preferences.edit();
 * editor.putString(BLUETOOTHLE_DEVICE_MAC_KEY, device.getAddress());
 * editor.putFloat(BLUETOOTHLE_WHEEL_CIRCUMFERENCE,
 * Double.valueOf(wheelCircumference).floatValue());
 * editor.apply();
 * }
 *
 * @Override
 * public void onSetupProcessFailed(final Reason reason) {
 * compoundButton.setChecked(false);
 * if (reason.equals(Reason.NOT_SUPPORTED)) {
 * Toast.makeText(applicationContext, R.string.ble_not_supported, Toast.LENGTH_SHORT)
 * .show();
 * } else {
 * Log.e(TAG, "Setup process of bluetooth failed: " + reason);
 * Toast.makeText(applicationContext, R.string.bluetooth_setup_failed, Toast.LENGTH_SHORT)
 * .show();
 * }
 * }
 * });
 * bluetoothLeSetup.setup(mainActivity);
 * } else {
 * final SharedPreferences.Editor editor = preferences.edit();
 * editor.remove(BLUETOOTHLE_DEVICE_MAC_KEY);
 * editor.remove(BLUETOOTHLE_WHEEL_CIRCUMFERENCE);
 * editor.apply();
 * }
 * }
 * }
 */