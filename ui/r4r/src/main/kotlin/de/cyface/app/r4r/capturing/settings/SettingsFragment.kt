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
package de.cyface.app.r4r.capturing.settings

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import de.cyface.app.r4r.Application
import de.cyface.app.r4r.BuildConfig
import de.cyface.app.r4r.CameraServiceProvider
import de.cyface.app.r4r.R
import de.cyface.app.r4r.databinding.FragmentSettingsBinding
import de.cyface.app.r4r.utils.Constants.TAG
import de.cyface.app.utils.ServiceProvider
import de.cyface.app.utils.SharedConstants
import de.cyface.app.utils.trips.incentives.AuthExceptionListener
import de.cyface.camera_service.CameraInfo
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.synchronization.Auth
import io.sentry.Sentry
import net.openid.appauth.AuthorizationException
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.math.roundToInt

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

    /**
     * Can be launched to request permissions.
     */
    var permissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result.isNotEmpty()) {
                val allGranted = result.values.none { !it }
                if (allGranted) {
                    //showCameraModeDialog(this)
                    viewModel.setCameraEnabled(true, requireContext())
                } else {
                    Toast.makeText(
                        context,
                        requireContext().getString(de.cyface.camera_service.R.string.camera_service_off_missing_permissions),
                        Toast.LENGTH_LONG
                    ).show()
                    // Workaround to ensure it's updated back to false on permission denial
                    viewModel.setCameraEnabled(true, requireContext())
                    viewModel.setCameraEnabled(false, requireContext())
                }
            }
        }

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
        val cameraSettings =
            if (activity is CameraServiceProvider) {
                (activity as CameraServiceProvider).cameraSettings
            } else {
                throw RuntimeException("Context doesn't support the Fragment, implement `CameraServiceProvider`")
            }

        // Initialize ViewModel
        viewModel = ViewModelProvider(
            this,
            SettingsViewModelFactory(Application.appSettings, cameraSettings)
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
        /** camera settings **/
        binding.cameraEnabledSwitch.setOnCheckedChangeListener(
            CameraSwitchHandler(
                viewModel,
                this
            )
        )
        binding.distanceBasedSwitcher.setOnCheckedChangeListener(
            DistanceBasedSwitchHandler(
                requireContext(),
                viewModel
            )
        )
        binding.distanceBasedSlider.addOnChangeListener(
            TriggerDistanceSlideHandler(viewModel)
        )
        binding.distanceBasedUnit.text = TRIGGER_DISTANCE_UNIT

        // Observe view model and update UI
        /** app settings **/
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
        /** camera settings **/
        viewModel.cameraEnabled.observe(viewLifecycleOwner) { cameraEnabled ->
            run {
                binding.cameraEnabledSwitch.isChecked = cameraEnabled
                binding.cameraSettingsWrapper.visibility = if (cameraEnabled) View.VISIBLE else View.GONE

                // Manual Sensor support and features
                if (cameraEnabled) {
                    val cameraInfo = CameraInfo(requireContext())
                    viewModel.manualSensorSupported = cameraInfo.manualSensorSupported

                    /*
                    if (cameraInfo.manualSensorSupported) {
                        setManualSensorSupport(cameraInfo)
                    } else {
                        //binding.staticFocusDistanceSlider.visibility = INVISIBLE
                        binding.staticFocusWrapper.visibility = View.INVISIBLE
                        // binding.staticExposureTimeSlider.setVisibility(View.INVISIBLE);
                        // binding.staticExposureValueSlider.visibility = INVISIBLE
                        binding.staticExposureTimeWrapper.visibility = View.INVISIBLE
                        binding.staticExposureValueWrapper.visibility = View.INVISIBLE
                    }
                    */
                }
            }
        }
        viewModel.distanceBasedTriggering.observe(viewLifecycleOwner) { distanceBased ->
            run {
                binding.distanceBasedSwitcher.isChecked = distanceBased
                binding.distanceBasedWrapper.visibility = if (distanceBased) VISIBLE else INVISIBLE
            }
        }
        viewModel.triggeringDistance.observe(viewLifecycleOwner) { triggeringDistance ->
            run {
                val roundedDistance = (triggeringDistance * 100).roundToInt() / 100f
                Log.d(TAG, "updateView -> triggering distance to $roundedDistance")
                binding.distanceBasedSlider.value = roundedDistance

                val text = StringBuilder(roundedDistance.toString())
                while (text.length < 4) {
                    text.append("0")
                }
                binding.distanceBased.text = text
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

    companion object {
        /**
         * The unit of the [.triggerDistancePreference] value shown in the **UI**.
         */
        private const val TRIGGER_DISTANCE_UNIT = "m"
    }
}

