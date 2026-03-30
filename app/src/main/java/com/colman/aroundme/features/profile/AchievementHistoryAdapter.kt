package com.colman.aroundme.features.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.colman.aroundme.R
import com.colman.aroundme.utils.IsraelTime
import com.colman.aroundme.data.model.Achievement
import com.colman.aroundme.databinding.ItemAchievementHistoryBinding
import com.colman.aroundme.utils.backgroundForAchievement

class AchievementHistoryAdapter : ListAdapter<Achievement, AchievementHistoryAdapter.HistoryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemAchievementHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class HistoryViewHolder(
        private val binding: ItemAchievementHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Achievement) {
            binding.achievementIconText.text = item.icon
            binding.achievementIconText.setBackgroundResource(backgroundForAchievement(item))
            binding.achievementNameText.text = item.name
            binding.achievementDescriptionText.text = item.description
            binding.achievementDateText.text = if (item.unlockedAt > 0L) {
                IsraelTime.formatDate(item.unlockedAt)
            } else {
                binding.root.context.getString(R.string.achievement_recently_unlocked)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Achievement>() {
        override fun areItemsTheSame(oldItem: Achievement, newItem: Achievement): Boolean =
            oldItem.name == newItem.name && oldItem.unlockedAt == newItem.unlockedAt

        override fun areContentsTheSame(oldItem: Achievement, newItem: Achievement): Boolean =
            oldItem == newItem
    }
}
