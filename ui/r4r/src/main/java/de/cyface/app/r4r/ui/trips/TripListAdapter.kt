package de.cyface.app.r4r.ui.trips

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.cyface.app.r4r.R
import de.cyface.app.r4r.ui.trips.TripListAdapter.TripViewHolder
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * [ListAdapter] which creates and binds a [TripViewHolder].
 */
class TripListAdapter :
    ListAdapter<Measurement, TripViewHolder>(TripsComparator()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        return TripViewHolder.create(parent)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        val current = getItem(position)
        holder.bind(current)
    }

    /**
     * Binds data to the UI elements.
     */
    class TripViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tripTitleView: TextView = itemView.findViewById(R.id.titleView)
        private val tripDetailsView: TextView = itemView.findViewById(R.id.detailsView)

        fun bind(measurement: Measurement) {
            //tripTitleView.text = itemView.context.getString(R.string.trip_title, measurement.id)
            val date = Date(measurement.timestamp)
            val dateText = SimpleDateFormat("dd.MM.yy HH:mm", Locale.GERMANY).format(date)
            val distanceKm = (measurement.distance / 1000 * 1000).roundToInt() / 1000.0
            val status = measurement.status
            var statusText = ""
            if (status === MeasurementStatus.SYNCED || status === MeasurementStatus.SKIPPED || status === MeasurementStatus.DEPRECATED) {
                statusText += " - " + status.databaseIdentifier.lowercase()
            }
            tripTitleView.text = itemView.context.getString(R.string.trip_details, measurement.id, dateText, distanceKm, statusText)
        }

        companion object {
            /**
             * Inflates the layout.
             */
            fun create(parent: ViewGroup): TripViewHolder {
                val view: View = LayoutInflater.from(parent.context)
                    .inflate(R.layout.trips_item, parent, false)
                return TripViewHolder(view)
            }
        }
    }

    /**
     * Defines how to check if two entries or their content are the same.
     */
    class TripsComparator : DiffUtil.ItemCallback<Measurement>() {
        override fun areItemsTheSame(oldItem: Measurement, newItem: Measurement): Boolean {
            return oldItem === newItem
        }

        override fun areContentsTheSame(oldItem: Measurement, newItem: Measurement): Boolean {
            return oldItem.id == newItem.id
        }
    }
}