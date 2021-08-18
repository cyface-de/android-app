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
package de.cyface.app.ui.nav.controller;

import static de.cyface.app.utils.Constants.DATABASE_NAME;
import static de.cyface.app.utils.Constants.TAG;
import static de.cyface.utils.Utils.informMediaScanner;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;

import org.apache.commons.lang3.Validate;

import de.cyface.app.R;
import de.cyface.persistence.DefaultFileAccess;
import de.cyface.persistence.serialization.Point3dFile;

/**
 * Async task which exports the measurement data without the image data.
 *
 * TODO: AsyncTasks all run on the same thread this is only for short running operations!!
 * this will block e.g. authRequest and delete- async tasks!
 * see min 3:45 for alternatives:
 * https://www.youtube.com/watch?v=jtlRNNhane0&list=PLWz5rJ2EKKc9CBxr3BVjPTPoDPLdPIFCE&index=4
 * see other videos for intros on those alternatives
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 1.1.1
 * @since 1.0.0
 */
public class ExportTask extends AsyncTask<Void, Void, Long> {

    private final WeakReference<Context> contextReference;
    private final String targetPathTimestamp;

    public ExportTask(@NonNull final Context context) {
        this.contextReference = new WeakReference<>(context);
        targetPathTimestamp = "_" + new SimpleDateFormat("yyyy-MM-dd_H-m", Locale.GERMANY).format(new Date());
    }

    @Override
    protected Long doInBackground(final Void... params) {

        final Context context = contextReference.get();
        if (context == null) {
            Log.w(TAG, "Context reference is null, ignoring task.");
            return null;
        }

        // Export sensor data
        final DefaultFileAccess fileAccess = new DefaultFileAccess();
        final File accelerations = fileAccess.getFolderPath(context, Point3dFile.ACCELERATIONS_FOLDER_NAME);
        final File rotations = fileAccess.getFolderPath(context, Point3dFile.ROTATIONS_FOLDER_NAME);
        final File directions = fileAccess.getFolderPath(context, Point3dFile.DIRECTIONS_FOLDER_NAME);
        long bytesTransferred = 0L;
        final UUID exportIdentifier = UUID.randomUUID();
        try {
            bytesTransferred += exportFolder(context, accelerations, exportIdentifier);
            bytesTransferred += exportFolder(context, rotations, exportIdentifier);
            bytesTransferred += exportFolder(context, directions, exportIdentifier);

            // Export all database files - including "wal" which contains recent unmerged database changes
            final File parent = context.getDatabasePath(DATABASE_NAME).getParentFile();
            Validate.notNull(parent);
            bytesTransferred += exportFolder(context, parent,
                    exportIdentifier);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }

        return bytesTransferred;
    }

