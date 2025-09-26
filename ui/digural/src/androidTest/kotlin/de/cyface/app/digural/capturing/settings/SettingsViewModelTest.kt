package de.cyface.app.digural.capturing.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.cyface.camera_service.settings.CameraSettings
import de.cyface.utils.settings.AppSettings
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class SettingsViewModelTest {

    private lateinit var context: Context
    private lateinit var tempFile: File

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext

        // Erstellen einer temporären Testdatei mit bekanntem Inhalt
        //tempFile = File(context.cacheDir, "test_file.txt")
        //tempFile.writeText("Dies ist der Inhalt der Testdatei für den Kopiervorgang.")

        // Using a real model file (12 MB) also does not reproduce [LEIP-386]
        /*val testContext = InstrumentationRegistry.getInstrumentation().context
        val inputStream = testContext.resources.openRawResource(
            testContext.resources.getIdentifier("fixme_do_not_upload", "raw", testContext.packageName)
        )

        tempFile = File(context.cacheDir, "fixme_do_not_upload.tflite")
        inputStream.use { inStream ->
            tempFile.outputStream().use { outStream ->
                inStream.copyTo(outStream)
            }
        }*/
    }

    @After
    fun tearDown() {
        tempFile.delete()
        File(context.getExternalFilesDir(null), "anon_model").delete()
    }

    @Test
    fun testSaveToFile() = runTest {
        // Arrange
        val appSettings = AppSettings.getInstance(context)
        val cameraSettings = CameraSettings.getInstance(context)
        val customSettings = CustomSettings.getInstance(context)
        val intent = Intent().apply { data = Uri.fromFile(tempFile) }
        val activityResult = ActivityResult(Activity.RESULT_OK, intent)
        val viewModel = SettingsViewModel(appSettings, cameraSettings, customSettings)

        // Act
        viewModel.modelFilePicked(activityResult, context)

        // Assert
        val copiedFile = File(context.getExternalFilesDir(null), "anon_model")
        assertTrue(copiedFile.exists())

        // Compare Hashes of copied files content instead of byte size [LEIP-386].
        // Unfortunately, this test did not reproduce [LEIP-386], even with a realistic model.
        assertEquals("Byte size mismatch!", tempFile.length(), copiedFile.length())
        assertEquals("MD5 mismatch!", tempFile.md5(), copiedFile.md5())
        assertEquals("SHA-256 mismatch!", tempFile.sha256(), copiedFile.sha256())
    }

    private fun File.md5(): String = this.hash("MD5")
    private fun File.sha256(): String = this.hash("SHA-256")

    private fun File.hash(algorithm: String): String {
        val buffer = ByteArray(8192)
        val digest = MessageDigest.getInstance(algorithm)
        FileInputStream(this).use { fis ->
            var read = fis.read(buffer)
            while (read > 0) {
                digest.update(buffer, 0, read)
                read = fis.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}