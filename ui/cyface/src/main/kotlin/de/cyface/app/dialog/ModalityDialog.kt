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
package de.cyface.app.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import de.cyface.app.CapturingFragment
import de.cyface.app.R
import de.cyface.persistence.model.Modality
import de.cyface.synchronization.BundlesExtrasCodes
import de.cyface.utils.settings.AppSettings
import de.cyface.utils.Validate
import kotlinx.coroutines.runBlocking

/**
 * After the user installed the app for the first time, after accepting the terms, this dialog is shown
 * to force the user to select his [Modality] type before being able to use the app. This is necessary
 * as it is not possible to have NO tab (here: `Modality` type) selected visually so we make sure the correct
 * one is selected by default. Else, the user might forget (oversee) to select a `Modality` at all.
 *
 * Make sure the order (0, 1, 2 from left(start) to right(end)) in the TabLayout is consistent in here.
 *
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 1.0.0
 */
class ModalityDialog(private val appSettings: AppSettings) : DialogFragment() {
    /**
     * The id if the `Measurement` if this dialog is called for a specific Measurement.
     */
    private var measurementId: Long? = null
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        builder.setTitle(R.string.dialog_modality_title).setItems(
            R.array.dialog_modality
        ) { _: DialogInterface?, which: Int ->
            val fragmentActivity = activity
            Validate.notNull(fragmentActivity)
            val modality: Modality = when (which) {
                0 -> Modality.valueOf(Modality.CAR.name)
                1 -> Modality.valueOf(Modality.BICYCLE.name)
                2 -> Modality.valueOf(Modality.WALKING.name)
                3 -> Modality.valueOf(Modality.BUS.name)
                4 -> Modality.valueOf(Modality.TRAIN.name)
                else -> throw IllegalArgumentException("Unknown modality selected: $which")
            }
            runBlocking { appSettings.setModality(modality.databaseIdentifier) }
            val requestCode = targetRequestCode
            val resultCode: Int
            val intent = Intent()
            when (requestCode) {
                CapturingFragment.DIALOG_INITIAL_MODALITY_SELECTION_REQUEST_CODE -> resultCode =
                    DIALOG_INITIAL_MODALITY_SELECTION_RESULT_CODE

                DIALOG_ADD_EVENT_MODALITY_SELECTION_REQUEST_CODE -> {
                    resultCode = DIALOG_ADD_EVENT_MODALITY_SELECTION_RESULT_CODE
                    intent.putExtra(DIALOG_MODALITY_KEY, modality.databaseIdentifier) // FIXME: check if this extra is read again
                    Validate.notNull(measurementId)
                    intent.putExtra(BundlesExtrasCodes.MEASUREMENT_ID, measurementId)
                }

                else -> throw IllegalArgumentException("Unknown request code: $requestCode")
            }
            val targetFragment = targetFragment
            Validate.notNull(targetFragment)
            targetFragment!!.onActivityResult(requestCode, resultCode, intent)
        }
        return builder.create()
    }

    fun setMeasurementId(measurementId: Long) {
        this.measurementId = measurementId
    }

    companion object {
        private const val DIALOG_INITIAL_MODALITY_SELECTION_RESULT_CODE = 201909192

        /**
         * The identifier for the [ModalityDialog] request which asks the user to select a [Modality] when he
         * adds a new `EventType.MODALITY_TYPE_CHANGE` via UI.
         */
        private const val DIALOG_ADD_EVENT_MODALITY_SELECTION_REQUEST_CODE = 201909193
        private const val DIALOG_ADD_EVENT_MODALITY_SELECTION_RESULT_CODE = 201909194
        private const val DIALOG_MODALITY_KEY = "de.cyface.app.modality"
    }
}