    @Override
    protected void onPostExecute(final Long bytesTransferred) {
        final Context context = contextReference.get();
        if (context == null) {
            Log.w(TAG, "Context reference is null, ignoring task.");
            return;
        }
        try {
            // Attention: getContext() is not marked red but not supported API<23! Issue 185848
            new AlertDialog.Builder(context).setTitle(R.string.title_menu_item_export)
                    .setMessage(
                            context.getString(R.string.toast_export) + " " + humanReadableByteCount(bytesTransferred))
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    }).show();
        } catch (final NullPointerException e) {
            Log.d(TAG, "Dialog (export successful) not shown: Activity not active.");
            // We don't need to report this to Sentry as it's ok if this happens (app is closed)
        }
    }

    /**
     * Zips and exports a folder to the {@code Environment#DIRECTORY_DOWNLOADS}.
     *
     * @param context the {@code Context} to get the paths
     * @param sourceFolder the folder to export
     * @param exportIdentifier A globally unique identifier used to group files created by one export call and to
     *            distinguish those files from different export calls made in the same minute potentially on a different
     *            device.
     * @return the number of bytes read
     */
    private long exportFolder(@NonNull final Context context, @NonNull final File sourceFolder,
            @NonNull final UUID exportIdentifier) throws IOException {

        final File[] files = sourceFolder.listFiles();
        Validate.notNull(files);
        if (!sourceFolder.exists() || files.length == 0) {
            return 0L;
        }

        // Export zipped folder
        OutputStream targetOutputStream = null;
        try {
            // TODO This should not use a random UUID but would be better to use the device id. Unfortunately we require
            // a different architecture to get access to that identifier.
            final String fileName = "cyface-" + sourceFolder.getName() + targetPathTimestamp + "_" + exportIdentifier
                    + ".zip";

            final long bytesTransferred;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                final ContentResolver resolver = contextReference.get().getContentResolver();
                final ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, MediaStore.Downloads.CONTENT_TYPE);
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                final Uri outputUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
                Validate.notNull(outputUri);
                targetOutputStream = resolver.openOutputStream(outputUri);
                bytesTransferred = zipFolder(sourceFolder, targetOutputStream);
            } else {
                // Create target directory
                final File downloadDirectory = Environment
                        .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadDirectory.exists()) {
                    if (!downloadDirectory.mkdirs()) {
                        throw new IllegalStateException("Failed to create target directory");
                    }
                }
                if (!downloadDirectory.canWrite()) {
                    throw new IllegalStateException("Download target not writable");
                }

                final File target = new File(downloadDirectory, fileName);
                targetOutputStream = new FileOutputStream(target);
                bytesTransferred = zipFolder(sourceFolder, targetOutputStream);

                informMediaScanner(context, target);
            }

            return bytesTransferred;
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        } finally {
            if (targetOutputStream != null) {
                targetOutputStream.close();
            }
        }
    }

    /**
     * Zips a folder and it's sub elements.
     *
     * @param source the folder to be zipped.
     * @param targetOutputStream the target stream to write the zip file to
     * @return the number of bytes read
     * @throws IOException when it fails to zip the folder
     */
    private long zipFolder(final File source, final OutputStream targetOutputStream) throws IOException {

        long bytesTransferred = 0L;

        if (!source.exists()) {
            throw new IllegalArgumentException("Source file does not exit.");
        }
        if (!source.isDirectory()) {
            throw new IllegalArgumentException("Source file is no folder.");
        }

        try (ZipOutputStream outputStream = new ZipOutputStream(new BufferedOutputStream(targetOutputStream))) {

            final File[] subFolders = source.listFiles();
            Validate.notNull(subFolders);
            for (final File file : subFolders) {

                if (file.isDirectory()) {
                    final String parent = file.getParent();
                    Validate.notNull(parent);
                    bytesTransferred += zipSubFolder(outputStream, file, parent.length());
                } else {
                    bytesTransferred += zipFile(file, outputStream,
                            file.getAbsolutePath().substring(file.getAbsolutePath().lastIndexOf("/")));
                }
            }
        }
        return bytesTransferred;
    }

    /**
     * Zips a subfolder recursively.
     *
     * @param outputStream The {@link ZipOutputStream} to write the zipped content to.
     * @param folder The {@link File} reference to the subfolder to be zipped.
     * @param basePathLength The length of the parent folder path.
     * @return the number of bytes read
     * @throws IOException when the subfolder failed to be read or written to the outputStream
     */
    private long zipSubFolder(final ZipOutputStream outputStream, final File folder, final int basePathLength)
            throws IOException {
        Log.d(TAG, "Zipping folder " + folder.getName());

        final File[] folderContent = folder.listFiles();
        Validate.notNull(folderContent);
        long bytesTransferred = 0L;
        for (final File file : folderContent) {
            if (file.isDirectory()) {
                bytesTransferred += zipSubFolder(outputStream, file, basePathLength);
                continue;
            }

            bytesTransferred += zipFile(file, outputStream, file.getPath().substring(basePathLength));
        }
        return bytesTransferred;
    }

    /**
     * Zips a single file.
     *
     * @param outputStream The {@link ZipOutputStream} to write the zipped content to.
     * @param file The {@link File} reference to the file to be zipped.
     * @param relativePath The relative path to the file.
     * @return the number of bytes read
     * @throws IOException when the subfolder failed to be read or written to the outputStream
     */
    private long zipFile(final File file, final ZipOutputStream outputStream, final String relativePath)
            throws IOException {
        Log.d(TAG, "Zipping file " + file.getName());

        BufferedInputStream inputStream = null;
        try {
            // The size of the buffer to zip the measurement folder.
            final int ZIP_BUFFER_SIZE = 2048;
            inputStream = new BufferedInputStream(new FileInputStream(file.getPath()), ZIP_BUFFER_SIZE);

            final ZipEntry entry = new ZipEntry(relativePath);
            entry.setTime(file.lastModified()); // keeps modification time after unzipping
            outputStream.putNextEntry(entry);

            final byte[] data = new byte[ZIP_BUFFER_SIZE];
            int bytesRead;
            long bytesTransferred = 0L;
            while ((bytesRead = inputStream.read(data, 0, ZIP_BUFFER_SIZE)) != -1) {
                outputStream.write(data, 0, bytesRead);
                bytesTransferred += bytesRead;
            }
            inputStream.close();

            return bytesTransferred;
        } catch (IOException e) {
            if (outputStream != null) {
                outputStream.close();
            }
            throw new IllegalStateException("Failed to compress file: " + file.getPath(), e);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    /**
     * Makes the exported size readable to humans.
     * source: https://stackoverflow.com/a/3758880/5815054
     *
     * @param bytes The number of bytes of the size to format as human readable String
     * @return a human readable size as String
     */
    private String humanReadableByteCount(long bytes) {
        final int unit = 1000;
        if (bytes < unit)
            return bytes + " B";
        final int exp = (int)(Math.log(bytes) / Math.log(unit));
        // noinspection SpellCheckingInspection
        final String pre = ("kMGTPE").charAt(exp - 1) + ("");
        return String.format(Locale.GERMAN, "%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }
}
