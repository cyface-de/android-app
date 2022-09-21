/*
 * Copyright 2017 Cyface GmbH
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
package de.cyface.app.ui;

import static de.cyface.app.ui.nav.view.InformationViewFragment.INFORMATION_VIEW_KEY;
import static de.cyface.app.utils.Constants.PACKAGE;
import static de.cyface.app.utils.Constants.SUPPORT_EMAIL;
import static de.cyface.camera_service.Constants.PERMISSION_REQUEST_CAMERA_AND_STORAGE_PERMISSION;
import static de.cyface.camera_service.Constants.PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY;
import static de.cyface.energy_settings.Constants.DIALOG_ENERGY_SAFER_WARNING_CODE;
import static de.cyface.energy_settings.TrackingSettings.generateFeedbackEmailIntent;
import static de.cyface.energy_settings.TrackingSettings.showEnergySaferWarningDialog;
import static de.cyface.energy_settings.TrackingSettings.showGnssWarningDialog;
import static de.cyface.energy_settings.TrackingSettings.showNoGuidanceNeededDialog;
import static de.cyface.energy_settings.TrackingSettings.showProblematicManufacturerDialog;
import static de.cyface.energy_settings.TrackingSettings.showRestrictedBackgroundProcessingWarningDialog;

import java.lang.ref.WeakReference;

import com.google.android.material.navigation.NavigationView;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TintContextWrapper;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import de.cyface.app.R;
import de.cyface.app.ui.nav.controller.NavDrawer;
import de.cyface.app.ui.nav.controller.NavDrawerListener;
import de.cyface.app.ui.nav.view.InformationViewFragment;
import de.cyface.app.ui.nav.view.MeasurementOverviewFragment;
import de.cyface.app.ui.nav.view.SettingsFragment;
import de.cyface.app.utils.Constants;
import de.cyface.camera_service.CameraModeDialog;

/**
 * The base {@code Activity} for the actual Cyface measurement client. It's called by the {@link TermsOfUseActivity}
 * class.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.3.0
 * @since 1.0.0
 */
public class MainActivity extends AppCompatActivity implements NavDrawerListener {

    /**
     * The tag used to identify logging messages send to logcat.
     */
    public final static String TAG = PACKAGE;
    /**
     * This is used to find the fragments of this activity in the FragmentManager
     */
    public final static String MAIN_FRAGMENT_TAG = PACKAGE + ".fragment.capturing";
    /**
     * This is used to find the fragments of this activity in the {@code FragmentManager}
     */
    public final static String MEASUREMENT_OVERVIEW_FRAGMENT_TAG = PACKAGE + ".fragment.measurements";
    /**
     * This is used to find the fragments of this activity in the {@code FragmentManager}
     */
    public final static String INFORMATION_VIEW_FRAGMENT_TAG = PACKAGE + ".fragment.information_view";
    /**
     * This is used to find the fragments of this activity in the {@code FragmentManager}
     */
    public final static String CAMERA_SETTINGS_FRAGMENT_TAG = PACKAGE + ".fragment.camera_settings";
    /**
     * The {@code MainFragment} which is shown by default ("home" page)
     */
    private MainFragment mainFragment;
    /**
     * The {@code FragmentManager} required to replace and find {@code Fragments}
     */
    private FragmentManager fragmentManager;
    /**
     * The {@code NavDrawer} which allows the user to change Fragments and to change preferences.
     */
    private NavDrawer navDrawer;
    /**
     * The {@code Intent} used when the user wants to send feedback.
     */
    private Intent emailIntent;
    /**
     * The {@code SharedPreferences} used to store the user's preferences.
     */
    private SharedPreferences preferences;

    /**
     * Gets the MainActivity from the context provided
     *
     * @param context the {@link Context}
     * @return the {@link MainActivity}
     */
    public static MainActivity getMainActivityFromContext(Context context) {
        return (context instanceof MainActivity ? (MainActivity)context
                : (MainActivity)((TintContextWrapper)context).getBaseContext());
    }

    /**
     * Tries to find an existing {@link MainFragment}, if it fails to do so it creates a new instance.
     *
     * @param fragmentManager The {@code FragmentManager} to search for the fragment
     * @return A {@code MainFragment}
     */
    public MainFragment getMainFragment(@NonNull final FragmentManager fragmentManager) {
        MainFragment mainFragment = (MainFragment)fragmentManager.findFragmentByTag(MAIN_FRAGMENT_TAG);
        if (mainFragment == null) {
            mainFragment = new MainFragment();
        }
        return mainFragment;
    }

    @Override
    public void homeSelected() {
        final FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        mainFragment = getMainFragment(fragmentManager);
        fragmentTransaction.replace(R.id.main_fragment_placeholder, mainFragment, MAIN_FRAGMENT_TAG).commit();
        setTitle(getString(R.string.app_name)); // Set action bar title
    }

    @Override
    public void measurementsSelected() {
        showFragment(new MeasurementOverviewFragment(), getString(R.string.drawer_title_measurements),
                MEASUREMENT_OVERVIEW_FRAGMENT_TAG);
    }

