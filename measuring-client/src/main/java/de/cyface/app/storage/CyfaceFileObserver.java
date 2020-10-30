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
/*package de.cyface.app.storage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import android.os.FileObserver;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import de.cyface.persistence.model.Measurement;

/**
 * Observes which measurement exist on the phone to be displayed to the user.
 * <p>
 * You may start this observer by calling {@link #startWatching()} and you should stop it by calling
 * {@link #stopWatching()}. Changes to the measurements are handed directly to the <code>LiveData</code> instances
 * returned by {@link #getCorruptedMeasurements()}, {@link #getFinishedMeasurements()}, {@link #getOpenMeasurements()}
 * and {@link #getSyncedMeasurements()}.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.0.0
 * @since 3.0.0
 * /
public final class CyfaceFileObserver extends FileObserver {

    /**
     * The events we want to listen to (for the folders we subscribe to).
     * We don't want to be notified about accessed, opened or changed files to avoid much work load
     * during capturing if the user is checking his measurements at the same time.
     * /
    private final static int FILE_OBSERVER_MASK = CREATE + DELETE_SELF + DELETE + MOVED_FROM + MOVED_TO;
    /**
     * The <code>TAG</code> used identify Logcat messages from an instance of this class.
     * /
    private static final String TAG = "de.cyface.app.m_loader";
    /**
     * The parent directories for storing measurement data in different states.
     * /
    private final List<String> measurementParentDirectories;
    /**
     * The root directory of the measurement storage area.
     * /
    private final String rootPath;
    /**
     * Instance of {@link FileUtils} used to handle CRUD operations on the measurement storage area.
     * /
    private final FileUtils fileUtils;
    /**
     * The storage for open measurements currently running.
     * /
    private final MeasurementsStorage openMeasurements;
    /**
     * The storage for already synchronized measurements, if any of their data needs to be kept around.
     * /
    private final MeasurementsStorage syncedMeasurements;
    /**
     * The storage for measurements finished but not yet synchronized.
     * /
    private final MeasurementsStorage finishedMeasurements;
    /**
     * The storage for measurements that have been corrupted. In an ideal world this does not happen, but sometimes
     * there are crashes that corrupt a measurement during capturing or synchronization for example.
     * /
    private final MeasurementsStorage corruptedMeasurements;
    /**
     * The list of observers on the files in the directory containing all the measurement data.
     * /
    private List<SingleFileObserver> mObservers;

    /**
     * Creates a new completely initialized <code>MeasurementObserver</code> with access to the stored measurements
     * using the provided {@link FileUtils} instance.
     *
     * @param fileUtils A utility object providing access to the directory containing the measurement data.
     * /
    public MeasurementObserver(final @NonNull FileUtils fileUtils) {
        super(fileUtils.getMeasurementsRootPath(), FILE_OBSERVER_MASK); // See
        // https://stackoverflow.com/a/20609634/5815054
        this.rootPath = fileUtils.getMeasurementsRootPath();
        this.fileUtils = fileUtils;
        measurementParentDirectories = new ArrayList<>();
        measurementParentDirectories.add(fileUtils.getOpenMeasurementsDirPath());
        measurementParentDirectories.add(fileUtils.getFinishedMeasurementsDirPath());
        measurementParentDirectories.add(fileUtils.getSynchronizedMeasurementsDirPath());
        measurementParentDirectories.add(fileUtils.getCorruptedMeasurementsDirPath());
        measurementParentDirectories.add(fileUtils.getMeasurementsRootPath());

        openMeasurements = new MeasurementsStorage();
        syncedMeasurements = new MeasurementsStorage();
        finishedMeasurements = new MeasurementsStorage();
        corruptedMeasurements = new MeasurementsStorage();
        startWatching();
    }

    @Override
    public void startWatching() {
        if (mObservers != null) {
            // Log.d(TAG, "FILE: already initialized, ignoring");
            return;
        }
        mObservers = new ArrayList<>();
        addToWatchList(new File(rootPath));
    }

    /**
     * Adds the provided file and, if it is a directory, also all its children recursively to the files observed for
     * changes by this <code>MeasurementObserver</code>.
     *
     * @param file The file to observe for changes.
     * /
    private void addToWatchList(final @NonNull File file) {
        // Collect all (sub) dirs
        Stack<String> stack = new Stack<>();
        stack.push(file.getAbsolutePath());
        while (!stack.empty()) {
            final String path = stack.pop();
            final File dir = new File(path);
            final SingleFileObserver singleFileObserver = new SingleFileObserver(path);
            Log.d(TAG, "FILE: Start watching: " + singleFileObserver.path);
            mObservers.add(singleFileObserver);
            singleFileObserver.startWatching();

            if (!isMeasurementsParentDir(dir)) {
                final String parentDirectory = dir.getParent();
                if (parentDirectory.equals(fileUtils.getOpenMeasurementsDirPath())) {
                    Log.d(TAG, "FILE: add open " + dir);
                    openMeasurements.add(dir);
                } else if (parentDirectory.equals(fileUtils.getFinishedMeasurementsDirPath())) {
                    Log.d(TAG, "FILE: add finished " + dir);
                    finishedMeasurements.add(dir);
                } else if (parentDirectory.equals(fileUtils.getSynchronizedMeasurementsDirPath())) {
                    Log.d(TAG, "FILE: add synced " + dir);
                    syncedMeasurements.add(dir);
                } else if (parentDirectory.equals(fileUtils.getCorruptedMeasurementsDirPath())) {
                    Log.d(TAG, "FILE: add corrupted " + dir);
                    corruptedMeasurements.add(dir);
                } else if (!dir.getAbsolutePath().equals(fileUtils.getMeasurementsRootPath())) {
                    throw new IllegalStateException("Unknown measurement state of file: " + dir);
                }
            }

            // Watch sub dirs
            File[] subDirs = new File(path).listFiles(FileUtils.directoryFilter());
            if (subDirs == null)
                continue;
            for (File directory : subDirs) {
                stack.push(directory.getPath());
            }
        }
    }

    @Override
    public void stopWatching() {
        if (mObservers == null)
            return;

        // Stop watching all dir and sub dirs
        for (int i = 0; i < mObservers.size(); ++i) {
            // Log.d(TAG, "FILE: Stop watching: " + mObservers.get(i).path);
            mObservers.get(i).stopWatching();
        }

        mObservers.clear();
        mObservers = null;
    }

    /**
     * Removes the provided file and all children from the list of watched files.
     *
     * @param file The file to remove from the list of watched files.
     * /
    private void removeFromWatchList(final @NonNull File file) {
        boolean wasSuccessfullyRemoved = false;
        for (SingleFileObserver observer : mObservers) {
            if (observer.path.startsWith(file.getAbsolutePath())) {
                wasSuccessfullyRemoved |= mObservers.remove(observer);
                observer.stopWatching();
                // Log.d(TAG, "FILE: Stop watching " + observer.path);

                if (!isMeasurementsParentDir(file)) {
                    final String parentDirectory = file.getParent();
                    if (parentDirectory.equals(fileUtils.getOpenMeasurementsDirPath())) {
                        Log.d(TAG, "FILE: removed open " + file);
                        openMeasurements.remove(file);
                    } else if (parentDirectory.equals(fileUtils.getFinishedMeasurementsDirPath())) {
                        Log.d(TAG, "FILE: removed finished " + file);
                        finishedMeasurements.remove(file);
                        return;
                    } else if (parentDirectory.equals(fileUtils.getSynchronizedMeasurementsDirPath())) {
                        Log.d(TAG, "FILE: removed synced " + file);
                        syncedMeasurements.remove(file);
                    } else if (parentDirectory.equals(fileUtils.getCorruptedMeasurementsDirPath())) {
                        Log.d(TAG, "FILE: removed corrupted " + file);
                        corruptedMeasurements.remove(file);
                    } else if (!file.getAbsolutePath().equals(fileUtils.getMeasurementsRootPath())) {
                        throw new IllegalStateException("Unknown measurement state of file: " + file);
                    }
                }
            }
        }
        if (!wasSuccessfullyRemoved) {
            throw new IllegalStateException("Failed to remove from watch list: " + file);
        }
    }

    @Override
    public void onEvent(int event, final String path) {
        event &= FileObserver.ALL_EVENTS; // See https://stackoverflow.com/a/20609634/5815054

        File file = new File(path);
        if (file.getName().equals("null"))
            file = file.getParentFile();
        final boolean isMeasurementsParentDir = isMeasurementsParentDir(file);
        final boolean isMeasurementDirCreated = event == FileObserver.CREATE && file.isDirectory()
                && !isMeasurementsParentDir;
        final boolean isMeasurementDirDeleted = event == FileObserver.DELETE_SELF && !isMeasurementsParentDir;
        final boolean isMeasurementDirMovedAway = event == FileObserver.MOVED_FROM && !isMeasurementsParentDir;
        final boolean isMeasurementDirMovedTo = event == FileObserver.MOVED_TO && !isMeasurementsParentDir;
        final boolean isParentDirDeleted = event == FileObserver.DELETE_SELF && isMeasurementsParentDir;
        final boolean isParentDirCreated = event == FileObserver.CREATE && file.isDirectory()
                && isMeasurementsParentDir;

        if (isMeasurementDirCreated || isMeasurementDirMovedTo) {
            // Log.d(TAG, "FILE: Measurement created of moved to: " + file.getAbsolutePath());
            addToWatchList(file);
        } else if (isMeasurementDirDeleted || isMeasurementDirMovedAway) {
            // Log.d(TAG, "FILE: Measurement deleted or moved away: " + file.getAbsolutePath());
            removeFromWatchList(file);
        } else if (isParentDirDeleted) {
            Log.w(TAG, "FILE: parent dir was deleted ! " + file.getAbsolutePath());
            removeFromWatchList(file);
        } else if (isParentDirCreated) {
            Log.w(TAG, "FILE: Parent dir created: " + file.getAbsolutePath());
            addToWatchList(file);
        } else {
            if (event == 0 || event == FileObserver.ACCESS || event == CLOSE_NOWRITE || event == ATTRIB || event == OPEN
                    || event == MOVE_SELF) {
                return;
            }
            if (event == CREATE) {
                // Nothing to do yet, file created
                return;
            }
            if (event == FileObserver.DELETE && !file.isDirectory()) {
                // Nothing to do yet, file deleted
                return;
            }
            Log.d(TAG, "FILE: UNKNOWN (" + (file.isDirectory() ? "dir" : "file") + ") event " + event + ": "
                    + file.getAbsolutePath());
        }
    }

    /**
     * Checks if the file is one of the parent directories which *contain* measurement dirs, e.g.
     * open, synced, finished, corrupted.
     *
     * @param file The file to check
     * @return True of the file one of those dirs.
     * /
    private boolean isMeasurementsParentDir(final File file) {
        for (String parentDir : measurementParentDirectories) {
            if (parentDir.equals(file.getAbsolutePath())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return The storage for open measurements currently running.
     * /
    public @NonNull LiveData<List<Measurement>> getOpenMeasurements() {
        return openMeasurements.getData();
    }

    /**
     * @return The storage for already synchronized measurements, if any of their data needs to be kept around.
     * /
    public @NonNull LiveData<List<Measurement>> getSyncedMeasurements() {
        return syncedMeasurements.getData();
    }

    /**
     * @return All currently finished measurements stored on this device.
     * /
    public @NonNull LiveData<List<Measurement>> getFinishedMeasurements() {
        return finishedMeasurements.getData();
    }

    /**
     * @return The storage for measurements that have been corrupted. In an ideal world this does not happen, but
     *         sometimes there are crashes that corrupt a measurement during capturing or synchronization for example.
     * /
    public @NonNull LiveData<List<Measurement>> getCorruptedMeasurements() {
        return corruptedMeasurements.getData();
    }

    /**
     * An observer listening for changes to a single file in the local Android file system.
     *
     * @author Armin Schnabel
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 3.0.0
     * /
    private final class SingleFileObserver extends FileObserver {
        /**
         * The path to the file to observe.
         * /
        private String path;

        /**
         * Creates a new completely initialized observer for a single file.
         *
         * @param path The path to the file to observe.
         * /
        SingleFileObserver(final @NonNull String path) {
            super(path, FILE_OBSERVER_MASK);
            this.path = path;
        }

        @Override
        public void onEvent(final int event, final @NonNull String path) {
            String newPath = this.path + "/" + path;
            MeasurementObserver.this.onEvent(event, newPath);
        }

    }
}*/
