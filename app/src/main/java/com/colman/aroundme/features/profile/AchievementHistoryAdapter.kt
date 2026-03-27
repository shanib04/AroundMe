package com.colman.aroundme.features.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.colman.aroundme.R
import com.colman.aroundme.data.model.Achievement
import com.colman.aroundme.databinding.ItemAchievementHistoryBinding
import java.text.DateFormat
import java.util.Date

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
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.unlockedAt))
            } else {
                binding.root.context.getString(com.colman.aroundme.R.string.achievement_recently_unlocked)
            }
        }

        private fun backgroundForAchievement(achievement: Achievement): Int {
            val name = achievement.name.lowercase()
            return when {
                name.contains("rising") ||
                    name.contains("legend") ||
                    name.contains("fresh face") -> R.drawable.ach_bg_orange

                name.contains("trustworthy") ||
                    name.contains("oracle") ||
                    name.contains("fact checker") -> R.drawable.ach_bg_blue

                else -> R.drawable.ach_bg_purple
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
