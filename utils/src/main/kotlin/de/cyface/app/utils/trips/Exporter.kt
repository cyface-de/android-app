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
package de.cyface.app.utils.trips

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import de.cyface.app.utils.R
import de.cyface.app.utils.SharedConstants
import de.cyface.app.utils.SharedConstants.DATABASE_NAME
import de.cyface.persistence.io.DefaultFileIOHandler
import de.cyface.persistence.serialization.Point3DFile
import de.cyface.utils.Utils
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.ln
import kotlin.math.pow

/**
 * Async task which exports the measurement data without the image data.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.0.1
 * @since 1.0.0
 */
class Exporter(context: Context) {
    private val contextReference: WeakReference<Context> = WeakReference(context)
    private var targetPathTimestamp: String = "_" + SimpleDateFormat("yyyy-MM-dd_H-m", Locale.GERMANY).format(Date())

    /**
     * Copies the persistence layer files to a compressed archive into `Downloads` folder.
     *
     * *Attention:*  This long running, blocking code, execute this asynchronously!
     */
    fun export() {
        // To be able to show a UI dialog/message
        if (Looper.myLooper() == null) {
            Looper.prepare()
        }
        val context = contextReference.get()!!

        runOnUiThread {
            Toast.makeText(context, context.getString(R.string.export_data), Toast.LENGTH_SHORT)
                .show()
        }

        val fileAccess = DefaultFileIOHandler()
        val accelerations =
            fileAccess.getFolderPath(context, Point3DFile.ACCELERATIONS_FOLDER_NAME)
        val rotations = fileAccess.getFolderPath(context, Point3DFile.ROTATIONS_FOLDER_NAME)
        val directions = fileAccess.getFolderPath(context, Point3DFile.DIRECTIONS_FOLDER_NAME)
        var bytesTransferred = 0L
        val exportIdentifier = UUID.randomUUID()
        try {
            bytesTransferred += exportFolder(context, accelerations, exportIdentifier)
            bytesTransferred += exportFolder(context, rotations, exportIdentifier)
            bytesTransferred += exportFolder(context, directions, exportIdentifier)

            // Export all database files - including "wal" which contains recent unmerged database changes
            val parent = context.getDatabasePath(DATABASE_NAME).parentFile!!
            bytesTransferred += exportFolder(
                context, parent,
                exportIdentifier
            )
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }

        // Only show success note if app is still active
        try {
            runOnUiThread {
                val text = context.getString(R.string.toast_export) + " " + humanReadableByteCount(
                    bytesTransferred
                )
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            }
        } catch (e: NullPointerException) {
            Log.d(SharedConstants.TAG, "Dialog (export successful) not shown: Activity not active.", e)
            // We don't need to report this to Sentry as it's ok if this happens (app is closed)
        }
    }

    /**
     * This method helps to access the UI thread from a handler thread.
     */
    private fun runOnUiThread(runnable: Runnable) {
        Handler(Looper.getMainLooper()).post(runnable)
    }

