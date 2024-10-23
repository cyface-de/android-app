/*
 * Copyright 2024 Cyface GmbH
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
package de.cyface.app.digural.upload

import android.util.Log
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import de.cyface.app.digural.MainActivity.Companion.TAG
import de.cyface.model.Json
import de.cyface.model.Json.JsonObject
import de.cyface.synchronization.NoLocationData
import de.cyface.uploader.Result
import de.cyface.uploader.UploadProgressListener
import de.cyface.uploader.Uploader
import de.cyface.uploader.exception.AccountNotActivated
import de.cyface.uploader.exception.BadRequestException
import de.cyface.uploader.exception.ConflictException
import de.cyface.uploader.exception.EntityNotParsableException
import de.cyface.uploader.exception.ForbiddenException
import de.cyface.uploader.exception.InternalServerErrorException
import de.cyface.uploader.exception.MeasurementTooLarge
import de.cyface.uploader.exception.NetworkUnavailableException
import de.cyface.uploader.exception.ServerUnavailableException
import de.cyface.uploader.exception.SynchronisationException
import de.cyface.uploader.exception.SynchronizationInterruptedException
import de.cyface.uploader.exception.TooManyRequestsException
import de.cyface.uploader.exception.UnauthorizedException
import de.cyface.uploader.exception.UnexpectedResponseCode
import de.cyface.uploader.exception.UploadFailed
import de.cyface.uploader.exception.UploadSessionExpired
import de.cyface.uploader.model.Attachment
import de.cyface.uploader.model.Measurement
import de.cyface.uploader.model.Uploadable
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLException

/**
 * Implementation of the [Uploader].
 *
 * To use this interface just call [WebdavUploader.uploadMeasurement] or [WebdavUploader.uploadAttachment].
 *
 * @author Armin Schnabel
 * @property apiEndpoint An API endpoint running a Webdav data collector service, like `https://some.url/api/v3`
 */
