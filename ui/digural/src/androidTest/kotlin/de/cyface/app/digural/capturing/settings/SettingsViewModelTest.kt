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

@RunWith(AndroidJUnit4::class)
class SettingsViewModelTest {

    private lateinit var context: Context
    private lateinit var tempFile: File

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext

        // FIXME: This test may not be reproducing the issue as the test file is unrealistic small.

        // Erstellen einer temporären Testdatei mit bekanntem Inhalt
        tempFile = File(context.cacheDir, "test_file.txt")
        tempFile.writeText("Dies ist der Inhalt der Testdatei für den Kopiervorgang.")

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
        // Aufräumen: Löschen der temporären Dateien
        tempFile.delete()
        File(context.getExternalFilesDir(null), "anon_model").delete()
    }

    @Test
    fun testSaveToFile() = runTest {
        val appSettings = AppSettings.getInstance(context)
        val cameraSettings = CameraSettings.getInstance(context)
        val customSettings = CustomSettings.getInstance(context)
        // GIVEN: Ein Intent mit einem URI, der auf unsere Testdatei verweist
        val intent = Intent().apply {
            data = Uri.fromFile(tempFile)
        }
        val activityResult = ActivityResult(Activity.RESULT_OK, intent)

        val viewModel = SettingsViewModel(
            appSettings,
            cameraSettings,
            customSettings,
            )

        viewModel.modelFilePicked(activityResult, context)

        // THEN: Prüfen, ob die Zieldatei existiert
        val copiedFile = File(context.getExternalFilesDir(null), "anon_model")
        assertTrue(copiedFile.exists())

        // THEN: Prüfen, ob der Inhalt der kopierten Datei korrekt ist
        val copiedContent = copiedFile.readText()
        assertEquals("Dies ist der Inhalt der Testdatei für den Kopiervorgang.", copiedContent)

        // THEN: Byte-Größe vergleichen statt Inhalt
        //val originalSize = tempFile.length()
        //val copiedSize = copiedFile.length()
        //Log.d("de.cyface", "$originalSize vs $copiedSize")
        //assertEquals("Byte size mismatch!", originalSize, copiedSize)
    }
}