    @Override
    public void feedbackSelected() {
        startActivity(Intent.createChooser(emailIntent, getString(R.string.feedback_choose_email_app)));
    }

    @Override
    public void guideSelected() {
        homeSelected();
        if (!showGnssWarningDialog(this) && !showEnergySaferWarningDialog(this)
                && !showRestrictedBackgroundProcessingWarningDialog(this)
                && !showProblematicManufacturerDialog(this, true, SUPPORT_EMAIL)) {
            showNoGuidanceNeededDialog(this, SUPPORT_EMAIL);
        }
    }

    @Override
    public void imprintSelected() {

        final InformationViewFragment fragment = new InformationViewFragment();
        final Bundle bundle = new Bundle();
        bundle.putInt(INFORMATION_VIEW_KEY, R.layout.fragment_imprint);
        fragment.setArguments(bundle);

        showFragment(fragment, getString(R.string.drawer_title_imprint), INFORMATION_VIEW_FRAGMENT_TAG);
    }

    @Override
    public void cameraSettingsSelected() {
        showFragment(new SettingsFragment(), getString(R.string.drawer_settings),
                CAMERA_SETTINGS_FRAGMENT_TAG);
    }

    @Override
    public void logoutSelected() {
        // Nothing to do
    }

    /**
     * Displays a dialog for the user to select a camera mode (video- or picture mode).
     *
     */
    public void showCameraModeDialog() {

        // avoid crash DAT-69
        homeSelected();

        final DialogFragment cameraModeDialog = new CameraModeDialog();
        cameraModeDialog.setTargetFragment(mainFragment, DIALOG_ENERGY_SAFER_WARNING_CODE);
        cameraModeDialog.setCancelable(false);
        cameraModeDialog.show(fragmentManager, "CAMERA_MODE_DIALOG");
    }

    /**
     * Replaces the currently shown {@code android.app.Fragment} with another fragment.
     *
     * @param fragment the {@code Fragment} to show
     * @param fragmentTitle the title of the {@code Fragment} to show in the action bar
     * @param fragmentTag the {@code String} used as "tag" to find and identify the {@code Fragment}
     */
    private void showFragment(@NonNull final Fragment fragment, @NonNull final String fragmentTitle,
            @NonNull final String fragmentTag) {
        final FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        mainFragment = (MainFragment)fragmentManager.findFragmentByTag(MAIN_FRAGMENT_TAG);

        fragmentTransaction.replace(R.id.main_fragment_placeholder, fragment, fragmentTag).commit();
        setTitle(fragmentTitle);
    }

    @Override
    public void onAutoCenterMapSettingsChanged() {
        if (mainFragment == null) {
            Log.d(TAG, "Not updating map's autoCenterMapSettings as mainFragment is null");
            return;
        }

        final boolean isAutoCenterMapEnabled = preferences.getBoolean(Constants.PREFERENCES_MOVE_TO_LOCATION_KEY,
                false);
        final Map map = mainFragment.getMap();
        if (map == null) {
            Log.d(TAG, "Not updating map's autoCenterMapSettings as map is null");
            return;
        }

        map.setAutoCenterMapEnabled(isAutoCenterMapEnabled);
        Log.d(TAG, "setAutoCenterMapEnabled to " + isAutoCenterMapEnabled);
    }

    @Override
    public void synchronizationToggled() {
        // Nothing to do here
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Targeting Android 12+ we always need to request coarse together with fine location
            ActivityCompat.requestPermissions(this,
                    new String[] {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    Constants.PERMISSION_REQUEST_ACCESS_FINE_LOCATION);
        }
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // If camera service is requested, check needed permissions
        final boolean cameraCapturingEnabled = preferences.getBoolean(PREFERENCES_CAMERA_CAPTURING_ENABLED_KEY, false);
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
        }

        setContentView(R.layout.activity_main);

        // If the savedInstanceState bundle isn't null, a configuration change occurred (e.g. screen rotation)
        // thus, we don't need to recreate the fragment but can just reattach the existing one
        fragmentManager = getSupportFragmentManager();
        if (findViewById(R.id.main_fragment_placeholder) != null) {
            if (savedInstanceState != null) {
                mainFragment = (MainFragment)fragmentManager.findFragmentByTag(MAIN_FRAGMENT_TAG);
            }
            if (mainFragment == null) {
                mainFragment = new MainFragment();
            }
            fragmentManager.beginTransaction().replace(R.id.main_fragment_placeholder, mainFragment, MAIN_FRAGMENT_TAG)
                    .commit();
        } else {
            Log.w(PACKAGE, "onCreate: main fragment already attached");
        }

        // Setting up tool bar & nav bar
        final Toolbar toolbar = setupToolbar();
        final DrawerLayout drawerLayout = findViewById(R.id.drawer_layout_main_activity);
        final NavigationView navigationView = findViewById(R.id.navigation_view);
        navDrawer = new NavDrawer(this, navigationView, drawerLayout, toolbar, mainFragment);
        navDrawer.addNavDrawerListener(this);

