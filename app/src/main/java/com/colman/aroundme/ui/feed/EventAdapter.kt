package com.colman.aroundme.ui.feed

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.colman.aroundme.databinding.ItemEventBinding
import com.colman.aroundme.model.Event

class EventAdapter(
    private val onItemClick: ((Event) -> Unit)? = null
) : ListAdapter<Event, EventAdapter.VH>(DiffCallback) {

    class VH(val binding: ItemEventBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val event = getItem(position)
        val b = holder.binding

        b.eventTitle.text = event.title
        b.eventDescription.text = event.description
        b.eventLocation.text = event.locationName
        b.eventTimeRemaining.text = event.timeRemaining

        // Chips
        b.chipGroup.removeAllViews()
        val ctx = b.chipGroup.context
        event.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { tag ->
            val chip = com.google.android.material.chip.Chip(ctx)
            chip.text = tag
            chip.isClickable = false
            chip.isCheckable = false
            b.chipGroup.addView(chip)
        }

        // Try to load image via Picasso reflectively if available; fall back to placeholder
        val defaultPlaceholder = android.R.drawable.ic_menu_gallery
        val imageView = b.eventImage
        val imgUrl = event.imageUrl
        var loaded = false
        try {
            val picassoClass = Class.forName("com.squareup.picasso.Picasso")
            val getMethod = picassoClass.getMethod("get")
            val picassoInstance = getMethod.invoke(null)
            val loadMethod = picassoInstance.javaClass.getMethod("load", String::class.java)
            val requestCreator = loadMethod.invoke(picassoInstance, imgUrl ?: "")
            val placeholderMethod = requestCreator.javaClass.getMethod("placeholder", Int::class.javaPrimitiveType)
            placeholderMethod.invoke(requestCreator, defaultPlaceholder)
            val intoMethod = requestCreator.javaClass.getMethod("into", android.widget.ImageView::class.java)
            intoMethod.invoke(requestCreator, imageView)
            loaded = true
        } catch (t: Throwable) {
            // ignore and fallback
            loaded = false
        }

        if (!loaded) {
            imageView.setImageResource(defaultPlaceholder)
        }

        holder.binding.root.setOnClickListener {
            onItemClick?.invoke(event)
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Event>() {
            override fun areItemsTheSame(oldItem: Event, newItem: Event): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Event, newItem: Event): Boolean = oldItem == newItem
        }
    }
}
