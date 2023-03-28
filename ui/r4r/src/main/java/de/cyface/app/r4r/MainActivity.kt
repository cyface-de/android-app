package de.cyface.app.r4r

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AccountManagerFuture
import android.accounts.AuthenticatorException
import android.accounts.OperationCanceledException
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import de.cyface.app.r4r.databinding.ActivityMainBinding
import de.cyface.app.r4r.ui.capturing.CapturingViewModel
import de.cyface.app.r4r.ui.capturing.CapturingViewModelFactory
import de.cyface.app.r4r.utils.Constants.ACCOUNT_TYPE
import de.cyface.app.r4r.utils.Constants.AUTHORITY
import de.cyface.app.r4r.utils.Constants.SUPPORT_EMAIL
import de.cyface.app.r4r.utils.Constants.TAG
import de.cyface.app.utils.SharedConstants.DEFAULT_SENSOR_FREQUENCY
import de.cyface.app.utils.SharedConstants.PERMISSION_REQUEST_ACCESS_FINE_LOCATION
import de.cyface.app.utils.SharedConstants.PREFERENCES_SYNCHRONIZATION_KEY
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.DataCapturingListener
import de.cyface.datacapturing.exception.SetupException
import de.cyface.datacapturing.model.CapturedData
import de.cyface.datacapturing.persistence.CapturingPersistenceBehaviour
import de.cyface.datacapturing.ui.Reason
import de.cyface.energy_settings.TrackingSettings.showEnergySaferWarningDialog
import de.cyface.energy_settings.TrackingSettings.showGnssWarningDialog
import de.cyface.energy_settings.TrackingSettings.showProblematicManufacturerDialog
import de.cyface.energy_settings.TrackingSettings.showRestrictedBackgroundProcessingWarningDialog
import de.cyface.persistence.DefaultPersistenceLayer
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.synchronization.Constants.AUTH_TOKEN_TYPE
import de.cyface.synchronization.WiFiSurveyor
import de.cyface.utils.DiskConsumption
import de.cyface.utils.Validate
import java.io.IOException

class MainActivity : AppCompatActivity(), ServiceProvider {

    private lateinit var binding: ActivityMainBinding

    override lateinit var capturingService: CyfaceDataCapturingService

    private lateinit var persistenceLayer: DefaultPersistenceLayer<CapturingPersistenceBehaviour>

    private lateinit var navController: NavController

    private lateinit var toolbar: Toolbar

    /**
     * The `SharedPreferences` used to store the user's preferences.
     */
    private lateinit var preferences: SharedPreferences

    /**
     * Shared instance of the [CapturingViewModel] which is used by multiple `Fragments.
     */
    @Suppress("unused") // Used by Fragments
    private val capturingViewModel: CapturingViewModel by viewModels {
        CapturingViewModelFactory(
            persistenceLayer.measurementRepository!!, persistenceLayer.eventRepository!!
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(
                this, ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Targeting Android 12+ we always need to request coarse together with fine location
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    ACCESS_FINE_LOCATION,
                    ACCESS_COARSE_LOCATION
                ), PERMISSION_REQUEST_ACCESS_FINE_LOCATION
            )
        }
        preferences = PreferenceManager.getDefaultSharedPreferences(this)

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
                    override fun onRequiresPermission(permission: String, reason: Reason): Boolean {
                        return false
                    }

