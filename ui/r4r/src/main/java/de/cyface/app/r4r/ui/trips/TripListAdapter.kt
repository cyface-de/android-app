package de.cyface.app.r4r.ui.trips

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.cyface.app.r4r.R
import de.cyface.persistence.model.Measurement
import kotlin.math.roundToInt

/**
 * [ListAdapter] which creates and binds a [TripViewHolder].
 */
class TripListAdapter :
    ListAdapter<Measurement, TripListAdapter.TripViewHolder>(TripsComparator()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        return TripViewHolder.create(parent)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        val current = getItem(position)
        holder.bind("Measurement ${current.id} (${(current.distance * 100).roundToInt() / 100.0} km)")
    }

    /**
     * Binds data to the UI elements.
     */
    class TripViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tripItemView: TextView = itemView.findViewById(R.id.textView)

        fun bind(text: String?) {
            tripItemView.text = text
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