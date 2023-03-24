package de.cyface.app.r4r.ui.trips

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.Navigation
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.cyface.app.r4r.R
import de.cyface.app.r4r.ui.trips.TripListAdapter.TripViewHolder
import de.cyface.persistence.model.Measurement
import de.cyface.persistence.model.MeasurementStatus
import org.w3c.dom.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * [ListAdapter] which creates and binds a [TripViewHolder].
 */
class TripListAdapter : ListAdapter<Measurement, TripViewHolder>(TripsComparator()) {

    var tracker: SelectionTracker<Long>? = null

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        return TripViewHolder.create(parent)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        val current = getItem(position)
        holder.itemView.setOnClickListener {
            // not passing complex data, as recommended: https://developer.android.com/guide/navigation/navigation-pass-data
            val action = TripsFragmentDirections.actionTripsToDetails(current.id)
            Navigation.findNavController(it).navigate(action)
        }
        tracker?.let {
            holder.bind(current, it.isSelected(position.toLong()))
        }
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    public override fun getItem(position: Int): Measurement {
        return super.getItem(position)
    }

    fun selectAll() {
        for (i in 0 until itemCount) {
            tracker!!.select(i.toLong())
        }
    }

    /**
     * Binds data to the UI elements.
     */
    class TripViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        private val tripTitleView: TextView = itemView.findViewById(R.id.titleView)
        private val tripDetailsView: TextView = itemView.findViewById(R.id.detailsView)

        fun bind(measurement: Measurement, isActivated: Boolean = false) {
            itemView.isActivated = isActivated
            val date = Date(measurement.timestamp)
            val dateText = SimpleDateFormat("dd.MM.yy HH:mm", Locale.GERMANY).format(date)
            val distanceKm = (measurement.distance / 1000 * 1000).roundToInt() / 1000.0
            val status = measurement.status
            var statusText = ""
            if (status === MeasurementStatus.SYNCED || status === MeasurementStatus.SKIPPED || status === MeasurementStatus.DEPRECATED) {
                statusText += " - " + status.databaseIdentifier.lowercase()
            }
            tripTitleView.text = itemView.context.getString(R.string.trip_id,measurement.id)
            tripDetailsView.text = itemView.context.getString(
                R.string.trip_details_line,
                dateText,
                distanceKm,
                statusText
            )
            val textColor = if (isActivated) itemView.resources.getColor(R.color.white) else itemView.resources.getColor(R.color.text)
            tripTitleView.setTextColor(textColor)
            tripDetailsView.setTextColor(textColor)
            itemView.findViewById<ImageView>(R.id.list_details_arrow).setColorFilter(textColor)
        }

        fun getItemDetails(): ItemDetailsLookup.ItemDetails<Long> =
            object : ItemDetailsLookup.ItemDetails<Long>() {
                override fun getPosition(): Int = adapterPosition
                override fun getSelectionKey(): Long = itemId
            }

        companion object {
            /**
             * Inflate the layout.
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

    class ItemsDetailsLookup(private val recyclerView: RecyclerView) : ItemDetailsLookup<Long>() {
        override fun getItemDetails(event: MotionEvent): ItemDetails<Long>? {
            val view = recyclerView.findChildViewUnder(event.x, event.y)
            if (view != null) {
                return (recyclerView.getChildViewHolder(view) as TripViewHolder).getItemDetails()
            }
            return null
        }
    }
}