    /**
     * Zips and exports a folder to the `Environment#DIRECTORY_DOWNLOADS`.
     *
     * @param context the `Context` to get the paths
     * @param sourceFolder the folder to export
     * @param exportIdentifier A globally unique identifier used to group files created by one export call and to
     * distinguish those files from different export calls made in the same minute potentially on a different
     * device.
     * @return the number of bytes read
     */
    @Throws(IOException::class)
    private fun exportFolder(
        context: Context, sourceFolder: File,
        exportIdentifier: UUID
    ): Long {
        val files = sourceFolder.listFiles()
        requireNotNull(files)
        if (!sourceFolder.exists() || files.isEmpty()) {
            return 0L
        }

        // Export zipped folder
        var targetOutputStream: OutputStream? = null
        return try {
            // TODO This should not use a random UUID but would be better to use the device id. Unfortunately we require
            // a different architecture to get access to that identifier.
            val fileName =
                ("cyface-" + sourceFolder.name + targetPathTimestamp + "_" + exportIdentifier
                        + ".zip")
            val bytesTransferred: Long
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contextReference.get()!!.contentResolver
                val contentValues = ContentValues()
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                contentValues.put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.Downloads.CONTENT_TYPE
                )
                contentValues.put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS
                )
                val outputUri =
                    resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                requireNotNull(outputUri)
                targetOutputStream = resolver.openOutputStream(outputUri)
                bytesTransferred = zipFolder(sourceFolder, targetOutputStream)
            } else {
                // Create target directory
                val downloadDirectory = Environment
                    .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadDirectory.exists()) {
                    check(downloadDirectory.mkdirs()) { "Failed to create target directory" }
                }
                check(downloadDirectory.canWrite()) { "Download target not writable" }
                val target = File(downloadDirectory, fileName)
                targetOutputStream = FileOutputStream(target)
                bytesTransferred = zipFolder(sourceFolder, targetOutputStream)
                Utils.informMediaScanner(context, target)
            }
            bytesTransferred
        } catch (e: IOException) {
            throw IllegalStateException(e)
        } finally {
            targetOutputStream?.close()
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
    @Throws(IOException::class)
    private fun zipFolder(source: File, targetOutputStream: OutputStream?): Long {
        var bytesTransferred = 0L
        require(source.exists()) { "Source file does not exit." }
        require(source.isDirectory) { "Source file is no folder." }
        ZipOutputStream(BufferedOutputStream(targetOutputStream)).use { outputStream ->
            val subFolders = source.listFiles()
            requireNotNull(subFolders)
            for (file in subFolders) {
                bytesTransferred += if (file.isDirectory) {
                    val parent = file.parent
                    requireNotNull(parent)
                    zipSubFolder(outputStream, file, parent.length)
                } else {
                    zipFile(
                        file, outputStream,
                        file.absolutePath.substring(file.absolutePath.lastIndexOf("/"))
                    )
                }
            }
        }
        return bytesTransferred
    }

    /**
     * Zips a subfolder recursively.
     *
     * @param outputStream The [ZipOutputStream] to write the zipped content to.
     * @param folder The [File] reference to the subfolder to be zipped.
     * @param basePathLength The length of the parent folder path.
     * @return the number of bytes read
     * @throws IOException when the subfolder failed to be read or written to the outputStream
     */
    @Throws(IOException::class)
    private fun zipSubFolder(
        outputStream: ZipOutputStream,
        folder: File,
        basePathLength: Int
    ): Long {
        Log.d(SharedConstants.TAG, "Zipping folder " + folder.name)
        val folderContent = folder.listFiles()
        requireNotNull(folderContent)
        var bytesTransferred = 0L
        for (file in folderContent) {
            if (file.isDirectory) {
                bytesTransferred += zipSubFolder(outputStream, file, basePathLength)
                continue
            }
            bytesTransferred += zipFile(file, outputStream, file.path.substring(basePathLength))
        }
        return bytesTransferred
    }

    /**
     * Zips a single file.
     *
     * @param outputStream The [ZipOutputStream] to write the zipped content to.
     * @param file The [File] reference to the file to be zipped.
     * @param relativePath The relative path to the file.
     * @return the number of bytes read
     * @throws IOException when the subfolder failed to be read or written to the outputStream
     */
    @Throws(IOException::class)
    private fun zipFile(file: File, outputStream: ZipOutputStream?, relativePath: String): Long {
        Log.d(SharedConstants.TAG, "Zipping file " + file.name)
        var inputStream: BufferedInputStream? = null
        return try {
            // The size of the buffer to zip the measurement folder.
            val zipBufferSize = 2048
            inputStream = BufferedInputStream(FileInputStream(file.path), zipBufferSize)
            val entry = ZipEntry(relativePath)
            entry.time = file.lastModified() // keeps modification time after unzipping
            outputStream!!.putNextEntry(entry)
            val data = ByteArray(zipBufferSize)
            var bytesRead: Int
            var bytesTransferred = 0L
            while (inputStream.read(data, 0, zipBufferSize).also { bytesRead = it } != -1) {
                outputStream.write(data, 0, bytesRead)
                bytesTransferred += bytesRead.toLong()
            }
            inputStream.close()
            bytesTransferred
        } catch (e: IOException) {
            outputStream?.close()
            throw IllegalStateException("Failed to compress file: " + file.path, e)
        } finally {
            inputStream?.close()
        }
    }

    /**
     * Makes the exported size readable to humans.
     * source: https://stackoverflow.com/a/3758880/5815054
     *
     * @param bytes The number of bytes of the size to format as human readable String
     * @return a human readable size as String
     */
    private fun humanReadableByteCount(bytes: Long): String {
        val unit = 1000
        if (bytes < unit) return "$bytes B"
        val exp = (ln(bytes.toDouble()) / ln(unit.toDouble())).toInt()

        @Suppress("SpellCheckingInspection")
        val pre = "kMGTPE"[exp - 1].toString() + ""
        return String.format(
            Locale.GERMAN,
            "%.1f %sB",
            bytes / unit.toDouble().pow(exp.toDouble()),
            pre
        )
    }
}
