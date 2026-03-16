package com.colman.aroundme.ui.map

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.colman.aroundme.databinding.ItemCategoryChipBinding

class CategoryFilterAdapter(
    private var filters: List<String>,
    private val onFilterToggled: (String) -> Unit
) : RecyclerView.Adapter<CategoryFilterAdapter.CategoryViewHolder>() {

    private val selectedFilters = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryChipBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(filters[position])
    }

    override fun getItemCount(): Int = filters.size

    fun updateFilters(newFilters: List<String>) {
        filters = newFilters
        notifyDataSetChanged()
    }

    fun updateSelection(selection: Set<String>) {
        selectedFilters.clear()
        selectedFilters.addAll(selection)
        notifyDataSetChanged()
    }

    inner class CategoryViewHolder(
        private val binding: ItemCategoryChipBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(filter: String) {
            binding.categoryChip.text = filter
            binding.categoryChip.isChecked = selectedFilters.contains(filter)
            binding.categoryChip.setOnClickListener {
                onFilterToggled(filter)
            }
        }
    }
}
