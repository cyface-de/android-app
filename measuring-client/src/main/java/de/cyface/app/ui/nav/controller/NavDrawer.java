/*
 * Copyright 2017-2023 Cyface GmbH
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
package de.cyface.app.ui.nav.controller;

import static de.cyface.app.utils.SharedConstants.PREFERENCES_CENTER_MAP_KEY;
import static de.cyface.app.utils.SharedConstants.PREFERENCES_SYNCHRONIZATION_KEY;
import static de.cyface.app.utils.Constants.TAG;
import static de.cyface.camera_service.Constants.PERMISSION_REQUEST_CAMERA_AND_STORAGE_PERMISSION;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY;

import java.util.Collection;
import java.util.HashSet;

import com.google.android.material.navigation.NavigationView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import de.cyface.app.R;
import de.cyface.app.ui.MainActivity;
import de.cyface.app.ui.MainFragment;
import de.cyface.datacapturing.CyfaceDataCapturingService;
import de.cyface.synchronization.exception.SynchronisationException;
import de.cyface.synchronization.WiFiSurveyor;

/**
 * The Nav Drawer is a menu which allows the user to switch settings with one click and to access other
 * {@link Fragment}s of the app.
 *
 * @author Armin Schnabel
 * @version 1.4.1
 * @since 1.0.0
 */
public class NavDrawer implements NavigationView.OnNavigationItemSelectedListener {

    private final DrawerLayout layout;
    private final Collection<NavDrawerListener> listener;
    private final SharedPreferences preferences;
    private final MainActivity mainActivity;
    /**
     * The {@link MainFragment} required to access the {@link WiFiSurveyor} syncable setting.
     */
    private final MainFragment mainFragment;
    private final SwitchCompat cameraServiceToggle;
    private final NavigationView view;

    public NavDrawer(final MainActivity mainActivity, final NavigationView view, final DrawerLayout layout,
            final Toolbar toolbar, @NonNull final MainFragment mainFragment) {
        this.view = view;
        listener = new HashSet<>();
        this.layout = layout;
        this.mainActivity = mainActivity;
        this.mainFragment = mainFragment;

        view.setNavigationItemSelectedListener(this);

        // setup nav bar setting switches
        final Context applicationContext = mainActivity.getApplicationContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        final SwitchCompat zoomToLocationToggle = (SwitchCompat)view.getMenu()
                .findItem(R.id.drawer_setting_zoom_to_location).getActionView();
        final SwitchCompat synchronizationToggle = (SwitchCompat)view.getMenu()
                .findItem(R.id.drawer_setting_synchronization).getActionView();

        // final SwitchCompat connectToExternalSpeedSensorToggle = (SwitchCompat)view.getMenu()
        // .findItem(R.id.drawer_setting_speed_sensor).getActionView();
        cameraServiceToggle = (SwitchCompat)view.getMenu().findItem(R.id.drawer_setting_pictures).getActionView();

        zoomToLocationToggle.setChecked(preferences.getBoolean(PREFERENCES_CENTER_MAP_KEY, false));
        // SynchronizationEnabled is set to the user preference when account is created
        final boolean syncEnabledPreference = preferences.getBoolean(PREFERENCES_SYNCHRONIZATION_KEY, true);
        Log.d(WiFiSurveyor.TAG, "Setting navDrawer switch to syncEnabledPreference: " + syncEnabledPreference);
        synchronizationToggle.setChecked(syncEnabledPreference);
        /*
         * final boolean bluetoothIsConfigured = preferences.getString(BLUETOOTHLE_DEVICE_MAC_KEY, null) != null
         * && preferences.getFloat(BLUETOOTHLE_WHEEL_CIRCUMFERENCE, 0.0F) > 0.0F;
         * connectToExternalSpeedSensorToggle.setChecked(bluetoothIsConfigured);
         */
        cameraServiceToggle.setChecked(preferences.getBoolean(PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY, false));

        zoomToLocationToggle.setOnCheckedChangeListener(new AutoCenterMapSettingsChangedListener());
        synchronizationToggle.setOnCheckedChangeListener(new SynchronizationToggleListener());
        // connectToExternalSpeedSensorToggle.setOnClickListener(new ConnectToExternalSpeedSensorToggleListener());
        cameraServiceToggle.setOnCheckedChangeListener(new PicturesToggleListener());

        final ActionBarDrawerToggle drawerToggle = setupDrawerToggle(layout, toolbar);
        layout.addDrawerListener(drawerToggle); // Tie DrawerLayout events to the ActionBarToggle
    }