        // Setting up feedback email template
        emailIntent = generateFeedbackEmailIntent(this, getString(R.string.feedback_error_description), SUPPORT_EMAIL);

        // Not showing manufacturer warning on each resume to increase likelihood that it's read
        showProblematicManufacturerDialog(this, false, SUPPORT_EMAIL);
    }

    /**
     * Loads the toolbar
     *
     * @return the {@code Toolbar}
     */
    Toolbar setupToolbar() {
        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        } else {
            Log.e(PACKAGE, "Action bar not found!");
        }

        return toolbar;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        switch (requestCode) {
            case Constants.PERMISSION_REQUEST_ACCESS_FINE_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mainFragment.necessaryPermissionsGranted();
                } else {
                    // Close Cyface if permission has not been granted.
                    // When the user repeatedly denies the location permission, the app won't start
                    // and only starts again if the permissions are granted manually.
                    // It was always like this, but if this is a problem we need to add a screen
                    // which explains the user that this can happen.
                    this.finish();
                }
                break;

            case PERMISSION_REQUEST_CAMERA_AND_STORAGE_PERMISSION:
                if (navDrawer != null && !(grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && (grantResults.length < 2 || (grantResults[1] == PackageManager.PERMISSION_GRANTED)))) {
                    // Deactivate camera service and inform user about this
                    navDrawer.deactivateCameraService();
                    Toast.makeText(getApplicationContext(),
                            getApplicationContext().getString(R.string.camera_service_off_missing_permissions),
                            Toast.LENGTH_LONG).show();
                } else {
                    // Ask used which camera mode to use, video or default (shutter image)
                    showCameraModeDialog();
                }
                break;

            default: {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }
    }

    @Override
    protected void onPause() {
        // dismissAllDialogs(fragmentManager); not required anymore with MaterialDialogs
        super.onPause();
    }

    @Override
    protected void onResume() {
        showGnssWarningDialog(this);
        showEnergySaferWarningDialog(this);
        showRestrictedBackgroundProcessingWarningDialog(this);
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {

        // Close NavDrawer is visible
        if (navDrawer.closeIfOpen()) {
            return;
        }

        // Do nothing if App is already closed
        if (findViewById(R.id.main_fragment_placeholder) == null) {
            return;
        }

        // Close app the usual way when MainFragment (capturing) is shown
        final MainFragment mainFragment = (MainFragment)getSupportFragmentManager()
                .findFragmentByTag(MAIN_FRAGMENT_TAG);
        if (mainFragment != null && mainFragment.isVisible()) {
            super.onBackPressed();
        }

        // Replace MeasurementOverviewFragment's eventListView with measurementListView
        final MeasurementOverviewFragment measurementOverviewFragment = (MeasurementOverviewFragment)getSupportFragmentManager()
                .findFragmentByTag(MEASUREMENT_OVERVIEW_FRAGMENT_TAG);
        if (measurementOverviewFragment != null && measurementOverviewFragment.isVisible()) {
            if (measurementOverviewFragment.onBackPressed()) {
                return;
            }
            // else "Go back to Home" - see below
        }

        // Go back to Home (MainFragment/capturing) when another Fragment is shown
        navDrawer.getView().setCheckedItem(R.id.drawer_item_home);
        homeSelected();
    }

    /**
     * Handles incoming inter process communication messages from services used by this application.
     * This is required to update the UI based on changes within those services (e.g. status).
     * - The Handler code runs all on the same (e.g. UI) thread.
     * - We don't use Broadcasts here to reduce the amount of broadcasts.
     *
     * @author Klemens Muthmann
     * @version 1.0.1
     * @since 1.0.0
     */
    private final static class IncomingMessageHandler extends Handler {

        /**
         * A weak reference to the context activity this handler handles messages for. The weak reference is
         * necessary since the lifetime of the handler might be longer than the activity's and a normal reference would
         * hinder the garbage collector to destroy the activity in that instance.
         */
        private final WeakReference<MainActivity> context;

        /**
         * Constructor creating a new completely initialized message handler.
         *
         * @param context The context {@link MainActivity} for this message handler.
         */
        IncomingMessageHandler(final MainActivity context) {
            this.context = new WeakReference<>(context);
        }

        @Override
        public void handleMessage(@SuppressWarnings("NullableProblems") final Message msg) {
            final MainActivity activity = context.get();

            if (activity == null) {
                // noinspection UnnecessaryReturnStatement
                return;
            }
            /*
             * switch (msg.what) {
             * case Message.WARNING_SPACE:
             * Log.d(TAG, "received MESSAGE about WARNING SPACE: Unbinding services !");
             * activity.unbindDataCapturingService();
             * final MainFragment mainFragment = (MainFragment)activity.getFragmentManager()
             * .findFragmentByTag(MAIN_FRAGMENT_TAG);
             * if (mainFragment != null) { // the fragment was switch just now this one can be null
             * mainFragment.dataCapturingButton.setDeactivated();
             * }
             * break;
             * default:
             * super.handleMessage(msg);
             * }
             */
        }
    }
}
