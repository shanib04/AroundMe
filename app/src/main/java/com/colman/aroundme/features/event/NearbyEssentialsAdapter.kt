package com.colman.aroundme.features.event

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.colman.aroundme.R
import com.colman.aroundme.data.model.NearbyPlace
import com.colman.aroundme.databinding.ItemNearbyEssentialBinding

class NearbyEssentialsAdapter(
    private val onClick: (NearbyPlace) -> Unit
) : ListAdapter<NearbyPlace, NearbyEssentialsAdapter.PlaceViewHolder>(Diff) {

    /** The currently selected type so we can show a consistent fallback icon. */
    var fallbackType: EventDetailsViewModel.EssentialsType = EventDetailsViewModel.EssentialsType.PARKING

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val binding = ItemNearbyEssentialBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PlaceViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        holder.bind(getItem(position), fallbackType)
    }

    class PlaceViewHolder(
        private val binding: ItemNearbyEssentialBinding,
        private val onClick: (NearbyPlace) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(place: NearbyPlace, fallbackType: EventDetailsViewModel.EssentialsType) {
            binding.placeName.text = place.name
            binding.placeSubtitle.text = place.vicinity

            val fallbackRes = when (fallbackType) {
                EventDetailsViewModel.EssentialsType.PARKING -> R.drawable.ic_parking
                EventDetailsViewModel.EssentialsType.FOOD -> R.drawable.ic_food
                EventDetailsViewModel.EssentialsType.GAS -> R.drawable.ic_gas
            }

            // Prefer photoUrl if we have it, otherwise show a crisp local icon.
            val photoUrl = place.photoUrl?.takeIf { it.isNotBlank() }

            Glide.with(binding.placeImage)
                .load(photoUrl ?: fallbackRes)
                .placeholder(fallbackRes)
                .error(fallbackRes)
                .centerCrop()
                .into(binding.placeImage)

            binding.root.setOnClickListener { onClick(place) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<NearbyPlace>() {
        override fun areItemsTheSame(oldItem: NearbyPlace, newItem: NearbyPlace): Boolean {
            return oldItem.placeId == newItem.placeId && oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: NearbyPlace, newItem: NearbyPlace): Boolean {
            return oldItem == newItem
        }
    }
}
