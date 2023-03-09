package de.cyface.app.r4r

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import de.cyface.app.r4r.databinding.ActivityMainBinding
import de.cyface.app.r4r.utils.Constants.ACCOUNT_TYPE
import de.cyface.app.r4r.utils.Constants.AUTHORITY
import de.cyface.app.r4r.utils.Constants.DEFAULT_SENSOR_FREQUENCY
import de.cyface.app.r4r.utils.Constants.PERMISSION_REQUEST_ACCESS_FINE_LOCATION
import de.cyface.app.r4r.utils.Constants.SUPPORT_EMAIL
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.DataCapturingListener
import de.cyface.datacapturing.exception.SetupException
import de.cyface.datacapturing.model.CapturedData
import de.cyface.datacapturing.ui.Reason
import de.cyface.energy_settings.TrackingSettings.showEnergySaferWarningDialog
import de.cyface.energy_settings.TrackingSettings.showGnssWarningDialog
import de.cyface.energy_settings.TrackingSettings.showProblematicManufacturerDialog
import de.cyface.energy_settings.TrackingSettings.showRestrictedBackgroundProcessingWarningDialog
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.utils.DiskConsumption

class MainActivity : AppCompatActivity(), ServiceProvider {

    private lateinit var binding: ActivityMainBinding

    override lateinit var capturingService: CyfaceDataCapturingService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Targeting Android 12+ we always need to request coarse together with fine location
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                PERMISSION_REQUEST_ACCESS_FINE_LOCATION
            )
        }
        //preferences = PreferenceManager.getDefaultSharedPreferences(this)

        // If camera service is requested, check needed permissions
        /* FIXME: final boolean cameraCapturingEnabled = preferences.getBoolean(PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY, false);
        final boolean permissionsMissing = ContextCompat.checkSelfPermission(this,
            Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        /*
         * No permissions needed as we write the data to the app specific directory.
         * || ContextCompat.checkSelfPermission(this,
         * Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
         * || ContextCompat.checkSelfPermission(this,
         * Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
         */;
        if (cameraCapturingEnabled && permissionsMissing) {
            ActivityCompat.requestPermissions(this,
                new String[] {Manifest.permission.CAMERA/*
                                                             * , Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                             * Manifest.permission.READ_EXTERNAL_STORAGE
                                                             */},
                PERMISSION_REQUEST_CAMERA_AND_STORAGE_PERMISSION);
        }*/

        // If the savedInstanceState bundle isn't null, a configuration change occurred (e.g. screen rotation)
        // thus, we don't need to recreate the fragment but can just reattach the existing one
        /*fragmentManager = supportFragmentManager
        if (findViewById<View?>(R.id.main_fragment_placeholder) != null) {
            if (savedInstanceState != null) {
                mainFragment =
                    fragmentManager.findFragmentByTag(de.cyface.app.ui.MainActivity.MAIN_FRAGMENT_TAG) as MainFragment
            }
            if (mainFragment == null) {
                mainFragment = MainFragment()
            }
            fragmentManager.beginTransaction().replace(
                R.id.main_fragment_placeholder,
                mainFragment,
                de.cyface.app.ui.MainActivity.MAIN_FRAGMENT_TAG
            )
                .commit()
        } else {
            Log.w(de.cyface.app.utils.Constants.PACKAGE, "onCreate: main fragment already attached")
        }*/

        // FIXME: To access the Activities `capturingService` in Fragment.onCreate this needs to happen before `inflate` but there might be
        // Start DataCapturingService and CameraService
        try {
            capturingService = CyfaceDataCapturingService(
                applicationContext,
                AUTHORITY,
                ACCOUNT_TYPE,
                BuildConfig.cyfaceServer,
                CapturingEventHandler(),
                // Instead of registering the `DataCapturingButton/CapturingFragment` here,
                // the CapturingFragment just registers and unregisters itself
                object : DataCapturingListener {
                    override fun onFixAcquired() {}
                    override fun onFixLost() {}
                    override fun onNewGeoLocationAcquired(position: ParcelableGeoLocation?) {}
                    override fun onNewSensorDataAcquired(data: CapturedData?) {}
                    override fun onLowDiskSpace(allocation: DiskConsumption?) {}
                    override fun onSynchronizationSuccessful() {}
                    override fun onErrorState(e: Exception?) {}
                    override fun onRequiresPermission(permission: String, reason: Reason): Boolean {return false}
                    override fun onCapturingStopped() {}
                },
                DEFAULT_SENSOR_FREQUENCY
            )
            // Needs to be called after new CyfaceDataCapturingService() for the SDK to check and throw
            // a specific exception when the LOGIN_ACTIVITY was not set from the SDK using app.
            //startSynchronization(fragmentRoot.getContext())
            //dataCapturingService.addConnectionStatusListener(this)
            /*cameraService = CameraService(
                fragmentRoot.getContext(), fragmentRoot.getContext().getContentResolver(),
                de.cyface.app.utils.Constants.AUTHORITY, CameraEventHandler(), dataCapturingButton
            )*/
        } catch (e: SetupException) {
            throw IllegalStateException(e)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setting up top action bar and bottom menu navigation
        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_trips, R.id.navigation_capturing, R.id.navigation_statistics
            )
        )
        setSupportActionBar(findViewById(R.id.toolbar))
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        addMenuProvider(MenuProvider())

        // Setting up feedback email template
        /*emailIntent = generateFeedbackEmailIntent(
            this,
            getString(de.cyface.energy_settings.R.string.feedback_error_description),
            de.cyface.app.utils.Constants.SUPPORT_EMAIL
        )*/

        // Not showing manufacturer warning on each resume to increase likelihood that it's read
        showProblematicManufacturerDialog(this, false, SUPPORT_EMAIL)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PERMISSION_REQUEST_ACCESS_FINE_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    necessaryPermissionsGranted()
                } else {
                    // Close Cyface if permission has not been granted.
                    // When the user repeatedly denies the location permission, the app won't start
                    // and only starts again if the permissions are granted manually.
                    // It was always like this, but if this is a problem we need to add a screen
                    // which explains the user that this can happen.
                    finish()
                }
            }
            /*de.cyface.camera_service.Constants.PERMISSION_REQUEST_CAMERA_AND_STORAGE_PERMISSION -> if (navDrawer != null && !(grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && (grantResults.size < 2 || grantResults[1] == PackageManager.PERMISSION_GRANTED))
            ) {
                // Deactivate camera service and inform user about this
                navDrawer.deactivateCameraService()
                Toast.makeText(
                    applicationContext,
                    applicationContext.getString(de.cyface.camera_service.R.string.camera_service_off_missing_permissions),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                // Ask used which camera mode to use, video or default (shutter image)
                showCameraModeDialog()
            }*/
            else -> {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
        }
    }

    @Suppress("RedundantOverride")
    override fun onPause() {
        // dismissAllDialogs(fragmentManager); not required anymore with MaterialDialogs
        super.onPause()
    }

    override fun onResume() {
        showGnssWarningDialog(this)
        showEnergySaferWarningDialog(this)
        showRestrictedBackgroundProcessingWarningDialog(this)
        super.onResume()
    }

    private fun necessaryPermissionsGranted() {
        /* FIXME: if (fragmentRoot != null && fragmentRoot.isShown()) {
            map.showAndMoveToCurrentLocation(true)
        }*/
    }

    private class MenuProvider : androidx.core.view.MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.capturing, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            TODO("Not yet implemented")
        }
    }
}