class WebdavUploader(
    private val apiEndpoint: String,
    private val deviceId: String,
    private val login: String,
    password: String
) : Uploader {

    private val sardine = OkHttpSardine()

    init {
        require(apiEndpoint.isNotEmpty())
        require(deviceId.isNotEmpty())
        require(login.isNotEmpty())
        require(password.isNotEmpty())
        sardine.setCredentials(login, password)
    }

    @Suppress("unused", "CyclomaticComplexMethod", "LongMethod") // Part of the API
    override fun uploadMeasurement(
        jwtToken: String,
        uploadable: Measurement,
        file: File,
        progressListener: UploadProgressListener
    ): Result {
        if (uploadable.timestamp() === null) {
            Log.i(TAG, "Skipping measurement upload without location: ${uploadable.identifier}")
            return Result.UPLOAD_SKIPPED
        }
        val endpoint = URL(imuDirectory(uploadable))
        val result = uploadFile(uploadable, file, MEASUREMENT_FILE_FILENAME, endpoint)
        if (result != Result.UPLOAD_FAILED) {
            progressListener.updatedProgress(1.0f) // the whole file is uploaded
        }
        return result
    }

    override fun uploadAttachment(
        jwtToken: String,
        uploadable: Attachment,
        file: File,
        fileName: String,
        progressListener: UploadProgressListener
    ): Result {
        if (uploadable.timestamp() === null) {
            Log.i(TAG, "Skipping attachment upload without location: ${uploadable.identifier}")
            return Result.UPLOAD_SKIPPED
        }
        val endpoint = attachmentsEndpoint(uploadable)
        val result = uploadFile(uploadable, file, fileName, endpoint)
        if (result != Result.UPLOAD_FAILED) {
            progressListener.updatedProgress(1.0f) // the whole file is uploaded
        }
        return result
    }

    override fun measurementsEndpoint(uploadable: Uploadable): URL {
        try {
            return URL(imuDirectory(uploadable))
        } catch (e: Exception) {
            throw UploadFailed(e)
        }
    }

    override fun attachmentsEndpoint(uploadable: Uploadable): URL {
        try {
            @Suppress("SpellCheckingInspection")
            // For Webdav we use another directory structure than in the Collector API, here we use:
            // /files/<login>/digural-upload/<YYYY>_<MM>_<DD>/<deviceId>_<measurementId>/imgs
            // But the API uses /measurements/<deviceId>/<measurementId>/attachments
            return URL(imagesDirectory(uploadable))
        } catch (e: Exception) {
            throw UploadFailed(e)
        }
    }

    private fun imuDirectory(uploadable: Uploadable): String {
        return sensorDirectory(uploadable) + "/imu"
    }

    private fun sensorDirectory(uploadable: Uploadable): String {
        return deviceMeasurementDirectory(uploadable) + "/sensor"
    }

    private fun imagesDirectory(uploadable: Uploadable): String {
        @Suppress("SpellCheckingInspection")
        return deviceMeasurementDirectory(uploadable) + "/imgs"
    }

    private fun deviceMeasurementDirectory(uploadable: Uploadable): String {
        return dateDirectory(uploadable) + "/${deviceId}_${uploadable.measurementId()}"
    }

    private fun dateDirectory(uploadable: Uploadable): String {
        val timestamp = uploadable.timestamp()
        if (timestamp === null) {
            Log.i(TAG, "Skip attachment upload w/o location for measurement: $uploadable")
            throw NoLocationData("Skip upload as upload dir required date from missing location data")
        }
        val date = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofPattern("yyyy_MM_dd"))

        // The same user is used on all devices, so no user id is stored in the path.
        return returnUrlWithTrailingSlash(apiEndpoint) + "files/${login}/digural-upload/$date"
    }

    @Throws(UploadFailed::class)
    private fun uploadFile(
        uploadable: Uploadable,
        file: File,
        fileName: String,
        endpoint: URL,
    ): Result {
        return try {
            // TODO: the sardine library has PRs which support input streams but they are not merged
            // also, the connections are not closed, same with the PRs (not merged for 5+ years)
            //FileInputStream(file).use { fis ->
            //val bufferedInputStream = BufferedInputStream(fis)

            // We currently cannot merge multiple upload-chunk requests into one file on server side.
            // Thus, we prevent slicing the file into multiple files by increasing the chunk size.
            // If the file is larger sync would be successful but only the 1st chunk received DAT-730.
            // i.e. we throw an exception (which skips the upload) for too large measurements (44h+).
            if (file.length() > MAX_CHUNK_SIZE) {
                throw MeasurementTooLarge("Transfer file is too large: ${file.length()}")
            }

            // ** attention ** It's normal to see the log info:
            // `Authenticating for response: Response{protocol=http/1.1, code=401, message=Unauthorized,`

            // Skip uploads without startLocation as this is needed for the upload folder name


            val isMeasurementUpload = fileName == MEASUREMENT_FILE_FILENAME
            ensureDirectoriesExist(isMeasurementUpload, uploadable)

            // File uploads
            val uploadDir = endpoint.toExternalForm()
            if (isMeasurementUpload) {
                // Meta file
                val metaDataUri = "$uploadDir/metadata.json"
                if (!sardine.exists(metaDataUri)) {
                    Log.d(TAG, "Upload meta data to $metaDataUri ...")
                    val metaDataMap = uploadable.toMap()
                    val metaDataJson = JsonObject.Builder()
                    metaDataMap.keys.forEach {
                        metaDataJson.add(Json.jsonKeyValue(it, metaDataMap.getValue(it)))
                    }
                    sardine.put(metaDataUri, metaDataJson.build().stringValue.toByteArray())
                }

                // Measurement file
                val measurementUri = "$uploadDir/$fileName"
                if (!sardine.exists(measurementUri)) {
                    Log.d(TAG, "Upload measurement: $fileName ...")
                    sardine.put(measurementUri, file, "application/octet-stream")
                }
            } else {
                // Attachment file
                //
                // Regarding `data.json` file: [LEIP-283]
                // All required information is already contained in other uploaded log files.
                // We anyhow do not have all information available which is required in the
                // `data.json`, e.g. `yaw/pitch/roll` or geometry of the image (this info is not
                // easily available during upload as the upload happens in multiple parts and the
                // geometry of the image are only in the image EXIF header, but during measurement
                // upload we only have the geometry of the track available. Thus, this would require
                // significant refactoring.
                // Thus, the whole `data.json` file should be created during post-processing.
                //
                // Regarding `${recId}_{l,m,r,p}.txt` files: [LEIP-282]
                // We currently create an `annotations.json` file in the COCO format which contains
                // all annotations of all images instead of one YOLO `txt` file per image.
                // - advantage: significant less upload request to next cloud (~50%)
                // - we can create the `txt` files per image in post-processing like other meta files
                // - all required information is available in the COCO `json file:
                //   rectId.txt = attachment-id → images.id
                //   recognized object → COCO-class-Id → convert to Digural class ID
                //   recognized bounding box → not normalized, but image width/height is available
                //   confidence-score → in COCO-JSON per object for each image
                //   image name → [timestamp].jpg (not required in the txt format as of now)
                // - as the `txt` files are created in post-processing, all log files can also be
                //   uploaded into `img` or another directory.
                val attachmentUri = "$uploadDir/$fileName"
                if (!sardine.exists(attachmentUri)) {
                    Log.d(TAG, "Upload attachment: $fileName ...")
                    sardine.put(attachmentUri, file, "application/octet-stream")
                }
            }
            de.cyface.uploader.Result.UPLOAD_SUCCESSFUL
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            handleUploadException(e)
        }
    }

    /**
     * Function to check and create directories for upload if they don't exist.
     *
     * It's less important how many requests are made for the measurement upload, as this only
     * happens once every hour or so, but for attachments we should try to minimize the number of
     * requests.
     */
    private fun ensureDirectoriesExist(isMeasurementUpload: Boolean, uploadable: Uploadable) {
        Log.e(TAG, "Checking for directories ...")
        val isAttachmentUpload = !isMeasurementUpload

        val imagesDir = imagesDirectory(uploadable)
        val imuDir = imuDirectory(uploadable)
        if (isAttachmentUpload && !sardine.exists(imagesDir)) {
            // Attachment Upload
            ensureDeviceMeasurementDirectoryExists(uploadable)
            Log.d(TAG, "Creating directory: $imagesDir")
            sardine.createDirectory(imagesDir)
        } else if (isMeasurementUpload && !sardine.exists(imuDir)) {
            // Measurement Upload
            ensureDeviceMeasurementDirectoryExists(uploadable)
            ensureDirectoryExists(sardine, sensorDirectory(uploadable))
            Log.d(TAG, "Creating directory: $imuDir")
            sardine.createDirectory(imuDir)
        }
    }

    /**
     * Function to check and create measurement directory if it doesn't exist.
     *
     * This directory contains the measurement files and the attachments folder.
     */
    private fun ensureDeviceMeasurementDirectoryExists(uploadable: Uploadable) {
        if (!sardine.exists(deviceMeasurementDirectory(uploadable))) {
            ensureDirectoryExists(sardine, dateDirectory(uploadable))
            ensureDirectoryExists(sardine, deviceMeasurementDirectory(uploadable))
        }
    }

    /**
     * Function to check and create directory if it doesn't exist
     */
    private fun ensureDirectoryExists(sardine: Sardine, directory: String) {
        if (!sardine.exists(directory)) {
            Log.d(TAG, "Creating directory: $directory")
            sardine.createDirectory(directory)
        }
    }


    /**
     * Handles exceptions thrown during upload.
     *
     * We wrap errors with [UploadFailed] so that the caller can handle this without crashing.
     * This way the SDK's `SyncPerformer` can determine if the sync should be repeated.
     */
    @Suppress("ComplexMethod")
    private fun handleUploadException(exception: Exception): Nothing {
        // TODO: This is the handler code from the Cyface Collector
        // We leave this for now until we see actual exceptions being thrown by webdav/sardine

        fun handleIOException(e: IOException): Nothing {
            Log.w(TAG, "Caught IOException: ${e.message}")
            // Unstable Wi-Fi connection [DAT-742]. transmission stream ended too early, likely because the sync
            // thread was interrupted (sync canceled). Try again later.
            if (e.message?.contains("unexpected end of stream") == true) {
                throw SynchronizationInterruptedException("Upload interrupted", e)
            }
            // IOException while reading the response. Try again later.
            throw UploadFailed(SynchronisationException(e))
        }

        fun handleSSLException(e: SSLException): Nothing {
            Log.w(TAG, "Caught SSLException: ${e.message}")
            // Thrown by OkHttp when the network is no longer available [DAT-740]. Try again later.
            if (e.message?.contains("I/O error during system call, Broken pipe") == true) {
                throw UploadFailed(NetworkUnavailableException("Network became unavailable during upload."))
            }
            throw UploadFailed(SynchronisationException(e))
        }

        when (exception) {
            // Crash unexpected errors hard
            is MalformedURLException -> error(exception)

            // Soft caught errors

            // Happened on emulator when endpoint is local network instead of 10.0.2.2 [DAT-727]
            // Server not reachable. Try again later.
            is SocketTimeoutException -> throw UploadFailed(ServerUnavailableException(exception))
            is SSLException -> handleSSLException(exception)
            is InterruptedIOException -> {
                Log.w(TAG, "Caught InterruptedIOException: ${exception.message}")
                // Request interrupted [DAT-741]. Try again later.
                if (exception.message?.contains("thread interrupted") == true) {
                    throw UploadFailed(
                        NetworkUnavailableException(
                            "Network interrupted during upload",
                            exception
                        )
                    )
                }
                // InterruptedIOException while reading the response. Try again later.
                throw UploadFailed(SynchronisationException(exception))
            }

            is IOException -> handleIOException(exception)
            // File is too large to be uploaded. Handle in caller (e.g. skip the upload).
            // The max size is currently static and set to 100 MB which should be about 44 hours of 100 Hz measurement.
            is MeasurementTooLarge -> throw UploadFailed(exception)
            // `HTTP_BAD_REQUEST` (400).
            is BadRequestException -> throw UploadFailed(exception)
            // `HTTP_UNAUTHORIZED` (401).
            is UnauthorizedException -> throw UploadFailed(exception)
            // `HTTP_FORBIDDEN` (403). Seems to happen when server is unavailable. Handle in caller.
            is ForbiddenException -> throw UploadFailed(exception)
            // `HTTP_CONFLICT` (409). Already uploaded. Handle in caller (e.g. mark as synced).
            is ConflictException -> throw UploadFailed(exception)
            // `HTTP_ENTITY_NOT_PROCESSABLE` (422).
            is EntityNotParsableException -> throw UploadFailed(exception)
            // `HTTP_INTERNAL_ERROR` (500).
            is InternalServerErrorException -> throw UploadFailed(exception)
            // `HTTP_TOO_MANY_REQUESTS` (429). Try again later.
            is TooManyRequestsException -> throw UploadFailed(exception)
            // IOException while reading the response. Try again later.
            is SynchronisationException -> throw UploadFailed(exception)
            // `HTTP_NOT_FOUND` (404). Try again.
            is UploadSessionExpired -> throw UploadFailed(exception)
            // Unexpected response code. Should be reported to the server admin.
            is UnexpectedResponseCode -> throw UploadFailed(exception)
            // `PRECONDITION_REQUIRED` (428). Shouldn't happen during upload, report to server admin.
            is AccountNotActivated -> throw UploadFailed(exception)
            // This is not yet thrown as a specific exception.
            // Network without internet connection. Try again later.
            // is HostUnresolvable -> throw LoginFailed(e)

            else -> throw UploadFailed(SynchronisationException(exception))
        }
    }

    companion object {
        @Suppress("SpellCheckingInspection")
        private const val MEASUREMENT_FILE_FILENAME = "measurement.ccyf"
        private const val MB_FROM_MEDIA_HTTP_UPLOADER = 0x100000

        /**
         * With a sensor frequency of 100 Hz this supports Measurements up to ~ 44 hours.
         */
        private const val MAX_CHUNK_SIZE = 100 * MB_FROM_MEDIA_HTTP_UPLOADER

        /**
         * Adds a trailing slash to the server URL or leaves an existing trailing slash untouched.
         *
         * @param url The url to format.
         * @return The server URL with a trailing slash.
         */
        fun returnUrlWithTrailingSlash(url: String): String {
            return if (url.endsWith("/")) {
                url
            } else {
                "$url/"
            }
        }
    }
}
