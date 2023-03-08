package de.cyface.app.r4r

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import de.cyface.app.r4r.databinding.ActivityMainBinding
import de.cyface.app.r4r.utils.Constants.ACCOUNT_TYPE
import de.cyface.app.r4r.utils.Constants.AUTHORITY
import de.cyface.app.r4r.utils.Constants.DEFAULT_SENSOR_FREQUENCY
import de.cyface.datacapturing.CyfaceDataCapturingService
import de.cyface.datacapturing.DataCapturingListener
import de.cyface.datacapturing.exception.SetupException
import de.cyface.datacapturing.model.CapturedData
import de.cyface.datacapturing.ui.Reason
import de.cyface.persistence.model.ParcelableGeoLocation
import de.cyface.utils.DiskConsumption

class MainActivity : AppCompatActivity(), ServiceProvider {

    private lateinit var binding: ActivityMainBinding

    override lateinit var capturingService: CyfaceDataCapturingService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FIXME: To access the Activities `capturingService` in Fragment.onCreate this needs to happen before `inflate` but there might be
        // Start DataCapturingService and CameraService
        try {
            capturingService = CyfaceDataCapturingService(
                applicationContext,
                AUTHORITY,
                ACCOUNT_TYPE,
                BuildConfig.cyfaceServer,
                CapturingEventHandler(),
                /*FIXME dataCapturingButton*/ object : DataCapturingListener {
                    override fun onFixAcquired() {
                        TODO("Not yet implemented")
                    }

                    override fun onFixLost() {
                        TODO("Not yet implemented")
                    }

                    override fun onNewGeoLocationAcquired(position: ParcelableGeoLocation?) {
                        TODO("Not yet implemented")
                    }

                    override fun onNewSensorDataAcquired(data: CapturedData?) {
                        TODO("Not yet implemented")
                    }

                    override fun onLowDiskSpace(allocation: DiskConsumption?) {
                        TODO("Not yet implemented")
                    }

                    override fun onSynchronizationSuccessful() {
                        TODO("Not yet implemented")
                    }

                    override fun onErrorState(e: Exception?) {
                        TODO("Not yet implemented")
                    }

                    override fun onRequiresPermission(
                        permission: String?,
                        reason: Reason?
                    ): Boolean {
                        TODO("Not yet implemented")
                    }

                    override fun onCapturingStopped() {
                        TODO("Not yet implemented")
                    }
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