                    override fun onCapturingStopped() {}
                },
                DEFAULT_SENSOR_FREQUENCY
            )
            persistenceLayer = capturingService.persistenceLayer
            // Needs to be called after new CyfaceDataCapturingService() for the SDK to check and throw
            // a specific exception when the LOGIN_ACTIVITY was not set from the SDK using app.
            startSynchronization(applicationContext)
            //FIXME: dataCapturingService.addConnectionStatusListener(this)
            /*cameraService = CameraService(
                fragmentRoot.getContext(), fragmentRoot.getContext().getContentResolver(),
                de.cyface.app.utils.Constants.AUTHORITY, CameraEventHandler(), dataCapturingButton
            )*/
        } catch (e: SetupException) {
            throw IllegalStateException(e)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setting up top action bar & bottom menu (no sidebar, see RFR-333]
        // Not using `findNavController()` as `FragmentContainerView` in `activity_main.xml` does not
        // work with with `findNavController()` (https://stackoverflow.com/a/60434988/5815054).
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        setupActionBarWithNavController(
            navController, AppBarConfiguration(
                // Adding top-level destinations so they are added to the back stack while navigating
                setOf(
                    R.id.navigation_trips,
                    R.id.navigation_capturing,
                    R.id.navigation_statistics
                )
            )
        )

        // Not showing manufacturer warning on each resume to increase likelihood that it's read
        showProblematicManufacturerDialog(this, false, SUPPORT_EMAIL)
    }

    /**
     * Fixes "home" (back) button in the top action bar when in the fragment details fragment.
     */
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>, grantResults: IntArray
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

    /**
     * Starts the synchronization.
     *
     * Creates an [Account] if there is none as an account is required for synchronization. If there was
     * no account the synchronization is started when the async account creation future returns to ensure
     * the account is available at that point.
     *
     * @param context the [Context] to load the [AccountManager]
     */
    fun startSynchronization(context: Context?) {
        val accountManager = AccountManager.get(context)
        val validAccountExists = accountWithTokenExists(accountManager)
        if (validAccountExists) {
            try {
                Log.d(TAG, "startSynchronization: Starting WifiSurveyor with exiting account.")
                capturingService.startWifiSurveyor()
            } catch (e: SetupException) {
                throw java.lang.IllegalStateException(e)
            }
            return
        }

        // The LoginActivity is called by Android which handles the account creation
        Log.d(TAG, "startSynchronization: No validAccountExists, requesting LoginActivity")
        accountManager.addAccount(
            ACCOUNT_TYPE,
            AUTH_TOKEN_TYPE,
            null,
            null,
            this,
            { future: AccountManagerFuture<Bundle?> ->
                val accountManager1 = AccountManager.get(context)
                try {
                    // allows to detect when LoginActivity is closed
                    future.result

                    // The LoginActivity created a temporary account which cannot be used for synchronization.
                    // As the login was successful we now register the account correctly:
                    val account = accountManager1.getAccountsByType(ACCOUNT_TYPE)[0]
                    Validate.notNull(account)

                    // Set synchronizationEnabled to the current user preferences
                    val syncEnabledPreference =
                        preferences.getBoolean(PREFERENCES_SYNCHRONIZATION_KEY, true)
                    Log.d(
                        WiFiSurveyor.TAG,
                        "Setting syncEnabled for new account to preference: $syncEnabledPreference"
                    )
                    capturingService.wiFiSurveyor.makeAccountSyncable(
                        account, syncEnabledPreference
                    )
                    Log.d(TAG, "Starting WifiSurveyor with new account.")
                    capturingService.startWifiSurveyor()
                } catch (e: OperationCanceledException) {
                    // Remove temp account when LoginActivity is closed during login [CY-5087]
                    val accounts = accountManager1.getAccountsByType(ACCOUNT_TYPE)
                    if (accounts.isNotEmpty()) {
                        val account = accounts[0]
                        accountManager1.removeAccount(account, null, null)
                    }
                    // This closes the app when the LoginActivity is closed
                    this.finish()
                } catch (e: AuthenticatorException) {
                    throw java.lang.IllegalStateException(e)
                } catch (e: IOException) {
                    throw java.lang.IllegalStateException(e)
                } catch (e: SetupException) {
                    throw java.lang.IllegalStateException(e)
                }
            },
            null
        )
    }

    /**
     * Checks if there is an account with an authToken.
     *
     * @param accountManager A reference to the [AccountManager]
     * @return true if there is an account with an authToken
     * @throws RuntimeException if there is more than one account
     */
    private fun accountWithTokenExists(accountManager: AccountManager): Boolean {
        val existingAccounts = accountManager.getAccountsByType(ACCOUNT_TYPE)
        Validate.isTrue(existingAccounts.size < 2, "More than one account exists.")
        return existingAccounts.isNotEmpty()
    }
}