    public void deactivateCameraService() {
        cameraServiceToggle.setChecked(false);
        final SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY, false);
        editor.apply();
    }

    public void addNavDrawerListener(final NavDrawerListener navDrawerListener) {
        if (navDrawerListener == null) {
            throw new IllegalStateException("Invalid value for nav drawer listener: null.");
        }
        this.listener.add(navDrawerListener);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.drawer_item_home:
                homeSelected(item);
                break;
            case R.id.drawer_item_measurements:
                measurementsSelected(item);
                break;
            case R.id.drawer_item_guide:
                guideSelected(item);
                break;
            case R.id.drawer_item_feedback:
                feedbackSelected(item);
                break;
            case R.id.drawer_item_imprint:
                imprintSelected(item);
                break;
            case R.id.drawer_item_logout:
                logoutSelected(item);
                break;
            case R.id.drawer_settings_camera:
                cameraSettingsSelected(item);
                break;
            case R.id.drawer_setting_zoom_to_location:
            case R.id.drawer_setting_synchronization:
                // case R.id.drawer_setting_speed_sensor:
            case R.id.drawer_setting_pictures:
                ((SwitchCompat)item.getActionView()).toggle();
                break;
            default:
                return false;
        }
        return true;
    }

    private void homeSelected(final MenuItem item) {
        for (NavDrawerListener listener : this.listener) {
            listener.homeSelected();
        }
        finishSelection(item);
    }

    private void measurementsSelected(final MenuItem item) {
        for (NavDrawerListener listener : this.listener) {
            listener.measurementsSelected();
        }
        finishSelection(item);
    }

    private void guideSelected(final MenuItem item) {
        for (NavDrawerListener listener : this.listener) {
            listener.guideSelected();
        }
        finishSelection(item);
    }

    private void feedbackSelected(final MenuItem item) {
        for (NavDrawerListener listener : this.listener) {
            listener.feedbackSelected();
        }
        finishSelection(item);
    }

    private void imprintSelected(final MenuItem item) {
        for (NavDrawerListener listener : this.listener) {
            listener.imprintSelected();
        }
        finishSelection(item);
    }

    private void logoutSelected(final MenuItem item) {
        item.setEnabled(false);
        for (NavDrawerListener listener : this.listener) {
            listener.logoutSelected();
        }
        final CyfaceDataCapturingService dataCapturingService = mainFragment.getDataCapturingService();
        try {
            dataCapturingService.removeAccount(dataCapturingService.getWiFiSurveyor().getAccount().name);
        } catch (final SynchronisationException e) {
            throw new IllegalStateException(e);
        }
        // Show login screen
        mainFragment.startSynchronization(view.getContext());
        item.setEnabled(true);
        layout.closeDrawers();
    }

    private void cameraSettingsSelected(final MenuItem item) {
        for (NavDrawerListener listener : this.listener) {
            listener.cameraSettingsSelected();
        }
        finishSelection(item);
    }

    private void finishSelection(final MenuItem item) {
        item.setChecked(true); // Highlight the selected item has been done by NavigationView
        layout.closeDrawers();
    }

    public boolean closeIfOpen() {
        if (layout.isDrawerOpen(GravityCompat.START)) {
            layout.closeDrawers();
            return true;
        } else {
            return false;
        }
    }

    private ActionBarDrawerToggle setupDrawerToggle(final DrawerLayout layout, final Toolbar toolbar) {
        ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(mainActivity, layout, toolbar, R.string.app_name,
                R.string.app_name);
        // This is necessary to change the icon of the Drawer Toggle upon state change.
        drawerToggle.syncState();
        return drawerToggle;
    }

    /**
     * A listener which is called when the "zoom to location on updates" toggle in the {@link NavDrawer} is clicked.
     */
    private class AutoCenterMapSettingsChangedListener implements CompoundButton.OnCheckedChangeListener {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

            final Context applicationContext = view.getContext().getApplicationContext();

            // happened on Pixel 2 XL
            if (mainFragment == null) {
                Log.w(TAG, "MainFragment is null, ignoring ZoomToLocationToggle change");
                return;
            }

            final SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(PREFERENCES_CENTER_MAP_KEY, isChecked);
            editor.apply();

            if (isChecked) {
                Toast.makeText(applicationContext, de.cyface.app.utils.R.string.zoom_to_location_enabled_toast, Toast.LENGTH_LONG).show();
            }

            for (NavDrawerListener listener : NavDrawer.this.listener) {
                listener.onAutoCenterMapSettingsChanged();
            }
        }
    }

    /**
     * A listener which is called when the synchronization toggle in the {@link NavDrawer} is clicked.
     */
    private class SynchronizationToggleListener implements CompoundButton.OnCheckedChangeListener {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

            final Context applicationContext = buttonView.getContext().getApplicationContext();

            // happened on Pixel 2 XL
            if (mainFragment == null) {
                Log.w(TAG, "MainFragment is null, ignoring syncEnabled change");
                return;
            }

            // Update both, the preferences and WifiSurveyor's synchronizationEnabled status
            Log.d(WiFiSurveyor.TAG, (isChecked ? "Enable" : "Disable") + " sync and update preferences");
            mainFragment.getDataCapturingService().getWiFiSurveyor().setSyncEnabled(isChecked);
            preferences.edit().putBoolean(PREFERENCES_SYNCHRONIZATION_KEY, isChecked).apply();

            // Show warning to user (storage gets filled)
            if (!isChecked) {
                Toast.makeText(applicationContext, de.cyface.app.utils.R.string.sync_disabled_toast, Toast.LENGTH_LONG).show();
            }
        }
    }

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

    /**
     * A listener which is called when the camera toggle in the {@link NavDrawer} is clicked.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 2.0.0
     */
    private class PicturesToggleListener implements CompoundButton.OnCheckedChangeListener {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            final Context applicationContext = view.getContext().getApplicationContext();
            final SharedPreferences.Editor editor = preferences.edit();

            // Disable camera
            if (!buttonView.isChecked()) {
                editor.putBoolean(PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY, false).apply();
                return;
            }

            // No rear camera found to be enabled - we explicitly only support rear camera for now
            @SuppressLint("UnsupportedChromeOsCameraSystemFeature")
            final boolean noCameraFound = !applicationContext.getPackageManager()
                    .hasSystemFeature(PackageManager.FEATURE_CAMERA);
            if (noCameraFound) {
                buttonView.setChecked(false);
                Toast.makeText(applicationContext, R.string.no_camera_available_toast, Toast.LENGTH_LONG).show();
                return;
            }

            // Request permission for camera capturing
            final boolean permissionsGranted = ActivityCompat.checkSelfPermission(applicationContext,
                    Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            /*
             * No permissions needed as we write the data to the app specific directory.
             * && ActivityCompat.checkSelfPermission(applicationContext,
             * Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
             * && ActivityCompat.checkSelfPermission(applicationContext,
             * Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
             */;
            if (!permissionsGranted) {
                ActivityCompat.requestPermissions(mainActivity,
                        new String[] {Manifest.permission.CAMERA/*
                                                                 * ,
                                                                 * Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                                 * Manifest.permission.READ_EXTERNAL_STORAGE
                                                                 */},
                        PERMISSION_REQUEST_CAMERA_AND_STORAGE_PERMISSION);
            } else {
                // Ask user to select camera mode
                mainActivity.showCameraModeDialog();
            }

            // Enable camera capturing feature
            editor.putBoolean(PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY, true).apply();
        }
    }

    public NavigationView getView() {
        return view;
    }
}
